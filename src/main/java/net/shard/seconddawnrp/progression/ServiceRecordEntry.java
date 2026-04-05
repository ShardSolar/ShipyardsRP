package net.shard.seconddawnrp.progression;

import java.util.UUID;

/**
 * A single entry in a player's service record.
 * Used for commendations, demerits, promotions, transfers, and other career events.
 */
public class ServiceRecordEntry {

    public enum Type {
        COMMENDATION,
        DEMERIT,
        PROMOTION,
        DEMOTION,
        TRANSFER,
        ENLISTMENT,
        CADET_ENROLLED,
        CADET_GRADUATED,
        DISMISSED,
        NOTE
    }

    private final String entryId;
    private final UUID playerUuid;
    private final long timestamp;
    private final Type type;
    private final int pointsDelta;
    private final String actorUuid;      // UUID string, nullable
    private final String actorName;      // snapshot of actor's display name
    private final String reason;
    private final String divisionContext; // division name at time of entry

    public ServiceRecordEntry(String entryId, UUID playerUuid, long timestamp,
                              Type type, int pointsDelta,
                              String actorUuid, String actorName,
                              String reason, String divisionContext) {
        this.entryId         = entryId;
        this.playerUuid      = playerUuid;
        this.timestamp       = timestamp;
        this.type            = type;
        this.pointsDelta     = pointsDelta;
        this.actorUuid       = actorUuid;
        this.actorName       = actorName;
        this.reason          = reason;
        this.divisionContext = divisionContext;
    }

    public static ServiceRecordEntry create(UUID playerUuid, Type type, int pointsDelta,
                                            String actorUuid, String actorName,
                                            String reason, String divisionContext) {
        return new ServiceRecordEntry(
                UUID.randomUUID().toString(),
                playerUuid,
                System.currentTimeMillis(),
                type, pointsDelta,
                actorUuid, actorName,
                reason != null ? reason : "",
                divisionContext != null ? divisionContext : ""
        );
    }

    public String getEntryId()          { return entryId; }
    public UUID getPlayerUuid()         { return playerUuid; }
    public long getTimestamp()          { return timestamp; }
    public Type getType()               { return type; }
    public int getPointsDelta()         { return pointsDelta; }
    public String getActorUuid()        { return actorUuid; }
    public String getActorName()        { return actorName; }
    public String getReason()           { return reason; }
    public String getDivisionContext()  { return divisionContext; }

    public boolean isCommendation() { return type == Type.COMMENDATION; }
    public boolean isDemerit()      { return type == Type.DEMERIT; }
    public boolean isCareerEvent()  {
        return type != Type.COMMENDATION && type != Type.DEMERIT;
    }
}