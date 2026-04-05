package net.shard.seconddawnrp.transporter;

import java.util.UUID;

/**
 * A beam-up request submitted by a player in a colony dimension.
 * Stored in beam_up_requests table. Status is mutable.
 */
public class BeamUpRequest {

    public enum Status { PENDING, APPROVED, EXPIRED }

    private final String requestId;
    private final UUID playerUuid;
    private final String playerName;
    private final String sourceDimension;
    private final long requestedAt;
    private Status status;
    private String handledBy;
    private long handledAt;

    public BeamUpRequest(String requestId, UUID playerUuid, String playerName,
                         String sourceDimension, long requestedAt) {
        this.requestId       = requestId;
        this.playerUuid      = playerUuid;
        this.playerName      = playerName;
        this.sourceDimension = sourceDimension;
        this.requestedAt     = requestedAt;
        this.status          = Status.PENDING;
    }

    public String getRequestId()       { return requestId; }
    public UUID getPlayerUuid()        { return playerUuid; }
    public String getPlayerName()      { return playerName; }
    public String getSourceDimension() { return sourceDimension; }
    public long getRequestedAt()       { return requestedAt; }
    public Status getStatus()          { return status; }
    public String getHandledBy()       { return handledBy; }
    public long getHandledAt()         { return handledAt; }

    public void approve(String handlerUuid) {
        this.status    = Status.APPROVED;
        this.handledBy = handlerUuid;
        this.handledAt = System.currentTimeMillis();
    }

    public void expire() {
        this.status    = Status.EXPIRED;
        this.handledAt = System.currentTimeMillis();
    }

    public boolean isExpired(long expiryMs) {
        return status == Status.PENDING
                && (System.currentTimeMillis() - requestedAt) > expiryMs;
    }
}