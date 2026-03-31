package net.shard.seconddawnrp.medical;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.character.LongTermInjury;
import net.shard.seconddawnrp.character.LongTermInjuryTier;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the downed state for players.
 *
 * Phase 8.5: Force respawn + low pop STI system.
 *
 * Low pop mode activates when fewer than 5 players are online OR
 * no Medical division player is online. In low pop, downed events
 * grant a Short-Term Injury (STI) instead of a full LTI:
 * - MODERATE tier effects (same effects, shorter duration)
 * - Auto-clears after 30 minutes
 * - Clears after 1 treatment session if Medical is available
 *
 * Force respawn (available after 2 min downed, hold Sneak 3s):
 * - Normal pop: MODERATE LTI, 2 sessions / 3 days
 * - Low pop: MODERATE STI, 1 session / 30 minutes
 */
public class DownedService {

    private static final int  IMMOBILIZE_INTERVAL_TICKS   = 10;
    private static final int  IMMOBILIZE_DURATION_TICKS   = 30;
    private static final long FORCE_RESPAWN_PROMPT_DELAY_MS = 2 * 60 * 1000L;
    private static final long FORCE_RESPAWN_HOLD_MS         = 3000L;

    private static final LongTermInjuryTier FORCE_RESPAWN_TIER      = LongTermInjuryTier.MODERATE;
    private static final int  FORCE_RESPAWN_SESSIONS_REQUIRED        = 2;

    /** Players online threshold below which low pop mode activates. */
    private static final int  LOW_POP_PLAYER_THRESHOLD               = 5;

    /** STI duration in low pop mode — 30 minutes in ms. */
    private static final long LOW_POP_STI_DURATION_MS                = 30 * 60 * 1000L;

    /** Sessions to clear STI in low pop (1 = one Medical treatment clears it early). */
    private static final int  LOW_POP_STI_SESSIONS_REQUIRED          = 1;

    public static final String DEFAULT_DOWNED_CONDITION = "critical_trauma";

    private final PlayerProfileManager profileManager;
    private MinecraftServer server;

    /** Tracks when each player started holding sneak while downed. */
    private final Map<UUID, Long> sneakStartMs = new ConcurrentHashMap<>();

    public DownedService(PlayerProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── Low pop detection ─────────────────────────────────────────────────────

    /**
     * Returns true if the server is currently in low pop mode.
     * Triggers when: fewer than 5 players online OR no Medical division player online.
     */
    public boolean isLowPop() {
        if (server == null) return false;
        var players = server.getPlayerManager().getPlayerList();

        if (players.size() < LOW_POP_PLAYER_THRESHOLD) return true;

        for (var player : players) {
            var profile = profileManager.getLoadedProfile(player.getUuid());
            if (profile != null && profile.getDivision() == Division.MEDICAL) {
                return false;
            }
        }
        return true; // No Medical online
    }

    // ── Down ─────────────────────────────────────────────────────────────────

    public void downPlayer(ServerPlayerEntity player, boolean eventMode) {
        UUID uuid = player.getUuid();
        PlayerProfile profile = profileManager.getLoadedProfile(uuid);
        if (profile == null) return;
        if (profile.isDowned()) return;

        player.setHealth(1.0f);

        profile.setDowned(true);
        profile.setDownedAt(System.currentTimeMillis());
        profile.setDownedEventMode(eventMode);
        profileManager.markDirty(uuid);

        applyImmobilization(player);

        player.sendMessage(Text.literal(
                        "[Medical] You are down. Stay still — Medical is on their way.")
                .formatted(Formatting.DARK_RED), false);

        // Notify online Medical officers
        if (server != null) {
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                PlayerProfile p = profileManager.getLoadedProfile(online.getUuid());
                if (p != null && p.getDivision() == Division.MEDICAL) {
                    online.sendMessage(Text.literal(
                                    "[Medical] ⚠ " + player.getName().getString()
                                            + " is down! Respond immediately.")
                            .formatted(Formatting.RED), false);
                }
            }
        }

        // Register as medical terminal visitor
        if (net.shard.seconddawnrp.SecondDawnRP.MEDICAL_TERMINAL_SERVICE != null) {
            net.shard.seconddawnrp.SecondDawnRP.MEDICAL_TERMINAL_SERVICE.registerVisit(player);
        }

        applyDownedCondition(uuid);
    }

    private void applyDownedCondition(UUID uuid) {
        if (net.shard.seconddawnrp.SecondDawnRP.MEDICAL_SERVICE == null) return;
        if (net.shard.seconddawnrp.SecondDawnRP.MEDICAL_CONDITION_REGISTRY == null) return;
        if (!net.shard.seconddawnrp.SecondDawnRP.MEDICAL_CONDITION_REGISTRY
                .exists(DEFAULT_DOWNED_CONDITION)) {
            System.err.println("[SecondDawnRP] WARNING: '" + DEFAULT_DOWNED_CONDITION
                    + "' not found in registry.");
            return;
        }
        net.shard.seconddawnrp.SecondDawnRP.MEDICAL_SERVICE.applyCondition(
                uuid, DEFAULT_DOWNED_CONDITION, null, null,
                "Auto-applied: player downed.", "server");
    }

    // ── Revive ────────────────────────────────────────────────────────────────

    public void revivePlayer(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PlayerProfile profile = profileManager.getLoadedProfile(uuid);
        if (profile == null) return;
        if (!profile.isDowned()) return;

        profile.setDowned(false);
        profile.setDownedAt(0L);
        profile.setDownedEventMode(false);
        profileManager.markDirty(uuid);

        sneakStartMs.remove(uuid);

        player.setHealth(Math.min(player.getMaxHealth(), 10.0f));

        player.removeStatusEffect(
                Registries.STATUS_EFFECT.getEntry(StatusEffects.SLOWNESS.value()));
        player.removeStatusEffect(
                Registries.STATUS_EFFECT.getEntry(StatusEffects.MINING_FATIGUE.value()));

        player.sendMessage(Text.literal(
                        "[Medical] You have been stabilized. Take it easy.")
                .formatted(Formatting.GREEN), false);
    }

    // ── Force respawn ─────────────────────────────────────────────────────────

    private void forceRespawn(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PlayerProfile profile = profileManager.getLoadedProfile(uuid);
        if (profile == null || !profile.isDowned()) return;

        boolean lowPop = isLowPop();

        // Clear downed state
        profile.setDowned(false);
        profile.setDownedAt(0L);
        profile.setDownedEventMode(false);
        profileManager.markDirty(uuid);
        sneakStartMs.remove(uuid);

        player.removeStatusEffect(
                Registries.STATUS_EFFECT.getEntry(StatusEffects.SLOWNESS.value()));
        player.removeStatusEffect(
                Registries.STATUS_EFFECT.getEntry(StatusEffects.MINING_FATIGUE.value()));

        // Force-clear all active medical conditions (copy list to avoid CME)
        if (net.shard.seconddawnrp.SecondDawnRP.MEDICAL_SERVICE != null) {
            List<String> conditionIds = new ArrayList<>(profile.getActiveMedicalConditionIds());
            for (String condId : conditionIds) {
                net.shard.seconddawnrp.SecondDawnRP.MEDICAL_SERVICE
                        .forceResolve(condId, "server");
            }
        }

        // Apply injury — STI in low pop, LTI in normal pop
        applyPostRespawnInjury(uuid, lowPop);

        player.setHealth(Math.min(player.getMaxHealth(), 6.0f));

        if (lowPop) {
            player.sendMessage(Text.literal(
                            "[Medical] You have force-respawned. Short-term injury applied "
                                    + "— will clear in 30 minutes.")
                    .formatted(Formatting.YELLOW), false);
            player.sendMessage(Text.literal(
                            "§7[Medical] Low population mode — no Medical staff required.")
                    .formatted(Formatting.GRAY), false);
        } else {
            player.sendMessage(Text.literal(
                            "[Medical] You have force-respawned. Long-term injury applied "
                                    + "— report to Sickbay.")
                    .formatted(Formatting.YELLOW), false);
            player.sendMessage(Text.literal(
                            "[Medical] 2 treatment sessions required to clear your injury.")
                    .formatted(Formatting.RED), false);
        }
    }

    /**
     * Applies the appropriate post-respawn injury based on pop level.
     *
     * Normal pop: MODERATE LTI, 2 sessions, 3 days.
     * Low pop:    MODERATE STI, 1 session, 30 minutes — auto-clears.
     */
    private void applyPostRespawnInjury(UUID uuid, boolean lowPop) {
        if (net.shard.seconddawnrp.SecondDawnRP.LONG_TERM_INJURY_SERVICE == null) return;

        LongTermInjury injury;
        if (lowPop) {
            // STI — override both duration and sessions
            long now = System.currentTimeMillis();
            injury = new LongTermInjury(
                    java.util.UUID.randomUUID().toString(),
                    uuid,
                    FORCE_RESPAWN_TIER,
                    now,
                    now + LOW_POP_STI_DURATION_MS, // 30 minutes
                    0, 0L, true,
                    LOW_POP_STI_SESSIONS_REQUIRED, // 1 session
                    null,
                    "Short-Term Injury (Low Pop)",
                    "Temporary injury sustained during low population period. Auto-clears in 30 minutes.",
                    false, "[]", null, null, false, "server", null,
                    0L, 0L
            );
        } else {
            // Normal LTI — MODERATE, 2 sessions, 3 days
            injury = LongTermInjury.createWithSessionOverride(
                    uuid, FORCE_RESPAWN_TIER, FORCE_RESPAWN_SESSIONS_REQUIRED);
        }

        net.shard.seconddawnrp.SecondDawnRP.LONG_TERM_INJURY_SERVICE.registerCondition(injury);
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public void tick(MinecraftServer server, int currentTick) {
        if (server == null) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerProfile profile = profileManager.getLoadedProfile(player.getUuid());
            if (profile == null || !profile.isDowned()) {
                sneakStartMs.remove(player.getUuid());
                continue;
            }

            if (currentTick % IMMOBILIZE_INTERVAL_TICKS == 0) {
                applyImmobilization(player);
                if (player.getHealth() < 1.0f) player.setHealth(1.0f);
            }

            long downedAt = profile.getDownedAt();
            long now      = System.currentTimeMillis();
            long downedMs = now - downedAt;

            if (downedMs < FORCE_RESPAWN_PROMPT_DELAY_MS) {
                sneakStartMs.remove(player.getUuid());
                if (currentTick % 100 == 0) {
                    long remaining = (FORCE_RESPAWN_PROMPT_DELAY_MS - downedMs) / 1000;
                    player.sendMessage(
                            Text.literal("§c[DOWN] Force respawn available in " + remaining + "s"),
                            true);
                }
                continue;
            }

            boolean isSneaking = player.isSneaking();
            UUID uuid = player.getUuid();

            if (isSneaking) {
                long sneakStart = sneakStartMs.computeIfAbsent(uuid, k -> now);
                long heldMs     = now - sneakStart;
                long remaining  = FORCE_RESPAWN_HOLD_MS - heldMs;

                if (remaining <= 0) {
                    sneakStartMs.remove(uuid);
                    forceRespawn(player);
                } else {
                    long remainSec = (remaining / 1000) + 1;
                    String popTag  = isLowPop() ? " §8(STI — 30min)" : " §8(LTI penalty)";
                    player.sendMessage(
                            Text.literal("§c[DOWN] Force respawn in " + remainSec
                                    + "s — release sneak to cancel" + popTag),
                            true);
                }
            } else {
                sneakStartMs.remove(uuid);
                if (currentTick % 40 == 0) {
                    String popTag = isLowPop()
                            ? " §8[Low Pop — STI only]"
                            : " §8[LTI penalty applies]";
                    player.sendMessage(
                            Text.literal("§c[DOWN] §7Hold §cSneak §73s to force respawn"
                                    + popTag),
                            true);
                }
            }
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean isDowned(UUID playerUuid) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        return profile != null && profile.isDowned();
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void applyImmobilization(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(
                Registries.STATUS_EFFECT.getEntry(StatusEffects.SLOWNESS.value()),
                IMMOBILIZE_DURATION_TICKS, 254, false, false, false));
        player.addStatusEffect(new StatusEffectInstance(
                Registries.STATUS_EFFECT.getEntry(StatusEffects.MINING_FATIGUE.value()),
                IMMOBILIZE_DURATION_TICKS, 254, false, false, false));
    }
}