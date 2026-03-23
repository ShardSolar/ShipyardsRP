package net.shard.seconddawnrp.degradation.data;

import java.util.UUID;

/**
 * A registered, maintainable block in the world.
 *
 * <p>Identity key: {@code worldKey + blockPos}. Component IDs are
 * auto-generated server-side from the block type and position, matching
 * the same collision-suffix pattern used by the task system.
 */
public class ComponentEntry {

    private final String componentId;
    private final String worldKey;
    private final long blockPosLong;
    private final String blockTypeId;
    private final String displayName;
    private int health;
    private ComponentStatus status;
    private long lastDrainTickMs;
    private long lastTaskGeneratedMs;
    private UUID registeredByUuid;
    private String repairItemId;   // null = use global default
    private int repairItemCount;   // 0 = use global default

    public ComponentEntry(
            String componentId,
            String worldKey,
            long blockPosLong,
            String blockTypeId,
            String displayName,
            int health,
            ComponentStatus status,
            long lastDrainTickMs,
            long lastTaskGeneratedMs,
            UUID registeredByUuid,
            String repairItemId,
            int repairItemCount) {
        this.componentId = componentId;
        this.worldKey = worldKey;
        this.blockPosLong = blockPosLong;
        this.blockTypeId = blockTypeId;
        this.displayName = displayName;
        this.health = health;
        this.status = status;
        this.lastDrainTickMs = lastDrainTickMs;
        this.lastTaskGeneratedMs = lastTaskGeneratedMs;
        this.registeredByUuid = registeredByUuid;
        this.repairItemId = repairItemId;
        this.repairItemCount = repairItemCount;
    }

    // ── Mutators ─────────────────────────────────────────────────────────────

    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(100, health));
        this.status = ComponentStatus.fromHealth(this.health);
    }

    public void setLastDrainTickMs(long lastDrainTickMs) {
        this.lastDrainTickMs = lastDrainTickMs;
    }

    public void setLastTaskGeneratedMs(long lastTaskGeneratedMs) {
        this.lastTaskGeneratedMs = lastTaskGeneratedMs;
    }

    public void setStatus(ComponentStatus status) {
        this.status = status;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getComponentId() { return componentId; }
    public String getWorldKey() { return worldKey; }
    public long getBlockPosLong() { return blockPosLong; }
    public String getBlockTypeId() { return blockTypeId; }
    public String getDisplayName() { return displayName; }
    public int getHealth() { return health; }
    public ComponentStatus getStatus() { return status; }
    public long getLastDrainTickMs() { return lastDrainTickMs; }
    public long getLastTaskGeneratedMs() { return lastTaskGeneratedMs; }
    public UUID getRegisteredByUuid() { return registeredByUuid; }

    public String getRepairItemId() { return repairItemId; }
    public int getRepairItemCount() { return repairItemCount; }
    public void setRepairItemId(String repairItemId) { this.repairItemId = repairItemId; }
    public void setRepairItemCount(int repairItemCount) { this.repairItemCount = repairItemCount; }

    @Override
    public String toString() {
        return "ComponentEntry{id='" + componentId + "', health=" + health
                + ", status=" + status + ", world='" + worldKey + "', pos=" + blockPosLong + '}';
    }
}