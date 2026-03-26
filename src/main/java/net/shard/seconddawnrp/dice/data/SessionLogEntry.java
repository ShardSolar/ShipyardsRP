package net.shard.seconddawnrp.dice.data;

/**
 * A single captured event in an RP PADD session log.
 * Stored in item NBT while the PADD is active.
 * Moved to the Ops review queue on submission.
 */
public class SessionLogEntry {

    public enum Type { ROLL, RP }

    private final long sessionOffsetMs; // ms since session started
    private final String characterName;
    private final Type type;
    private final String content;       // /rp text or full roll breakdown string
    private final int rollTotal;        // ROLL type only, 0 otherwise
    private final int naturalRoll;      // ROLL type only, 0 otherwise
    private final Integer dcValue;      // ROLL type only, null if no DC
    private final String outcome;       // PASS/FAIL/CRITICAL_SUCCESS/CRITICAL_FAIL/UNKNOWN

    // ── Constructor for ROLL entries ──────────────────────────────────────────

    public static SessionLogEntry roll(long sessionOffsetMs, String characterName,
                                       RollResult result) {
        return new SessionLogEntry(
                sessionOffsetMs,
                characterName,
                Type.ROLL,
                result.toPlayerString(),
                result.getTotal(),
                result.getNaturalRoll(),
                result.getDcAtTime(),
                result.getOutcome().name()
        );
    }

    // ── Constructor for RP entries ────────────────────────────────────────────

    public static SessionLogEntry rp(long sessionOffsetMs, String characterName, String action) {
        return new SessionLogEntry(
                sessionOffsetMs, characterName, Type.RP, action, 0, 0, null, "");
    }

    private SessionLogEntry(long sessionOffsetMs, String characterName, Type type,
                            String content, int rollTotal, int naturalRoll,
                            Integer dcValue, String outcome) {
        this.sessionOffsetMs = sessionOffsetMs;
        this.characterName   = characterName;
        this.type            = type;
        this.content         = content;
        this.rollTotal       = rollTotal;
        this.naturalRoll     = naturalRoll;
        this.dcValue         = dcValue;
        this.outcome         = outcome;
    }

    /** Format the offset as T+HH:MM:SS for display. */
    public String formatOffset() {
        long totalSecs = sessionOffsetMs / 1000;
        long hours   = totalSecs / 3600;
        long minutes = (totalSecs % 3600) / 60;
        long secs    = totalSecs % 60;
        return String.format("T+%02d:%02d:%02d", hours, minutes, secs);
    }

    public long getSessionOffsetMs()    { return sessionOffsetMs; }
    public String getCharacterName()    { return characterName; }
    public Type getType()               { return type; }
    public String getContent()          { return content; }
    public int getRollTotal()           { return rollTotal; }
    public int getNaturalRoll()         { return naturalRoll; }
    public Integer getDcValue()         { return dcValue; }
    public String getOutcome()          { return outcome; }
}