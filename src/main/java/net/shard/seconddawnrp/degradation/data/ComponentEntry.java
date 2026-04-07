package net.shard.seconddawnrp.degradation.data;

import java.util.UUID;

/**
 * A registered, maintainable block in the world.
 *
 * Identity key: worldKey + blockPos.
 * Component IDs are auto-generated server-side from the block type and position.
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

    /**
     * True when the registered block no longer exists at the registered position.
     * The component remains registered so engineering can restore it.
     */
    private boolean missingBlock;

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
            int repairItemCount,
            boolean missingBlock
    ) {
        this.componentId = componentId;
        this.worldKey = worldKey;
        this.blockPosLong = blockPosLong;
        this.blockTypeId = blockTypeId;
        this.displayName = displayName;
        this.health = Math.max(0, Math.min(100, health));
        this.status = status != null ? status : ComponentStatus.fromHealth(this.health);
        this.lastDrainTickMs = lastDrainTickMs;
        this.lastTaskGeneratedMs = lastTaskGeneratedMs;
        this.registeredByUuid = registeredByUuid;
        this.repairItemId = repairItemId;
        this.repairItemCount = repairItemCount;
        this.missingBlock = missingBlock;

        normalizeState();
    }

    // ── Mutators ─────────────────────────────────────────────────────────────

    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(100, health));
        this.status = ComponentStatus.fromHealth(this.health);
        if (this.health > 0) {
            // If health has been restored above 0, the entry is no longer intrinsically offline.
            // Missing state is controlled separately.
            if (this.missingBlock) {
                this.status = ComponentStatus.OFFLINE;
            }
        }
    }

    public void setLastDrainTickMs(long lastDrainTickMs) {
        this.lastDrainTickMs = lastDrainTickMs;
    }

    public void setLastTaskGeneratedMs(long lastTaskGeneratedMs) {
        this.lastTaskGeneratedMs = lastTaskGeneratedMs;
    }

    public void setStatus(ComponentStatus status) {
        this.status = status != null ? status : ComponentStatus.fromHealth(this.health);
        normalizeState();
    }

    public void setRepairItemId(String repairItemId) {
        this.repairItemId = repairItemId;
    }

    public void setRepairItemCount(int repairItemCount) {
        this.repairItemCount = repairItemCount;
    }

    public void setMissingBlock(boolean missingBlock) {
        this.missingBlock = missingBlock;
        normalizeState();
    }

    /**
     * Forces consistency between health, status, and missingBlock.
     */
    public void normalizeState() {
        this.health = Math.max(0, Math.min(100, this.health));

        if (this.missingBlock) {
            this.health = 0;
            this.status = ComponentStatus.OFFLINE;
            return;
        }

        this.status = ComponentStatus.fromHealth(this.health);
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
    public boolean isMissingBlock() { return missingBlock; }

    @Override
    public String toString() {
        return "ComponentEntry{id='" + componentId
                + "', health=" + health
                + ", status=" + status
                + ", missingBlock=" + missingBlock
                + ", world='" + worldKey
                + "', pos=" + blockPosLong + "'}";
    }
}