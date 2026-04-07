package net.shard.seconddawnrp.tactical.data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Active encounter — contains all ships, combat log, and tick state.
 */
public class EncounterState {

    public enum Status { READY, ACTIVE, PAUSED, ENDED }

    private final String encounterId;
    private Status status;
    private final long createdAt;
    private long startedAt;
    private long endedAt;

    private final Map<String, ShipState> ships = new ConcurrentHashMap<>();
    private final List<String> combatLog = Collections.synchronizedList(new ArrayList<>());

    // Combat ID assignment counters
    private int friendlyCombatIndex = 0;
    private int hostileCombatIndex  = 0;
    private static final String[] FRIENDLY_IDS = {"Alpha", "Bravo", "Charlie", "Delta", "Echo"};

    public EncounterState(String encounterId) {
        this.encounterId = encounterId;
        this.status      = Status.READY;
        this.createdAt   = System.currentTimeMillis();
    }

    // ── Ship management ───────────────────────────────────────────────────────

    public void addShip(ShipState ship) {
        String combatId = assignCombatId(ship.getFaction());
        ship.setCombatId(combatId);
        ships.put(ship.getShipId(), ship);
        log("Ship " + ship.getRegistryName() + " [" + combatId + "] joined the encounter.");
    }

    private String assignCombatId(String faction) {
        if ("FRIENDLY".equals(faction)) {
            if (friendlyCombatIndex < FRIENDLY_IDS.length)
                return FRIENDLY_IDS[friendlyCombatIndex++];
            return "Friendly-" + (++friendlyCombatIndex);
        } else {
            return "Hostile-" + (++hostileCombatIndex);
        }
    }

    public Optional<ShipState> getShip(String shipId) {
        return Optional.ofNullable(ships.get(shipId));
    }

    public Collection<ShipState> getAllShips() {
        return ships.values();
    }

    public List<ShipState> getFriendlyShips() {
        return ships.values().stream()
                .filter(s -> "FRIENDLY".equals(s.getFaction()))
                .toList();
    }

    public List<ShipState> getHostileShips() {
        return ships.values().stream()
                .filter(s -> "HOSTILE".equals(s.getFaction()))
                .toList();
    }

    public boolean removeShip(String shipId) {
        ShipState ship = ships.remove(shipId);
        if (ship != null) {
            log("Ship " + ship.getRegistryName() + " [" + ship.getCombatId() + "] left the encounter.");
            return true;
        }
        return false;
    }

    // ── Status transitions ────────────────────────────────────────────────────

    public boolean start() {
        if (status != Status.READY) return false;
        status    = Status.ACTIVE;
        startedAt = System.currentTimeMillis();
        log("=== ENCOUNTER STARTED ===");
        return true;
    }

    public boolean pause() {
        if (status != Status.ACTIVE) return false;
        status = Status.PAUSED;
        log("=== ENCOUNTER PAUSED ===");
        return true;
    }

    public boolean resume() {
        if (status != Status.PAUSED) return false;
        status = Status.ACTIVE;
        log("=== ENCOUNTER RESUMED ===");
        return true;
    }

    public void end(String reason) {
        status  = Status.ENDED;
        endedAt = System.currentTimeMillis();
        log("=== ENCOUNTER ENDED: " + reason + " ===");
    }

    // ── Log ───────────────────────────────────────────────────────────────────

    public void log(String message) {
        String entry = "[" + System.currentTimeMillis() + "] " + message;
        combatLog.add(entry);
        // Keep last 200 entries
        while (combatLog.size() > 200) combatLog.remove(0);
    }

    /** Returns the last N log entries for display. */
    public List<String> getRecentLog(int count) {
        int size  = combatLog.size();
        int start = Math.max(0, size - count);
        return new ArrayList<>(combatLog.subList(start, size));
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getEncounterId() { return encounterId; }
    public Status getStatus()      { return status; }
    public long getCreatedAt()     { return createdAt; }
    public long getStartedAt()     { return startedAt; }
    public long getEndedAt()       { return endedAt; }
    public boolean isActive()      { return status == Status.ACTIVE; }
    public int getShipCount()      { return ships.size(); }
}