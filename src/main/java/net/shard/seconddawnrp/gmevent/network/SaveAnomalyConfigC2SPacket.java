package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;

public record SaveAnomalyConfigC2SPacket(
        String entryId,
        String name,
        String description,
        String anomalyType,
        boolean activate
) implements CustomPayload {

    public static final Id<SaveAnomalyConfigC2SPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "save_anomaly_config"));

    public static final PacketCodec<RegistryByteBuf, SaveAnomalyConfigC2SPacket> CODEC =
            PacketCodec.of(
                    (v, buf) -> {
                        buf.writeString(v.entryId());
                        buf.writeString(v.name());
                        buf.writeString(v.description());
                        buf.writeString(v.anomalyType());
                        buf.writeBoolean(v.activate());
                    },
                    buf -> new SaveAnomalyConfigC2SPacket(
                            buf.readString(), buf.readString(), buf.readString(),
                            buf.readString(), buf.readBoolean())
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}