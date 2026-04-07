package net.shard.seconddawnrp.tactical.cc;

import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tactical.data.EncounterState;
import net.shard.seconddawnrp.tactical.data.ShipState;
import net.shard.seconddawnrp.tactical.service.EncounterService;

import java.util.*;

/**
 * ComputerCraft peripheral for the Tactical system.
 * Read-only methods expose encounter state to CC programs.
 * Write methods require appropriate certs (gated in CCPermissionBridge).
 */
public class TacticalPeripheral {

    public static Object getEncounterState() {
        if (SecondDawnRP.TACTICAL_SERVICE == null) return "OFFLINE";
        EncounterService es = SecondDawnRP.TACTICAL_SERVICE.getEncounterService();
        var encounters = es.getAllEncounters();
        if (encounters.isEmpty()) return "STANDBY";

        EncounterState enc = encounters.iterator().next();
        Map<String, Object> result = new HashMap<>();
        result.put("encounterId", enc.getEncounterId());
        result.put("status", enc.getStatus().name());
        result.put("shipCount", enc.getShipCount());
        result.put("elapsed", System.currentTimeMillis() - enc.getStartedAt());
        result.put("log", enc.getRecentLog(5));
        return result;
    }

    public static Object getAllShips() {
        if (SecondDawnRP.TACTICAL_SERVICE == null) return new ArrayList<>();
        EncounterService es = SecondDawnRP.TACTICAL_SERVICE.getEncounterService();
        List<Map<String, Object>> ships = new ArrayList<>();
        for (EncounterState enc : es.getAllEncounters()) {
            for (ShipState ship : enc.getAllShips()) {
                ships.add(shipToMap(ship));
            }
        }
        return ships;
    }

    public static Object getShipState(String shipId) {
        if (SecondDawnRP.TACTICAL_SERVICE == null) return "OFFLINE";
        for (EncounterState enc : SecondDawnRP.TACTICAL_SERVICE.getEncounterService().getAllEncounters()) {
            Optional<ShipState> ship = enc.getShip(shipId);
            if (ship.isPresent()) return shipToMap(ship.get());
        }
        return "NOT_FOUND";
    }

    public static Object getAnomalyContacts() {
        // Phase 12.1 hook — Science + Tactical integration
        return new ArrayList<>();
    }

    // ── Write methods — no CCPermissionBridge dependency for now ─────────────

    public static Object setHelm(String encounterId, String shipId,
                                 double heading, double speed) {
        if (SecondDawnRP.TACTICAL_SERVICE == null) return "OFFLINE";
        // Permission check would go here — using server-side LuckPerms in Phase 12.1
        SecondDawnRP.TACTICAL_SERVICE.applyHelmInput(
                encounterId, shipId, (float) heading, (float) speed);
        return "OK";
    }

    public static Object firePhasers(String encounterId, String attackerShipId,
                                     String targetShipId) {
        if (SecondDawnRP.TACTICAL_SERVICE == null) return "OFFLINE";
        SecondDawnRP.TACTICAL_SERVICE.queueWeaponFire(
                encounterId, attackerShipId, targetShipId, "PHASER");
        return "QUEUED";
    }

    private static Map<String, Object> shipToMap(ShipState s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shipId", s.getShipId());
        m.put("registryName", s.getRegistryName());
        m.put("combatId", s.getCombatId());
        m.put("faction", s.getFaction());
        m.put("posX", s.getPosX());
        m.put("posZ", s.getPosZ());
        m.put("heading", s.getHeading());
        m.put("speed", s.getSpeed());
        m.put("hullIntegrity", s.getHullIntegrity());
        m.put("hullMax", s.getHullMax());
        m.put("hullState", s.getHullState().name());
        m.put("shieldFore", s.getShield(ShipState.ShieldFacing.FORE));
        m.put("shieldAft", s.getShield(ShipState.ShieldFacing.AFT));
        m.put("shieldPort", s.getShield(ShipState.ShieldFacing.PORT));
        m.put("shieldStarboard", s.getShield(ShipState.ShieldFacing.STARBOARD));
        m.put("powerBudget", s.getPowerBudget());
        m.put("warpSpeed", s.getWarpSpeed());
        m.put("warpCapable", s.isWarpCapable());
        m.put("torpedoCount", s.getTorpedoCount());
        m.put("destroyed", s.isDestroyed());
        m.put("controlMode", s.getControlMode().name());
        return m;
    }
}