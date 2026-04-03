package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

public record DespawnToolSpawnedC2SPacket() implements CustomPayload {

    public static final CustomPayload.Id<DespawnToolSpawnedC2SPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("despawn_tool_spawned"));

    public static final PacketCodec<RegistryByteBuf, DespawnToolSpawnedC2SPacket> CODEC =
            PacketCodec.of(DespawnToolSpawnedC2SPacket::write, DespawnToolSpawnedC2SPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(RegistryByteBuf buf) {}

    private static DespawnToolSpawnedC2SPacket read(RegistryByteBuf buf) {
        return new DespawnToolSpawnedC2SPacket();
    }
}