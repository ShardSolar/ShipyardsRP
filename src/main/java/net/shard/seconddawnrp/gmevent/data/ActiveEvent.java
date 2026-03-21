package net.shard.seconddawnrp.gmevent.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ActiveEvent {

    private final String eventId;
    private final String templateId;
    private final String linkedTaskId;
    private final long startedAtEpochMillis;
    private final List<UUID> spawnedMobUuids;
    private int totalSpawned;
    private int totalKilled;
    private boolean ended;

    public ActiveEvent(String eventId, String templateId, String linkedTaskId) {
        this.eventId             = Objects.requireNonNull(eventId, "eventId");
        this.templateId          = Objects.requireNonNull(templateId, "templateId");
        this.linkedTaskId        = linkedTaskId;
        this.startedAtEpochMillis = System.currentTimeMillis();
        this.spawnedMobUuids     = new ArrayList<>();
        this.totalSpawned        = 0;
        this.totalKilled         = 0;
        this.ended               = false;
    }

    public String getEventId()             { return eventId; }
    public String getTemplateId()          { return templateId; }
    public String getLinkedTaskId()        { return linkedTaskId; }
    public long getStartedAtEpochMillis()  { return startedAtEpochMillis; }
    public List<UUID> getSpawnedMobUuids() { return spawnedMobUuids; }
    public int getTotalSpawned()           { return totalSpawned; }
    public int getTotalKilled()            { return totalKilled; }
    public boolean isEnded()               { return ended; }

    public void addSpawnedMob(UUID uuid)   { spawnedMobUuids.add(uuid); totalSpawned++; }
    public void recordKill(UUID uuid)      { spawnedMobUuids.remove(uuid); totalKilled++; }
    public void end()                      { ended = true; }

    public int getActiveCount()            { return spawnedMobUuids.size(); }
}