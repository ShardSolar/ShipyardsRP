package net.shard.seconddawnrp.tactical.service;

import net.minecraft.server.MinecraftServer;
import net.shard.seconddawnrp.tactical.data.*;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking;

import java.util.Optional;
import java.util.UUID;

/**
 * Encounter tick orchestrator. Runs on END_SERVER_TICK every 100 ticks (5 seconds).
 * Coordinates all sub-services in order: Power → Movement → Shields → Weapons → Warp → Hull check.
 * Broadcasts S2CEncounterUpdatePacket to all open Tactical screens after each tick.
 */
public class TacticalService {

    private static final int TICK_INTERVAL = 100; // 5 seconds at 20 TPS

    private final EncounterService  encounterService;
    private final ShipMovementService movementService;
    private final ShieldService       shieldService;
    private final WeaponService       weaponService;
    private final PowerService        powerService;
    private final HullDamageService   hullDamageService;
    private final WarpService         warpService;

    private MinecraftServer server;

    // Pending weapon fire actions queued from C2S packets, resolved on next tick
    private final java.util.concurrent.ConcurrentLinkedQueue<PendingFireAction> pendingFire =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    public TacticalService(EncounterService encounterService) {
        this.encounterService = encounterService;
        this.hullDamageService = new HullDamageService();
        this.shieldService     = new ShieldService();
        this.movementService   = new ShipMovementService();
        this.powerService      = new PowerService();
        this.weaponService     = new WeaponService(shieldService, hullDamageService, encounterService);
        this.warpService       = new WarpService();
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── Main tick ─────────────────────────────────────────────────────────────

    public void tick(MinecraftServer server, int currentTick) {
        if (currentTick % TICK_INTERVAL != 0) return;

        for (EncounterState encounter : encounterService.getAllEncounters()) {
            if (!encounter.isActive()) continue;
            tickEncounter(encounter);
        }
    }

    private void tickEncounter(EncounterState encounter) {
        // 1. Update power budgets from warp core
        powerService.tick(encounter);

        // 2. Resolve movement
        movementService.tick(encounter);

        // 3. Tick shield suppression and regen
        shieldService.tick(encounter);

        // 4. Tick weapon cooldowns
        weaponService.tick(encounter);

        // 5. Resolve queued weapon fire actions
        resolvePendingFire(encounter);

        // 6. Update warp state
        warpService.tick(encounter, server);

        // 7. Check for destroyed ships
        for (ShipState ship : encounter.getAllShips()) {
            if (!ship.isDestroyed() && ship.getHullIntegrity() <= 0) {
                encounterService.handleShipDestroyed(encounter, ship);
            }
        }

        // 8. Broadcast updated state to all open Tactical screens
        TacticalNetworking.broadcastEncounterUpdate(encounter, server);
    }

    private void resolvePendingFire(EncounterState encounter) {
        PendingFireAction action;
        while ((action = pendingFire.poll()) != null) {
            if (!action.encounterId().equals(encounter.getEncounterId())) {
                pendingFire.offer(action); // put back for another encounter
                continue;
            }

            Optional<ShipState> attacker = encounter.getShip(action.attackerShipId());
            Optional<ShipState> target   = encounter.getShip(action.targetShipId());
            if (attacker.isEmpty() || target.isEmpty()) continue;

            String result = switch (action.weaponType()) {
                case "PHASER"  -> weaponService.firePhasers(encounter, attacker.get(), target.get(), server);
                case "TORPEDO" -> weaponService.fireTorpedo(encounter, attacker.get(), target.get(), server);
                default        -> "Unknown weapon type.";
            };
            encounter.log("[FIRE] " + result);
        }
    }

    // ── Action queuing (from C2S packets) ────────────────────────────────────

    public void queueWeaponFire(String encounterId, String attackerShipId,
                                String targetShipId, String weaponType) {
        pendingFire.offer(new PendingFireAction(encounterId, attackerShipId, targetShipId, weaponType));
    }

    public void applyHelmInput(String encounterId, String shipId,
                               float targetHeading, float targetSpeed) {
        encounterService.getEncounter(encounterId)
                .flatMap(e -> e.getShip(shipId))
                .ifPresent(ship -> {
                    ship.setTargetHeading(targetHeading);
                    ship.setTargetSpeed(targetSpeed);
                });
    }

    public void applyPowerReroute(String encounterId, String shipId,
                                  int weapons, int shields, int engines, int sensors) {
        encounterService.getEncounter(encounterId)
                .flatMap(e -> e.getShip(shipId))
                .ifPresent(ship -> powerService.setManualAllocation(ship, weapons, shields, engines, sensors));
    }

    public void applyShieldDistribution(String encounterId, String shipId,
                                        int fore, int aft, int port, int starboard) {
        encounterService.getEncounter(encounterId)
                .flatMap(e -> e.getShip(shipId))
                .ifPresent(ship -> shieldService.redistributeShields(ship, fore, aft, port, starboard));
    }

    public void applyEvasiveManeuver(String encounterId, String shipId) {
        encounterService.getEncounter(encounterId)
                .flatMap(e -> e.getShip(shipId))
                .ifPresent(movementService::applyEvasiveManeuver);
    }

    public String engageWarp(String encounterId, String shipId, int warpFactor) {
        return encounterService.getEncounter(encounterId)
                .flatMap(e -> e.getShip(shipId))
                .map(ship -> warpService.engageWarp(ship, warpFactor))
                .orElse("Ship or encounter not found.");
    }

    // ── Sub-service accessors (for commands / screen) ─────────────────────────

    public EncounterService  getEncounterService()  { return encounterService; }
    public HullDamageService getHullDamageService() { return hullDamageService; }
    public WarpService       getWarpService()       { return warpService; }

    private record PendingFireAction(String encounterId, String attackerShipId,
                                     String targetShipId, String weaponType) {}
}