package net.shard.seconddawnrp.gmevent.data;

import net.minecraft.util.math.BlockPos;
import java.util.Objects;

public class SpawnBlockEntry {

    private String worldKey;
    private long blockPosLong;
    private String templateId;
    private String linkedTaskId;

    public SpawnBlockEntry() {}

    public SpawnBlockEntry(String worldKey, BlockPos pos, String templateId, String linkedTaskId) {
        this.worldKey     = Objects.requireNonNull(worldKey, "worldKey");
        this.blockPosLong = pos.asLong();
        this.templateId   = templateId;
        this.linkedTaskId = linkedTaskId;
    }

    public String getWorldKey()      { return worldKey; }
    public long getBlockPosLong()    { return blockPosLong; }
    public BlockPos getBlockPos()    { return BlockPos.fromLong(blockPosLong); }
    public String getTemplateId()    { return templateId; }
    public String getLinkedTaskId()  { return linkedTaskId; }

    public void setTemplateId(String templateId)    { this.templateId = templateId; }
    public void setLinkedTaskId(String linkedTaskId) { this.linkedTaskId = linkedTaskId; }

    public boolean matches(String worldKey, BlockPos pos) {
        return this.worldKey.equals(worldKey) && this.blockPosLong == pos.asLong();
    }

    public String positionKey() {
        return worldKey + ":" + blockPosLong;
    }
}