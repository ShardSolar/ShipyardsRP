package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

public record PushToPoolC2SPacket(
        String templateId,
        String taskDisplayName,
        String taskDescription,
        String divisionName
) implements CustomPayload {

    public static final CustomPayload.Id<PushToPoolC2SPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("push_to_pool"));

    public static final PacketCodec<RegistryByteBuf, PushToPoolC2SPacket> CODEC =
            PacketCodec.of(PushToPoolC2SPacket::write, PushToPoolC2SPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }

    private void write(RegistryByteBuf buf) {
        buf.writeString(templateId);
        buf.writeString(taskDisplayName);
        buf.writeString(taskDescription);
        buf.writeString(divisionName);
    }

    private static PushToPoolC2SPacket read(RegistryByteBuf buf) {
        return new PushToPoolC2SPacket(
                buf.readString(), buf.readString(),
                buf.readString(), buf.readString());
    }
}