package net.shard.seconddawnrp.character;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.medical.MedicalConditionRegistry;
import net.shard.seconddawnrp.medical.MedicalConditionTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class LongTermInjuryService {

    public static final int REFRESH_INTERVAL_TICKS = 6000;
    private static final int EFFECT_DURATION_TICKS = 7200;

    public static final float SPECIALIST_BONUS_MULTIPLIER = 1.5f;

    private final LongTermInjuryRepository repository;
    private final ProfileLtiCallback profileCallback;
    private final MedicalConditionRegistry conditionRegistry;

    private final Map<UUID, List<LongTermInjury>> cache = new ConcurrentHashMap<>();

    private MinecraftServer server;

    public LongTermInjuryService(LongTermInjuryRepository repository,
                                 ProfileLtiCallback profileCallback,
                                 MedicalConditionRegistry conditionRegistry) {
        this.repository = repository;
        this.profileCallback = profileCallback;
        this.conditionRegistry = conditionRegistry;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    public void reload() {
        cache.clear();
        for (LongTermInjury injury : repository.loadAllActive()) {
            if (injury.isActive() && !injury.isExpired()) {
                cacheAdd(injury);
            } else if (injury.isActive() && injury.isExpired()) {
                expire(injury);
            }
        }
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        if (repository instanceof SqlLongTermInjuryRepository sqlRepo) {
            List<LongTermInjury> conditions = sqlRepo.loadAllActiveForPlayer(player.getUuid());
            List<LongTermInjury> valid = new ArrayList<>();
            for (LongTermInjury c : conditions) {
                if (c.isExpired()) {
                    expire(c);
                } else {
                    valid.add(c);
                    applyEffects(player, c);
                }
            }
            if (!valid.isEmpty()) {
                cache.put(player.getUuid(), valid);
                syncIdsToProfile(player.getUuid(), valid);
            }
        } else {
            repository.loadActive(player.getUuid()).ifPresent(injury -> {
                if (injury.isExpired()) {
                    expire(injury);
                    return;
                }
                cacheAdd(injury);
                applyEffects(player, injury);
            });
        }
    }

    public void onPlayerLeave(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    public void registerCondition(LongTermInjury injury) {
        cacheAdd(injury);
        syncIdsToProfile(injury.getPlayerUuid(), cache.getOrDefault(injury.getPlayerUuid(), List.of()));

        if (server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(injury.getPlayerUuid());
            if (player != null) {
                applyEffects(player, injury);
            }
        }
    }

    public void clearCondition(UUID playerUuid, String injuryId) {
        List<LongTermInjury> active = cache.get(playerUuid);
        if (active == null || active.isEmpty()) {
            syncIdsToProfile(playerUuid, List.of());
            return;
        }

        LongTermInjury removed = null;
        Iterator<LongTermInjury> it = active.iterator();
        while (it.hasNext()) {
            LongTermInjury injury = it.next();
            if (injury.getInjuryId().equals(injuryId)) {
                removed = injury;
                it.remove();
                break;
            }
        }

        if (active.isEmpty()) {
            cache.remove(playerUuid);
        }

        syncIdsToProfile(playerUuid, cache.getOrDefault(playerUuid, List.of()));

        if (removed == null || server == null) return;

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
        if (player == null) return;

        removeEffectsForClearedCondition(player, removed, cache.getOrDefault(playerUuid, List.of()));
    }

    /**
     * Called when a player drinks milk.
     *
     * Milk never clears the condition itself.
     * It can only suppress condition-owned effects temporarily if the template allows it.
     *
     * @return true if at least one condition was suppressed
     */
    public boolean handleMilkUse(ServerPlayerEntity player, ItemStack stack) {
        if (stack.getItem() != Items.MILK_BUCKET) {
            return false;
        }

        List<LongTermInjury> active = cache.get(player.getUuid());
        if (active == null || active.isEmpty()) {
            return false;
        }

        boolean anySuppressed = false;
        long now = System.currentTimeMillis();

        for (LongTermInjury injury : active) {
            if (!injury.isRegistryBacked()) continue;

            String key = injury.getConditionKey();
            if (key == null || key.isBlank()) continue;

            Optional<MedicalConditionTemplate> templateOpt = conditionRegistry.get(key);
            if (templateOpt.isEmpty()) continue;

            MedicalConditionTemplate template = templateOpt.get();
            if (!template.allowsMilkSuppression()) continue;

            MedicalConditionTemplate.MilkInteraction milk = template.milkInteraction();

            // Anti-spam: if currently suppressed, or still on cooldown, do nothing
            if (injury.areEffectsSuppressed()) {
                continue;
            }
            if (milk.hasCooldown() && now < injury.getLastMilkUseMs() + milk.reapplyCooldownSeconds() * 1000L) {
                continue;
            }

            long suppressedUntil = now + milk.suppressionSeconds() * 1000L;
            injury.setEffectsSuppressedUntilMs(suppressedUntil);
            injury.setLastMilkUseMs(now);
            repository.save(injury);

            if (milk.clearOnUse()) {
                removeEffectsForClearedCondition(player, injury, activeWithout(active, injury.getInjuryId()));
            }

            anySuppressed = true;
        }

        if (anySuppressed) {
            player.sendMessage(Text.literal(
                            "[Medical] Milk provided temporary symptom relief, but the condition remains.")
                    .formatted(Formatting.YELLOW), false);
        }

        return anySuppressed;
    }

    public void tick(MinecraftServer server, int currentTick) {
        if (currentTick % REFRESH_INTERVAL_TICKS != 0) return;

        for (UUID uuid : new ArrayList<>(cache.keySet())) {
            List<LongTermInjury> conditions = cache.get(uuid);
            if (conditions == null) continue;

            List<LongTermInjury> toRemove = new ArrayList<>();
            for (LongTermInjury injury : conditions) {
                if (injury.isExpired()) {
                    expire(injury);
                    toRemove.add(injury);
                    notifyPlayerExpired(server, uuid, injury);
                } else {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                    if (player != null) applyEffects(player, injury);
                }
            }

            if (!toRemove.isEmpty()) {
                conditions.removeAll(toRemove);
                if (conditions.isEmpty()) {
                    cache.remove(uuid);
                }
                syncIdsToProfile(uuid, cache.getOrDefault(uuid, List.of()));
            }
        }
    }

    public LongTermInjury applyInjury(UUID playerUuid, LongTermInjuryTier tier) {
        List<LongTermInjury> existing = getActive(playerUuid);
        LongTermInjury oldClassic = existing.stream()
                .filter(c -> !c.isRegistryBacked())
                .findFirst()
                .orElse(null);

        LongTermInjuryTier effectiveTier = tier;
        if (oldClassic != null) {
            if (oldClassic.getTier().ordinal() > tier.ordinal()) {
                effectiveTier = oldClassic.getTier();
            }
            oldClassic.setActive(false);
            repository.save(oldClassic);
            cacheRemove(playerUuid, oldClassic.getInjuryId());
        }

        LongTermInjury injury = LongTermInjury.createNow(playerUuid, effectiveTier);
        cacheAdd(injury);
        repository.save(injury);

        syncIdsToProfile(playerUuid, cache.getOrDefault(playerUuid, List.of()));

        if (server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
            if (player != null) {
                applyEffects(player, injury);
                player.sendMessage(Text.literal(
                                "[Injury] You have sustained a " + effectiveTier.name().toLowerCase()
                                        + " long-term injury. Report to Sickbay.")
                        .formatted(Formatting.RED), false);
            }
        }

        return injury;
    }

    public TreatmentResult treat(UUID patientUuid, boolean specialist) {
        List<LongTermInjury> active = getActive(patientUuid);
        if (active.isEmpty()) return TreatmentResult.NO_INJURY;

        LongTermInjury injury = active.get(active.size() - 1);

        if (!injury.isTreatmentCooldownOver()) {
            long remainMs = (injury.getLastTreatmentMs() + 24L * 3600_000)
                    - System.currentTimeMillis();
            return TreatmentResult.onCooldown(remainMs);
        }

        float reduction = injury.getTier().reductionPerSession;
        if (specialist) reduction *= SPECIALIST_BONUS_MULTIPLIER;

        long remaining = injury.getExpiresAtMs() - System.currentTimeMillis();
        long reduce = (long) (remaining * reduction);
        long newExpiry = Math.max(0, injury.getExpiresAtMs() - reduce);

        injury.setExpiresAtMs(newExpiry);
        injury.setSessionsCompleted(injury.getSessionsCompleted() + 1);
        injury.setLastTreatmentMs(System.currentTimeMillis());

        if (newExpiry <= System.currentTimeMillis()) {
            expire(injury);
            cacheRemove(patientUuid, injury.getInjuryId());
            repository.save(injury);
            syncIdsToProfile(patientUuid, cache.getOrDefault(patientUuid, List.of()));
            return TreatmentResult.CLEARED;
        }

        repository.save(injury);
        return TreatmentResult.REDUCED;
    }

    public void adjustExpiry(UUID playerUuid, int days) {
        List<LongTermInjury> active = getActive(playerUuid);
        if (active.isEmpty()) {
            if (repository instanceof SqlLongTermInjuryRepository sqlRepo) {
                List<LongTermInjury> loaded = sqlRepo.loadAllActiveForPlayer(playerUuid);
                for (LongTermInjury injury : loaded) {
                    applyExpiryAdjustment(playerUuid, injury, days);
                }
            }
            return;
        }
        for (LongTermInjury injury : active) {
            applyExpiryAdjustment(playerUuid, injury, days);
        }
    }

    private void applyExpiryAdjustment(UUID playerUuid, LongTermInjury injury, int days) {
        long adjustment = (long) days * 24 * 3600_000;
        long newExpiry = Math.max(System.currentTimeMillis() + 1000,
                injury.getExpiresAtMs() + adjustment);
        injury.setExpiresAtMs(newExpiry);
        repository.save(injury);
        if (newExpiry <= System.currentTimeMillis()) {
            expire(injury);
            cacheRemove(playerUuid, injury.getInjuryId());
            syncIdsToProfile(playerUuid, cache.getOrDefault(playerUuid, List.of()));
        }
    }

    public List<LongTermInjury> getActive(UUID playerUuid) {
        return Collections.unmodifiableList(
                cache.getOrDefault(playerUuid, Collections.emptyList()));
    }

    public boolean hasActiveCondition(UUID playerUuid) {
        List<LongTermInjury> list = cache.get(playerUuid);
        return list != null && !list.isEmpty();
    }

    private void cacheAdd(LongTermInjury injury) {
        cache.computeIfAbsent(injury.getPlayerUuid(), k -> new ArrayList<>()).add(injury);
    }

    private void cacheRemove(UUID playerUuid, String injuryId) {
        List<LongTermInjury> list = cache.get(playerUuid);
        if (list != null) {
            list.removeIf(c -> c.getInjuryId().equals(injuryId));
            if (list.isEmpty()) cache.remove(playerUuid);
        }
    }

    private void syncIdsToProfile(UUID playerUuid, List<LongTermInjury> active) {
        profileCallback.setMedicalConditionIds(playerUuid,
                active.stream().map(LongTermInjury::getInjuryId).collect(Collectors.toList()));
    }

    private void applyEffects(ServerPlayerEntity player, LongTermInjury injury) {
        if (injury.areEffectsSuppressed()) {
            return;
        }

        if (applyConditionSpecificEffects(player, injury)) {
            return;
        }

        applyLegacyTierEffects(player, injury);
    }

    private boolean applyConditionSpecificEffects(ServerPlayerEntity player, LongTermInjury injury) {
        if (!injury.isRegistryBacked()) return false;

        String key = injury.getConditionKey();
        if (key == null || key.isBlank()) return false;

        Optional<MedicalConditionTemplate> templateOpt = conditionRegistry.get(key);
        if (templateOpt.isEmpty()) return false;

        MedicalConditionTemplate template = templateOpt.get();
        if (!template.hasActiveEffects()) return false;

        for (MedicalConditionTemplate.ConditionEffect effectDef : template.activeEffects()) {
            String effectId = effectDef.effect();
            if (effectId == null || effectId.isBlank()) continue;

            Identifier id;
            try {
                id = Identifier.of(effectId);
            } catch (Exception e) {
                System.err.println("[SecondDawnRP] Invalid condition effect id: " + effectId);
                continue;
            }

            Optional<StatusEffect> effectOpt = Registries.STATUS_EFFECT.getOrEmpty(id);
            if (effectOpt.isEmpty()) {
                System.err.println("[SecondDawnRP] Unknown condition effect: " + effectId);
                continue;
            }

            int amplifier = Math.max(0, effectDef.amplifier());
            int duration = Math.max(20, effectDef.durationTicks());

            player.addStatusEffect(new StatusEffectInstance(
                    Registries.STATUS_EFFECT.getEntry(effectOpt.get()),
                    duration,
                    amplifier,
                    false,
                    false,
                    true
            ));
        }

        return true;
    }

    private void applyLegacyTierEffects(ServerPlayerEntity player, LongTermInjury injury) {
        switch (injury.getTier()) {
            case MINOR ->
                    player.addStatusEffect(new StatusEffectInstance(
                            Registries.STATUS_EFFECT.getEntry(StatusEffects.WEAKNESS.value()),
                            EFFECT_DURATION_TICKS, 0, false, false, true));
            case MODERATE -> {
                player.addStatusEffect(new StatusEffectInstance(
                        Registries.STATUS_EFFECT.getEntry(StatusEffects.WEAKNESS.value()),
                        EFFECT_DURATION_TICKS, 0, false, false, true));
                if (Math.random() < 0.33) {
                    player.addStatusEffect(new StatusEffectInstance(
                            Registries.STATUS_EFFECT.getEntry(StatusEffects.SLOWNESS.value()),
                            EFFECT_DURATION_TICKS, 0, false, false, true));
                }
            }
            case SEVERE -> {
                player.addStatusEffect(new StatusEffectInstance(
                        Registries.STATUS_EFFECT.getEntry(StatusEffects.WEAKNESS.value()),
                        EFFECT_DURATION_TICKS, 1, false, false, true));
                player.addStatusEffect(new StatusEffectInstance(
                        Registries.STATUS_EFFECT.getEntry(StatusEffects.SLOWNESS.value()),
                        EFFECT_DURATION_TICKS, 0, false, false, true));
                if (Math.random() < 0.20) {
                    player.addStatusEffect(new StatusEffectInstance(
                            Registries.STATUS_EFFECT.getEntry(StatusEffects.NAUSEA.value()),
                            EFFECT_DURATION_TICKS, 0, false, false, true));
                }
            }
        }
    }

    private void removeEffectsForClearedCondition(ServerPlayerEntity player,
                                                  LongTermInjury cleared,
                                                  List<LongTermInjury> remainingConditions) {
        if (cleared.isRegistryBacked() && removeConditionSpecificEffects(player, cleared, remainingConditions)) {
            return;
        }
        removeLegacyTierEffects(player, cleared, remainingConditions);
    }

    private boolean removeConditionSpecificEffects(ServerPlayerEntity player,
                                                   LongTermInjury cleared,
                                                   List<LongTermInjury> remainingConditions) {
        String key = cleared.getConditionKey();
        if (key == null || key.isBlank()) return false;

        Optional<MedicalConditionTemplate> templateOpt = conditionRegistry.get(key);
        if (templateOpt.isEmpty()) return false;

        MedicalConditionTemplate template = templateOpt.get();
        if (!template.hasActiveEffects()) return false;

        for (MedicalConditionTemplate.ConditionEffect effectDef : template.activeEffects()) {
            String effectId = effectDef.effect();
            if (effectId == null || effectId.isBlank()) continue;

            Identifier id;
            try {
                id = Identifier.of(effectId);
            } catch (Exception ignored) {
                continue;
            }

            Optional<StatusEffect> effectOpt = Registries.STATUS_EFFECT.getOrEmpty(id);
            if (effectOpt.isEmpty()) continue;

            StatusEffect effect = effectOpt.get();
            if (!remainingConditionsRequireEffect(remainingConditions, effect)) {
                player.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(effect));
            }
        }

        return true;
    }

    private void removeLegacyTierEffects(ServerPlayerEntity player,
                                         LongTermInjury cleared,
                                         List<LongTermInjury> remainingConditions) {
        Set<StatusEffect> legacyEffects = switch (cleared.getTier()) {
            case MINOR -> Set.of(StatusEffects.WEAKNESS.value());
            case MODERATE -> Set.of(StatusEffects.WEAKNESS.value(), StatusEffects.SLOWNESS.value());
            case SEVERE -> Set.of(
                    StatusEffects.WEAKNESS.value(),
                    StatusEffects.SLOWNESS.value(),
                    StatusEffects.NAUSEA.value()
            );
        };

        for (StatusEffect effect : legacyEffects) {
            if (!remainingConditionsRequireEffect(remainingConditions, effect)) {
                player.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(effect));
            }
        }
    }

    private boolean remainingConditionsRequireEffect(List<LongTermInjury> conditions, StatusEffect targetEffect) {
        for (LongTermInjury injury : conditions) {
            if (injury.areEffectsSuppressed()) {
                continue;
            }

            if (injury.isRegistryBacked()) {
                String key = injury.getConditionKey();
                if (key != null && !key.isBlank()) {
                    Optional<MedicalConditionTemplate> templateOpt = conditionRegistry.get(key);
                    if (templateOpt.isPresent() && templateOpt.get().hasActiveEffects()) {
                        for (MedicalConditionTemplate.ConditionEffect effectDef : templateOpt.get().activeEffects()) {
                            try {
                                Identifier id = Identifier.of(effectDef.effect());
                                Optional<StatusEffect> effectOpt = Registries.STATUS_EFFECT.getOrEmpty(id);
                                if (effectOpt.isPresent() && effectOpt.get() == targetEffect) {
                                    return true;
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        continue;
                    }
                }
            }

            if (legacyTierRequiresEffect(injury.getTier(), targetEffect)) {
                return true;
            }
        }
        return false;
    }

    private boolean legacyTierRequiresEffect(LongTermInjuryTier tier, StatusEffect effect) {
        return switch (tier) {
            case MINOR -> effect == StatusEffects.WEAKNESS.value();
            case MODERATE -> effect == StatusEffects.WEAKNESS.value()
                    || effect == StatusEffects.SLOWNESS.value();
            case SEVERE -> effect == StatusEffects.WEAKNESS.value()
                    || effect == StatusEffects.SLOWNESS.value()
                    || effect == StatusEffects.NAUSEA.value();
        };
    }

    private List<LongTermInjury> activeWithout(List<LongTermInjury> source, String injuryId) {
        List<LongTermInjury> copy = new ArrayList<>();
        for (LongTermInjury injury : source) {
            if (!injury.getInjuryId().equals(injuryId)) {
                copy.add(injury);
            }
        }
        return copy;
    }

    private void expire(LongTermInjury injury) {
        injury.setActive(false);
        repository.save(injury);
    }

    private void notifyPlayerExpired(MinecraftServer server, UUID uuid, LongTermInjury injury) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        if (p != null) {
            String name = injury.getDisplayNameOverride() != null
                    ? injury.getDisplayNameOverride()
                    : injury.getTier().name().toLowerCase() + " injury";
            p.sendMessage(Text.literal("[Medical] Your condition (" + name + ") has resolved.")
                    .formatted(Formatting.GREEN), false);
        }
    }

    public interface ProfileLtiCallback {
        void setMedicalConditionIds(UUID playerUuid, List<String> conditionIds);
    }

    public static final class TreatmentResult {
        public enum Type { NO_INJURY, ON_COOLDOWN, REDUCED, CLEARED }

        public static final TreatmentResult NO_INJURY = new TreatmentResult(Type.NO_INJURY, 0);
        public static final TreatmentResult REDUCED = new TreatmentResult(Type.REDUCED, 0);
        public static final TreatmentResult CLEARED = new TreatmentResult(Type.CLEARED, 0);

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