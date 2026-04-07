package net.shard.seconddawnrp.tactical.service;

import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.tactical.data.*;

import java.util.*;

/**
 * Handles hull integrity thresholds, zone damage events,
 * and physical block destruction on both the damage model
 * and the real ship build.
 */
public class HullDamageService {

    private static final net.minecraft.block.Block[] DAMAGE_BLOCKS = {
            Blocks.CRACKED_STONE_BRICKS,
            Blocks.COBBLESTONE,
            Blocks.FIRE
    };

    private final Map<String, Set<String>> damagedZones = new HashMap<>();

    public void applyHullDamage(EncounterState encounter, ShipState ship,
                                int damage, MinecraftServer server) {
        ShipState.HullState stateBefore = ship.getHullState();
        ship.setHullIntegrity(ship.getHullIntegrity() - damage);
        ShipState.HullState stateAfter = ship.getHullState();

        if (stateAfter == ShipState.HullState.DESTROYED && !ship.isDestroyed()) {
            ship.setDestroyed(true);
            encounter.log("[HULL] " + ship.getRegistryName() + " hull integrity at zero — DESTROYED");
            return;
        }

        if (stateBefore != stateAfter) {
            onThresholdCrossed(encounter, ship, stateAfter, server);
        }

        applyZoneDamage(encounter, ship, damage, server);
    }

    private void onThresholdCrossed(EncounterState encounter, ShipState ship,
                                    ShipState.HullState newState, MinecraftServer server) {
        encounter.log("[HULL] " + ship.getRegistryName() + " hull state: " + newState.name());
        if (server == null) return;

        switch (newState) {
            case DAMAGED -> {
                broadcastToCrew(server,
                        "[TACTICAL] Hull integrity below 75%. Damage reported in multiple sections.",
                        Formatting.YELLOW);
                generateEngineeringTasks(encounter, ship, 1);
            }
            case CRITICAL -> {
                broadcastToCrew(server,
                        "[TACTICAL] ⚠ CRITICAL HULL DAMAGE — weapons efficiency reduced, shield regen halved!",
                        Formatting.RED);
                applyCrewEffect(server, StatusEffects.NAUSEA, 60, 0);
                generateEngineeringTasks(encounter, ship, 2);
            }
            case FAILING -> {
                broadcastToCrew(server,
                        "[TACTICAL] ⚠⚠ HULL FAILING — multiple systems offline! EVACUATE NON-ESSENTIAL PERSONNEL!",
                        Formatting.DARK_RED);
                applyCrewEffect(server, StatusEffects.NAUSEA, 100, 1);
                applyCrewEffect(server, StatusEffects.BLINDNESS, 40, 0);
                generateEngineeringTasks(encounter, ship, 3);
            }
        }
    }

    private void applyZoneDamage(EncounterState encounter, ShipState ship,
                                 int damage, MinecraftServer server) {
        ShipClassDefinition def = ShipClassDefinition.get(ship.getShipClass()).orElse(null);
        if (def == null || def.getDamageZones().isEmpty()) return;
        if (damage < 50) return;

        Set<String> damaged = damagedZones.computeIfAbsent(ship.getShipId(), k -> new HashSet<>());
        List<String> undamaged = def.getDamageZones().stream()
                .filter(z -> !damaged.contains(z)).toList();
        List<String> candidates = undamaged.isEmpty() ? def.getDamageZones() : undamaged;

        String zoneId = candidates.get((int)(Math.random() * candidates.size()));
        damaged.add(zoneId);

        encounter.log("[HULL] Zone damaged: " + zoneId + " on " + ship.getRegistryName());
        applyZoneSystemEffect(encounter, ship, zoneId, server);

        net.shard.seconddawnrp.tactical.damage.DamageModelMapper
                .destroyZone(ship.getShipId(), zoneId, null, null);
    }

    private void applyZoneSystemEffect(EncounterState encounter, ShipState ship,
                                       String zoneId, MinecraftServer server) {
        String effect = switch (zoneId) {
            case "zone.bridge"       -> "Tactical console accuracy penalty.";
            case "zone.weapons_fore" -> "Forward phaser damage reduced.";
            case "zone.weapons_aft"  -> "Aft hardpoints disabled.";
            case "zone.torpedo_bay"  -> "Torpedo tube interaction locked until Engineering repairs.";
            case "zone.shield_emit"  -> "Shield regeneration rate reduced.";
            case "zone.engines"      -> "Maximum speed and turn rate reduced.";
            case "zone.sensors"      -> "Tactical map sensor range reduced.";
            case "zone.engineering"  -> "Total power output reduced.";
            case "zone.life_support" -> "Life support compromised — crew effects incoming.";
            default                  -> "System damage reported.";
        };
        if (server != null) broadcastToCrew(server, "[DAMAGE] " + effect, Formatting.RED);
        encounter.log("[ZONE] " + zoneId + ": " + effect);
    }

    public void repairZone(ShipState ship, String zoneId) {
        Set<String> damaged = damagedZones.get(ship.getShipId());
        if (damaged != null) {
            damaged.remove(zoneId);
            System.out.println("[Tactical] Zone " + zoneId + " repaired on " + ship.getShipId());
        }
    }

    public boolean isZoneDamaged(String shipId, String zoneId) {
        Set<String> damaged = damagedZones.get(shipId);
        return damaged != null && damaged.contains(zoneId);
    }

    public Set<String> getDamagedZones(String shipId) {
        return damagedZones.getOrDefault(shipId, Set.of());
    }

    private void generateEngineeringTasks(EncounterState encounter, ShipState ship, int count) {
        encounter.log("[HULL] " + count + " repair task(s) generated for Engineering.");
    }

    /**
     * In 1.21.1, StatusEffects fields are RegistryEntry<StatusEffect>.
     * StatusEffectInstance accepts RegistryEntry<StatusEffect> directly.
     */
    private void applyCrewEffect(MinecraftServer server,
                                 net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect,
                                 int durationTicks, int amplifier) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.addStatusEffect(new StatusEffectInstance(effect, durationTicks, amplifier));
        }
    }

    private void broadcastToCrew(MinecraftServer server, String message, Formatting color) {
        Text text = Text.literal(message).formatted(color);
        server.getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(text, false));
    }
}