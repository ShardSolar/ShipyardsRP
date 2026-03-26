package net.shard.seconddawnrp.dice.data;

import java.util.*;

/**
 * Server-side state for a {@code /gm roll group} session.
 * Created by GM, players are prompted to {@code /roll}.
 * Results collected until 60s timeout or all players have rolled.
 */
public class GroupRollSession {

    /** How long to wait for each player before excluding them. */
    public static final long TIMEOUT_MS = 60_000;

    private final UUID gmUuid;
    private final List<UUID> expectedPlayers;
    private final Map<UUID, RollResult> results = new LinkedHashMap<>();
    private final long startedAtMs;

    public GroupRollSession(UUID gmUuid, List<UUID> expectedPlayers) {
        this.gmUuid           = gmUuid;
        this.expectedPlayers  = new ArrayList<>(expectedPlayers);
        this.startedAtMs      = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - startedAtMs > TIMEOUT_MS;
    }

    public boolean isComplete() {
        return results.size() >= expectedPlayers.size();
    }

    public boolean expects(UUID playerUuid) {
        return expectedPlayers.contains(playerUuid);
    }

    public void addResult(UUID playerUuid, RollResult result) {
        results.put(playerUuid, result);
    }

    public boolean hasResult(UUID uuid) { return results.containsKey(uuid); }

    // ── Computed values for GM ────────────────────────────────────────────────

    public int highest() {
        return results.values().stream().mapToInt(RollResult::getTotal).max().orElse(0);
    }

    public int lowest() {
        return results.values().stream().mapToInt(RollResult::getTotal).min().orElse(0);
    }

    public int sum() {
        return results.values().stream().mapToInt(RollResult::getTotal).sum();
    }

    public int average() {
        if (results.isEmpty()) return 0;
        return sum() / results.size();
    }

    public List<RollResult> getNatural20s() {
        return results.values().stream().filter(RollResult::isNatural20).toList();
    }

    public List<RollResult> getNatural1s() {
        return results.values().stream().filter(RollResult::isNatural1).toList();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID getGmUuid()                         { return gmUuid; }
    public List<UUID> getExpectedPlayers()          { return expectedPlayers; }
    public Map<UUID, RollResult> getResults()       { return results; }
    public long getStartedAtMs()                    { return startedAtMs; }

    public List<UUID> getPendingPlayers() {
        return expectedPlayers.stream()
                .filter(uuid -> !results.containsKey(uuid))
                .toList();
    }
}