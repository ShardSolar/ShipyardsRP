package net.shard.seconddawnrp.tactical.service;

import net.shard.seconddawnrp.tactical.data.EncounterState;
import net.shard.seconddawnrp.tactical.data.ShipClassDefinition;
import net.shard.seconddawnrp.tactical.data.ShipState;
import net.shard.seconddawnrp.tactical.data.ShipState.ShieldFacing;

/**
 * Handles shield regeneration, suppression ticking, and power-scaled maximums.
 */
public class ShieldService {

    private static final int REGEN_SUPPRESSION_TICKS = 10;

    /**
     * Called every encounter tick — ticks down suppression and regenerates shields.
     */
    public void tick(EncounterState encounter) {
        for (ShipState ship : encounter.getAllShips()) {
            if (ship.isDestroyed()) continue;
            ship.tickSuppression();
            regenShields(ship);
        }
    }

    private void regenShields(ShipState ship) {
        ShipClassDefinition def = ShipClassDefinition.get(ship.getShipClass()).orElse(null);
        if (def == null) return;

        // Shield regen requires shieldsPower above minimum threshold (20%)
        if (ship.getShieldsPower() < ship.getPowerBudget() * 0.2f) return;

        int regenRate = def.getShieldRegenRate();
        int maxPerFacing = getMaxShieldPerFacing(ship, def);

        for (ShieldFacing facing : ShieldFacing.values()) {
            if (ship.getSuppression(facing) > 0) continue; // suppressed
            int current = ship.getShield(facing);
            if (current < maxPerFacing) {
                ship.setShield(facing, Math.min(maxPerFacing, current + regenRate));
            }
        }
    }

    /**
     * Max shield HP per facing scales with shieldsPower allocation.
     */
    public int getMaxShieldPerFacing(ShipState ship, ShipClassDefinition def) {
        if (ship.getPowerBudget() <= 0) return 0;
        float powerFraction = (float) ship.getShieldsPower() / ship.getPowerBudget();
        return (int)(def.getShieldMax() * Math.max(0.1f, powerFraction));
    }

    /**
     * Apply damage to a shield facing. Excess damage bleeds to hull.
     * Returns any excess damage that passed through to hull.
     */
    public int applyShieldDamage(ShipState ship, ShieldFacing facing, int damage) {
        int current = ship.getShield(facing);
        if (current <= 0) {
            // Shield already down — all damage goes to hull
            return damage;
        }

        int excess = 0;
        if (damage > current) {
            excess = damage - current;
            ship.setShield(facing, 0);
        } else {
            ship.setShield(facing, current - damage);
        }

        // Apply regen suppression on this facing
        ship.setSuppression(facing, REGEN_SUPPRESSION_TICKS);
        return excess;
    }

    /**
     * Redistribute shield power across facings.
     * Values are percentages of total shields budget — normalized automatically.
     */
    public void redistributeShields(ShipState ship, int fore, int aft, int port, int starboard) {
        int total = fore + aft + port + starboard;
        if (total <= 0) return;

        ShipClassDefinition def = ShipClassDefinition.get(ship.getShipClass()).orElse(null);
        if (def == null) return;

        int maxTotal = getMaxShieldPerFacing(ship, def) * 4;

        // Cap each facing proportionally
        ship.setShield(ShieldFacing.FORE,      Math.min(ship.getShield(ShieldFacing.FORE),      maxTotal * fore / total));
        ship.setShield(ShieldFacing.AFT,       Math.min(ship.getShield(ShieldFacing.AFT),       maxTotal * aft / total));
        ship.setShield(ShieldFacing.PORT,      Math.min(ship.getShield(ShieldFacing.PORT),      maxTotal * port / total));
        ship.setShield(ShieldFacing.STARBOARD, Math.min(ship.getShield(ShieldFacing.STARBOARD), maxTotal * starboard / total));
    }
}