package net.shard.seconddawnrp.medical;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;

import java.util.UUID;

/**
 * Manages the downed state for players.
 *
 * When a player goes down:
 * - Health locked to 1
 * - Immobilization effects applied every 10 ticks
 * - Registered as a medical terminal visitor (so they appear on PADD/Tricorder)
 * - critical_trauma condition auto-applied
 *
 * Revival happens through MedicalService resolving "critical_trauma".
 */
public class DownedService {

    private static final int IMMOBILIZE_INTERVAL_TICKS = 10;
    private static final int IMMOBILIZE_DURATION_TICKS = 30;

    public static final String DEFAULT_DOWNED_CONDITION = "critical_trauma";

    private final PlayerProfileManager profileManager;
    private MinecraftServer server;

    public DownedService(PlayerProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

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
                if (p != null && p.getDivision() ==
                        net.shard.seconddawnrp.division.Division.MEDICAL) {
                    online.sendMessage(Text.literal(
                                    "[Medical] ⚠ " + player.getName().getString()
                                            + " is down! Respond immediately.")
                            .formatted(Formatting.RED), false);
                }
            }
        }

        // Register as medical terminal visitor so they appear in PADD roster
        // and Tricorder scans without needing to have visited a terminal first
        if (net.shard.seconddawnrp.SecondDawnRP.MEDICAL_TERMINAL_SERVICE != null) {
            net.shard.seconddawnrp.SecondDawnRP.MEDICAL_TERMINAL_SERVICE
                    .registerVisit(player);
        }

        // Auto-apply critical_trauma
        applyDownedCondition(uuid);
    }

    private void applyDownedCondition(UUID uuid) {
        if (net.shard.seconddawnrp.SecondDawnRP.MEDICAL_SERVICE == null) {
            System.err.println("[SecondDawnRP] DownedService: MEDICAL_SERVICE is null, cannot apply " + DEFAULT_DOWNED_CONDITION);
            return;
        }
        if (net.shard.seconddawnrp.SecondDawnRP.MEDICAL_CONDITION_REGISTRY == null) {
            System.err.println("[SecondDawnRP] DownedService: MEDICAL_CONDITION_REGISTRY is null");
            return;
        }

        if (!net.shard.seconddawnrp.SecondDawnRP.MEDICAL_CONDITION_REGISTRY
                .exists(DEFAULT_DOWNED_CONDITION)) {
            System.err.println("[SecondDawnRP] WARNING: '" + DEFAULT_DOWNED_CONDITION
                    + "' not found in medical condition registry. "
                    + "Add critical_trauma.json to data/seconddawnrp/medical_conditions/");
            return;
        }

        System.out.println("[SecondDawnRP] DownedService: applying " + DEFAULT_DOWNED_CONDITION + " to " + uuid);

        MedicalService.ApplyResult result =
                net.shard.seconddawnrp.SecondDawnRP.MEDICAL_SERVICE.applyCondition(
                        uuid,
                        DEFAULT_DOWNED_CONDITION,
                        null,
                        null,
                        "Auto-applied: player downed.",
                        "server"
                );

        System.out.println("[SecondDawnRP] DownedService: applyCondition result = " + result);
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

        player.setHealth(Math.min(player.getMaxHealth(), 10.0f));

        player.removeStatusEffect(
                Registries.STATUS_EFFECT.getEntry(StatusEffects.SLOWNESS.value()));
        player.removeStatusEffect(
                Registries.STATUS_EFFECT.getEntry(StatusEffects.MINING_FATIGUE.value()));

        player.sendMessage(Text.literal(
                        "[Medical] You have been stabilized. Take it easy.")
                .formatted(Formatting.GREEN), false);
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public void tick(MinecraftServer server, int currentTick) {
        if (currentTick % IMMOBILIZE_INTERVAL_TICKS != 0) return;
        if (server == null) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerProfile profile = profileManager.getLoadedProfile(player.getUuid());
            if (profile != null && profile.isDowned()) {
                applyImmobilization(player);
                if (player.getHealth() < 1.0f) {
                    player.setHealth(1.0f);
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