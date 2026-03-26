package net.shard.seconddawnrp.dice.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The full result of a {@code /roll} command for one player.
 * Held server-side until a GM broadcasts it.
 */
public class RollResult {

    public enum Outcome {
        CRITICAL_SUCCESS,   // natural 20
        SUCCESS,            // >= DC (if set)
        FAIL,               // < DC (if set)
        CRITICAL_FAIL,      // natural 1
        UNKNOWN             // no DC set — outcome not determined
    }

    private final UUID playerUuid;
    private final String characterName;
    private final int naturalRoll;      // raw d20 value
    private final int rankBonus;
    private final Map<String, Integer> certBonuses; // certId → bonus
    private final int demeritPenalty;
    private final int divisionBonus;    // contextual, set by GM per scenario
    private final int total;
    private final Integer dcAtTime;     // null if no DC was set
    private final Outcome outcome;
    private final long rolledAtMs;

    public RollResult(UUID playerUuid, String characterName,
                      int naturalRoll, int rankBonus,
                      Map<String, Integer> certBonuses, int demeritPenalty,
                      int divisionBonus, Integer dcAtTime) {
        this.playerUuid    = playerUuid;
        this.characterName = characterName;
        this.naturalRoll   = naturalRoll;
        this.rankBonus     = rankBonus;
        this.certBonuses   = new LinkedHashMap<>(certBonuses);
        this.demeritPenalty = demeritPenalty;
        this.divisionBonus = divisionBonus;
        this.dcAtTime      = dcAtTime;
        this.rolledAtMs    = System.currentTimeMillis();

        int certTotal = certBonuses.values().stream().mapToInt(Integer::intValue).sum();
        this.total = naturalRoll + rankBonus + certTotal + divisionBonus - demeritPenalty;

        // Determine outcome
        if (naturalRoll == 20) {
            this.outcome = Outcome.CRITICAL_SUCCESS;
        } else if (naturalRoll == 1) {
            this.outcome = Outcome.CRITICAL_FAIL;
        } else if (dcAtTime != null) {
            this.outcome = total >= dcAtTime ? Outcome.SUCCESS : Outcome.FAIL;
        } else {
            this.outcome = Outcome.UNKNOWN;
        }
    }

    // ── Formatted strings ─────────────────────────────────────────────────────

    /** What the rolling player sees immediately: full breakdown. */
    public String toPlayerString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ROLL] You rolled ").append(naturalRoll);
        if (rankBonus != 0)    sb.append(" + ").append(rankBonus).append(" (rank)");
        if (divisionBonus != 0) sb.append(" + ").append(divisionBonus).append(" (division)");
        certBonuses.forEach((id, bonus) -> {
            if (bonus != 0) {
                String shortId = id.contains(".") ? id.substring(id.lastIndexOf('.') + 1) : id;
                sb.append(" + ").append(bonus).append(" (").append(shortId).append(")");
            }
        });
        if (demeritPenalty != 0) sb.append(" - ").append(demeritPenalty).append(" (demerit)");
        sb.append(" = ").append(total);
        if (dcAtTime != null) sb.append(" vs DC ").append(dcAtTime);
        return sb.toString();
    }

    /** What gets broadcast to the scene when GM releases it. */
    public String toBroadcastString() {
        if (naturalRoll == 20) {
            return "[ROLL] " + characterName + " rolled a NATURAL 20 — critical success!";
        }
        if (naturalRoll == 1) {
            return "[ROLL] " + characterName + " rolled a NATURAL 1 — critical fail!";
        }
        StringBuilder sb = new StringBuilder("[ROLL] ")
                .append(characterName).append(" rolled ").append(total);
        if (dcAtTime != null) {
            sb.append(" vs DC ").append(dcAtTime)
                    .append(" — ").append(outcome == Outcome.SUCCESS ? "PASS" : "FAIL");
        }
        return sb.toString();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID getPlayerUuid()         { return playerUuid; }
    public String getCharacterName()    { return characterName; }
    public int getNaturalRoll()         { return naturalRoll; }
    public int getRankBonus()           { return rankBonus; }
    public Map<String, Integer> getCertBonuses() { return certBonuses; }
    public int getDemeritPenalty()      { return demeritPenalty; }
    public int getDivisionBonus()       { return divisionBonus; }
    public int getTotal()               { return total; }
    public Integer getDcAtTime()        { return dcAtTime; }
    public Outcome getOutcome()         { return outcome; }
    public long getRolledAtMs()         { return rolledAtMs; }

    public boolean isNatural20()        { return naturalRoll == 20; }
    public boolean isNatural1()         { return naturalRoll == 1; }
}