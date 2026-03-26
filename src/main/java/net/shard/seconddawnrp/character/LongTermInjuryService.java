package net.shard.seconddawnrp.character;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages long-term injuries across the server lifetime.
 *
 * <p>Now uses {@link ProfileLtiCallback} to update the active LTI
 * reference on {@link net.shard.seconddawnrp.playerdata.PlayerProfile}
 * without depending on the removed CharacterRepository.
 */
public class LongTermInjuryService {

    public static final int REFRESH_INTERVAL_TICKS = 6000; // 5 minutes
    private static final int EFFECT_DURATION_TICKS  = 7200; // 6 minutes

    public static final float SPECIALIST_BONUS_MULTIPLIER = 1.5f;

    private final LongTermInjuryRepository repository;
    private final ProfileLtiCallback profileCallback;
    private final Map<UUID, LongTermInjury> cache = new ConcurrentHashMap<>();
    private MinecraftServer server;

    public LongTermInjuryService(LongTermInjuryRepository repository,
                                 ProfileLtiCallback profileCallback) {
        this.repository      = repository;
        this.profileCallback = profileCallback;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── Server startup ────────────────────────────────────────────────────────

    public void reload() {
        cache.clear();
        for (LongTermInjury injury : repository.loadAllActive()) {
            if (injury.isActive() && !injury.isExpired()) {
                cache.put(injury.getPlayerUuid(), injury);
            } else if (injury.isActive() && injury.isExpired()) {
                expire(injury);
            }
        }
    }

    // ── Player join / leave ───────────────────────────────────────────────────

    public void onPlayerJoin(ServerPlayerEntity player) {
        repository.loadActive(player.getUuid()).ifPresent(injury -> {
            if (injury.isExpired()) {
                expire(injury);
                return;
            }
            cache.put(player.getUuid(), injury);
            applyEffects(player, injury);
        });
    }

    public void onPlayerLeave(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public void tick(MinecraftServer server, int currentTick) {
        if (currentTick % REFRESH_INTERVAL_TICKS != 0) return;

        Iterator<Map.Entry<UUID, LongTermInjury>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LongTermInjury> entry = it.next();
            UUID uuid   = entry.getKey();
            LongTermInjury injury = entry.getValue();

            if (injury.isExpired()) {
                expire(injury);
                it.remove();
                notifyPlayerExpired(server, uuid);
                continue;
            }

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) applyEffects(player, injury);
        }
    }

    // ── Application ───────────────────────────────────────────────────────────

    public LongTermInjury applyInjury(UUID playerUuid, LongTermInjuryTier tier) {
        LongTermInjury existing = cache.get(playerUuid);

        LongTermInjuryTier effectiveTier = tier;
        if (existing != null && existing.isActive()) {
            if (existing.getTier().ordinal() > tier.ordinal()) {
                effectiveTier = existing.getTier();
            }
            existing.setActive(false);
            repository.save(existing);
        }

        LongTermInjury injury = LongTermInjury.createNow(playerUuid, effectiveTier);
        cache.put(playerUuid, injury);
        repository.save(injury);

        // Update PlayerProfile FK via callback
        profileCallback.setLongTermInjury(playerUuid, injury.getInjuryId());

        if (server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
            if (player != null) {
                applyEffects(player, injury);
                player.sendMessage(Text.literal(
                                "[Injury] You have sustained a " + effectiveTier.name().toLowerCase()
                                        + " long-term injury. Seek Medical attention.")
                        .formatted(Formatting.RED), false);
            }
        }

        return injury;
    }

    public TreatmentResult treat(UUID patientUuid, boolean specialist) {
        LongTermInjury injury = cache.get(patientUuid);
        if (injury == null) return TreatmentResult.NO_INJURY;
        if (!injury.isTreatmentCooldownOver()) {
            long remainMs = (injury.getLastTreatmentMs() + 24L * 3600_000)
                    - System.currentTimeMillis();
            return TreatmentResult.onCooldown(remainMs);
        }

        float reduction = injury.getTier().reductionPerSession;
        if (specialist) reduction *= SPECIALIST_BONUS_MULTIPLIER;

        long remaining = injury.getExpiresAtMs() - System.currentTimeMillis();
        long reduce    = (long) (remaining * reduction);
        long newExpiry = Math.max(0, injury.getExpiresAtMs() - reduce);

        injury.setExpiresAtMs(newExpiry);
        injury.setSessionsCompleted(injury.getSessionsCompleted() + 1);
        injury.setLastTreatmentMs(System.currentTimeMillis());

        if (newExpiry <= System.currentTimeMillis()) {
            expire(injury);
            cache.remove(patientUuid);
            repository.save(injury);
            profileCallback.clearLongTermInjury(patientUuid);
            return TreatmentResult.CLEARED;
        }

        repository.save(injury);
        return TreatmentResult.REDUCED;
    }

    public void adjustExpiry(UUID playerUuid, int days) {
        LongTermInjury injury = cache.get(playerUuid);
        if (injury == null) {
            injury = repository.loadActive(playerUuid).orElse(null);
            if (injury == null) return;
        }
        long adjustment = (long) days * 24 * 3600_000;
        long newExpiry  = Math.max(System.currentTimeMillis() + 1000,
                injury.getExpiresAtMs() + adjustment);
        injury.setExpiresAtMs(newExpiry);
        repository.save(injury);

        if (newExpiry <= System.currentTimeMillis()) {
            expire(injury);
            cache.remove(playerUuid);
        }
    }

    public Optional<LongTermInjury> getActive(UUID playerUuid) {
        return Optional.ofNullable(cache.get(playerUuid));
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void applyEffects(ServerPlayerEntity player, LongTermInjury injury) {
        switch (injury.getTier()) {
            case MINOR ->
                    player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.WEAKNESS, EFFECT_DURATION_TICKS, 0, false, false, true));
            case MODERATE -> {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.WEAKNESS, EFFECT_DURATION_TICKS, 0, false, false, true));
                if (Math.random() < 0.33) {
                    player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.SLOWNESS, EFFECT_DURATION_TICKS, 0, false, false, true));
                }
            }
            case SEVERE -> {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.WEAKNESS, EFFECT_DURATION_TICKS, 1, false, false, true));
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS, EFFECT_DURATION_TICKS, 0, false, false, true));
                if (Math.random() < 0.20) {
                    player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.NAUSEA, EFFECT_DURATION_TICKS, 0, false, false, true));
                }
            }
        }
    }

    private void expire(LongTermInjury injury) {
        injury.setActive(false);
        repository.save(injury);
        profileCallback.clearLongTermInjury(injury.getPlayerUuid());
    }

    private void notifyPlayerExpired(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        if (p != null) {
            p.sendMessage(Text.literal("[Medical] Your long-term injury has fully healed.")
                    .formatted(Formatting.GREEN), false);
        }
    }

    // ── Callback interface ────────────────────────────────────────────────────

    /**
     * Allows the service to update the active LTI reference on
     * {@link net.shard.seconddawnrp.playerdata.PlayerProfile} without
     * depending on CharacterRepository.
     */
    public interface ProfileLtiCallback {
        void clearLongTermInjury(UUID playerUuid);
        void setLongTermInjury(UUID playerUuid, String injuryId);
    }

    // ── Treatment result ──────────────────────────────────────────────────────

    public static final class TreatmentResult {
        public enum Type { NO_INJURY, ON_COOLDOWN, REDUCED, CLEARED }

        public static final TreatmentResult NO_INJURY = new TreatmentResult(Type.NO_INJURY, 0);
        public static final TreatmentResult REDUCED   = new TreatmentResult(Type.REDUCED,   0);
        public static final TreatmentResult CLEARED   = new TreatmentResult(Type.CLEARED,   0);

        public static TreatmentResult onCooldown(long remainingMs) {
            return new TreatmentResult(Type.ON_COOLDOWN, remainingMs);
        }

        public final Type type;
        public final long remainingCooldownMs;

        private TreatmentResult(Type type, long remainingCooldownMs) {
            this.type = type;
            this.remainingCooldownMs = remainingCooldownMs;
        }
    }
}