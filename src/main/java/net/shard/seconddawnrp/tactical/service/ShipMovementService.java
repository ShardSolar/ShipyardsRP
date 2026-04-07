package net.shard.seconddawnrp.tactical.service;

import net.shard.seconddawnrp.tactical.data.EncounterState;
import net.shard.seconddawnrp.tactical.data.ShipClassDefinition;
import net.shard.seconddawnrp.tactical.data.ShipState;

/**
 * Resolves ship movement each encounter tick.
 * Interpolates heading toward target, updates posX/posZ.
 */
public class ShipMovementService {

    /**
     * Called every 5-second encounter tick.
     * Updates heading, speed, and position for all non-destroyed ships.
     */
    public void tick(EncounterState encounter) {
        for (ShipState ship : encounter.getAllShips()) {
            if (ship.isDestroyed()) continue;
            updateMovement(ship);
        }
    }

    private void updateMovement(ShipState ship) {
        ShipClassDefinition def = ShipClassDefinition.get(ship.getShipClass()).orElse(null);
        if (def == null) return;

        float turnRate  = def.getTurnRate();
        float maxSpeed  = def.getMaxSpeed();

        // Scale speed by engine power allocation
        float engineFraction = (float) ship.getEnginesPower()
                / Math.max(1, ship.getPowerBudget());
        float effectiveMaxSpeed = maxSpeed * Math.max(0.1f, engineFraction);

        // Interpolate heading
        float current = ship.getHeading();
        float target  = ship.getTargetHeading();
        float diff    = ((target - current + 540) % 360) - 180; // shortest path
        float step    = Math.min(Math.abs(diff), turnRate) * Math.signum(diff);
        ship.setHeading(current + step);

        // Interpolate speed
        float currentSpeed = ship.getSpeed();
        float targetSpeed  = Math.min(ship.getTargetSpeed(), effectiveMaxSpeed);
        float speedDiff    = targetSpeed - currentSpeed;
        float speedStep    = Math.min(Math.abs(speedDiff), 0.5f) * Math.signum(speedDiff);
        ship.setSpeed(currentSpeed + speedStep);

        // Update position — 5-second tick so multiply by 5
        double headingRad = Math.toRadians(ship.getHeading());
        ship.setPosX(ship.getPosX() + Math.cos(headingRad) * ship.getSpeed() * 5);
        ship.setPosZ(ship.getPosZ() + Math.sin(headingRad) * ship.getSpeed() * 5);

        // Basic collision detection
        checkCollisions(ship);
    }

    private void checkCollisions(ShipState ship) {
        // Phase 14 hook — full collision handled when 3D positioning is added
        // For now just log if two ships are at nearly identical positions
    }

    /**
     * Apply evasive maneuver — randomize heading within 45 degrees, costs engine power.
     */
    public void applyEvasiveManeuver(ShipState ship) {
        float current = ship.getHeading();
        float offset  = (float)(Math.random() * 90 - 45);
        ship.setTargetHeading(current + offset);
    }
}