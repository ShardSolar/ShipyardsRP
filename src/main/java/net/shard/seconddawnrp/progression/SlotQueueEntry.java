package net.shard.seconddawnrp.progression;

import net.shard.seconddawnrp.division.Rank;

import java.util.UUID;

/**
 * A player who is eligible for promotion but cannot advance because the
 * target rank's slot cap is full.
 *
 * Queue ordering: highest serviceRecord first, then longest time at current rank.
 * Stored in SqlSlotQueueRepository.
 */
public class SlotQueueEntry {

    private final UUID playerUuid;
    private final Rank targetRank;
    private final long serviceRecord;
    private final long queuedAt;       // timestamp when added to queue
    private final long timeAtRank;     // ms the player has been at current rank

    public SlotQueueEntry(UUID playerUuid, Rank targetRank,
                          long serviceRecord, long queuedAt, long timeAtRank) {
        this.playerUuid   = playerUuid;
        this.targetRank   = targetRank;
        this.serviceRecord = serviceRecord;
        this.queuedAt     = queuedAt;
        this.timeAtRank   = timeAtRank;
    }

    public UUID getPlayerUuid()   { return playerUuid; }
    public Rank getTargetRank()   { return targetRank; }
    public long getServiceRecord(){ return serviceRecord; }
    public long getQueuedAt()     { return queuedAt; }
    public long getTimeAtRank()   { return timeAtRank; }
}