package net.shard.seconddawnrp.tactical.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleTypes;
import net.shard.seconddawnrp.tactical.data.*;
import net.shard.seconddawnrp.tactical.data.ShipState.ShieldFacing;

import java.util.*;

/**
 * Validates and resolves weapon fire actions.
 * Handles phasers, torpedoes, hardpoint cooldowns, and phaser beam particles.
 */
public class WeaponService {

    private final ShieldService shieldService;
    private final HullDamageService hullDamageService;
    private final EncounterService encounterService;

    public WeaponService(ShieldService shieldService,
                         HullDamageService hullDamageService,
                         EncounterService encounterService) {
        this.shieldService     = shieldService;
        this.hullDamageService = hullDamageService;
        this.encounterService  = encounterService;
    }

    /**
     * Tick all hardpoint cooldowns each encounter tick.
     */
    public void tick(EncounterState encounter) {
        for (ShipState ship : encounter.getAllShips()) {
            if (ship.isDestroyed()) continue;
            List<HardpointEntry> hps = encounterService.getHardpoints(ship.getShipId());
            hps.forEach(HardpointEntry::tickCooldown);
        }
    }

    /**
     * Fire a phaser from the given ship at the target ship.
     * Returns a result message.
     */
    public String firePhasers(EncounterState encounter, ShipState attacker,
                              ShipState target, MinecraftServer server) {
        ShipClassDefinition def = ShipClassDefinition.get(attacker.getShipClass()).orElse(null);
        if (def == null) return "No ship class definition found.";
        if (attacker.isDestroyed()) return "Your ship is destroyed.";
        if (target.isDestroyed()) return target.getRegistryName() + " is already destroyed.";

        // Check weapons power
        if (attacker.getWeaponsPower() < 50)
            return "Insufficient weapons power. Reroute power to weapons.";

        // Find available hardpoint covering the correct arc
        ShieldFacing impactFacing = target.getImpactFacing(attacker.getPosX(), attacker.getPosZ());
        List<HardpointEntry> hps = encounterService.getHardpoints(attacker.getShipId());

        HardpointEntry selectedHp = null;
        for (HardpointEntry hp : hps) {
            if (hp.getWeaponType() == HardpointEntry.WeaponType.PHASER_ARRAY
                    && hp.isAvailable()) {
                selectedHp = hp;
                break;
            }
        }

        // If no hardpoints registered, use class-based fallback
        boolean usedHardpoint = selectedHp != null;
        if (selectedHp != null) {
            if (attacker.getWeaponsPower() < selectedHp.getPowerDraw())
                return "Insufficient weapons power for this hardpoint.";
            selectedHp.fireCooldown();
        }

        // Calculate damage — scale by weapons power
        float powerScale  = (float) attacker.getWeaponsPower() / Math.max(1, attacker.getPowerBudget());
        int baseDamage    = usedHardpoint
                ? (int)(def.getPhaserDamage() * powerScale)
                : (int)(def.getPhaserDamage() * powerScale * 0.8f);

        // Hit resolution — accuracy affected by evasion and sensor power
        float hitChance = calculateHitChance(attacker, target);
        if (Math.random() > hitChance) {
            encounter.log("[WEAPONS] " + attacker.getCombatId() + " phaser burst: MISS");
            spawnPhaserParticles(attacker, target, server, false);
            return "Phaser burst: MISS — target evaded.";
        }

        // Apply damage
        int excess = shieldService.applyShieldDamage(target, impactFacing, baseDamage);
        String result;
        if (excess > 0) {
            hullDamageService.applyHullDamage(encounter, target, excess, server);
            result = String.format("Phaser HIT — shield %s depleted, %d hull damage. Shield: %d/%d",
                    impactFacing.name(), excess, target.getShield(impactFacing), def.getShieldMax());
        } else {
            result = String.format("Phaser HIT — %d damage to %s shield. Shield: %d",
                    baseDamage, impactFacing.name(), target.getShield(impactFacing));
        }

        encounter.log("[WEAPONS] " + attacker.getCombatId() + " → " + target.getCombatId()
                + " | " + result);
        spawnPhaserParticles(attacker, target, server, true);
        return result;
    }

    /**
     * Fire a torpedo from the given ship at the target.
     */
    public String fireTorpedo(EncounterState encounter, ShipState attacker,
                              ShipState target, MinecraftServer server) {
        if (attacker.getTorpedoCount() <= 0)
            return "No torpedoes loaded. Physical loading required in torpedo bay.";
        if (attacker.isDestroyed()) return "Your ship is destroyed.";
        if (target.isDestroyed()) return target.getRegistryName() + " is already destroyed.";

        ShipClassDefinition def = ShipClassDefinition.get(attacker.getShipClass()).orElse(null);
        if (def == null) return "No class definition.";

        attacker.setTorpedoCount(attacker.getTorpedoCount() - 1);

        ShieldFacing impactFacing = target.getImpactFacing(attacker.getPosX(), attacker.getPosZ());
        int shield = target.getShield(impactFacing);

        // Torpedoes bypass partial shields on depleted facings
        int damage = def.getTorpedoDamage();
        int excess;
        if (shield < def.getShieldMax() * 0.25f) {
            // Facing is heavily depleted — torpedo mostly bypasses
            excess = (int)(damage * 0.75f);
            shieldService.applyShieldDamage(target, impactFacing, (int)(damage * 0.25f));
        } else {
            excess = shieldService.applyShieldDamage(target, impactFacing, damage);
        }

        if (excess > 0) {
            hullDamageService.applyHullDamage(encounter, target, excess, server);
        }

        String result = String.format("TORPEDO IMPACT — %d hull damage. %d torpedoes remaining.",
                excess, attacker.getTorpedoCount());
        encounter.log("[WEAPONS] " + attacker.getCombatId() + " torpedo → " + target.getCombatId()
                + " | " + result);
        return result;
    }

    private float calculateHitChance(ShipState attacker, ShipState target) {
        float base = 0.85f;
        // Sensor power improves accuracy
        float sensorBonus = (float) attacker.getSensorsPower() / Math.max(1, attacker.getPowerBudget()) * 0.1f;
        // Speed of target reduces hit chance slightly
        float evasionPenalty = target.getSpeed() * 0.02f;
        return Math.min(0.98f, Math.max(0.30f, base + sensorBonus - evasionPenalty));
    }

    private void spawnPhaserParticles(ShipState attacker, ShipState target,
                                      MinecraftServer server, boolean hit) {
        if (server == null) return;
        // Use overworld as default particle world — in full implementation would use ship world
        ServerWorld world = server.getOverworld();
        double fromX = attacker.getPosX();
        double fromZ = attacker.getPosZ();
        double toX   = target.getPosX();
        double toZ   = target.getPosZ();
        double fromY = 64, toY = 64;

        // Spawn particle line between ships (10 points)
        for (int i = 0; i <= 10; i++) {
            double t = i / 10.0;
            double x = fromX + (toX - fromX) * t;
            double z = fromZ + (toZ - fromZ) * t;
            world.spawnParticles(hit ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SMOKE,
                    x, fromY, z, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }
}