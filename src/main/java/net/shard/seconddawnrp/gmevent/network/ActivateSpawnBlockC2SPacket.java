package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

public record ActivateSpawnBlockC2SPacket(
        int x, int y, int z,
        String worldKey,
        String linkedTaskId,
        String templateId        // ← new field
) implements CustomPayload {

    public static final CustomPayload.Id<ActivateSpawnBlockC2SPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("activate_spawn_block"));

    public static final PacketCodec<RegistryByteBuf, ActivateSpawnBlockC2SPacket> CODEC =
            PacketCodec.of(ActivateSpawnBlockC2SPacket::write, ActivateSpawnBlockC2SPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }

    private void write(RegistryByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        buf.writeString(worldKey);
        buf.writeString(linkedTaskId != null ? linkedTaskId : "");
        buf.writeString(templateId != null ? templateId : "");
    }

    private static ActivateSpawnBlockC2SPacket read(RegistryByteBuf buf) {
        int x = buf.readInt(), y = buf.readInt(), z = buf.readInt();
        String worldKey = buf.readString();
        String task = buf.readString();
        String template = buf.readString();
        return new ActivateSpawnBlockC2SPacket(x, y, z, worldKey,
                task.isBlank() ? null : task,
                template.isBlank() ? null : template);
    }
}