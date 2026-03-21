package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

public record FireSpawnC2SPacket(
        String templateId,
        int x, int y, int z,
        String worldKey
) implements CustomPayload {

    public static final CustomPayload.Id<FireSpawnC2SPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("fire_spawn"));

    public static final PacketCodec<RegistryByteBuf, FireSpawnC2SPacket> CODEC =
            PacketCodec.of(FireSpawnC2SPacket::write, FireSpawnC2SPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }

    private void write(RegistryByteBuf buf) {
        buf.writeString(templateId);
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
        buf.writeString(worldKey);
    }

    private static FireSpawnC2SPacket read(RegistryByteBuf buf) {
        return new FireSpawnC2SPacket(buf.readString(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readString());
    }
}