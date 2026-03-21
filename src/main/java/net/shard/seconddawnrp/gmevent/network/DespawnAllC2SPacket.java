package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

public record DespawnAllC2SPacket() implements CustomPayload {

    public static final CustomPayload.Id<DespawnAllC2SPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("despawn_all"));

    public static final PacketCodec<RegistryByteBuf, DespawnAllC2SPacket> CODEC =
            PacketCodec.of(DespawnAllC2SPacket::write, DespawnAllC2SPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }

    private void write(RegistryByteBuf buf) {}
    private static DespawnAllC2SPacket read(RegistryByteBuf buf) { return new DespawnAllC2SPacket(); }
}