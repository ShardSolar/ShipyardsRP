package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.SpawnBehaviour;

import java.util.ArrayList;
import java.util.List;

public record SaveTemplateC2SPacket(
        String id,
        String displayName,
        String mobTypeId,
        double maxHealth,
        double armor,
        int totalSpawnCount,
        int maxActiveAtOnce,
        int spawnRadiusBlocks,
        int spawnIntervalTicks,
        String spawnBehaviour,
        List<String> statusEffects
) implements CustomPayload {

    public static final CustomPayload.Id<SaveTemplateC2SPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("save_template"));

    public static final PacketCodec<RegistryByteBuf, SaveTemplateC2SPacket> CODEC =
            PacketCodec.of(SaveTemplateC2SPacket::write, SaveTemplateC2SPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }

    private void write(RegistryByteBuf buf) {
        buf.writeString(id);
        buf.writeString(displayName);
        buf.writeString(mobTypeId);
        buf.writeDouble(maxHealth);
        buf.writeDouble(armor);
        buf.writeInt(totalSpawnCount);
        buf.writeInt(maxActiveAtOnce);
        buf.writeInt(spawnRadiusBlocks);
        buf.writeInt(spawnIntervalTicks);
        buf.writeString(spawnBehaviour);
        buf.writeInt(statusEffects.size());
        for (String e : statusEffects) buf.writeString(e);
    }

    private static SaveTemplateC2SPacket read(RegistryByteBuf buf) {
        String id = buf.readString();
        String displayName = buf.readString();
        String mobTypeId = buf.readString();
        double maxHealth = buf.readDouble();
        double armor = buf.readDouble();
        int totalSpawnCount = buf.readInt();
        int maxActiveAtOnce = buf.readInt();
        int spawnRadiusBlocks = buf.readInt();
        int spawnIntervalTicks = buf.readInt();
        String spawnBehaviour = buf.readString();
        int effectCount = buf.readInt();
        List<String> effects = new ArrayList<>();
        for (int i = 0; i < effectCount; i++) effects.add(buf.readString());
        return new SaveTemplateC2SPacket(id, displayName, mobTypeId, maxHealth, armor,
                totalSpawnCount, maxActiveAtOnce, spawnRadiusBlocks, spawnIntervalTicks,
                spawnBehaviour, effects);
    }

    public SpawnBehaviour getBehaviour() {
        try { return SpawnBehaviour.valueOf(spawnBehaviour); }
        catch (Exception e) { return SpawnBehaviour.INSTANT; }
    }
}