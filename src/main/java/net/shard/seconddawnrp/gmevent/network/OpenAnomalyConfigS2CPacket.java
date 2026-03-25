package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.AnomalyEntry;
import net.shard.seconddawnrp.gmevent.data.AnomalyType;

public record OpenAnomalyConfigS2CPacket(
        String entryId,
        String name,
        String description,
        String anomalyType,
        boolean active
) implements CustomPayload {

    public static final Id<OpenAnomalyConfigS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "open_anomaly_config"));

    public static final PacketCodec<RegistryByteBuf, OpenAnomalyConfigS2CPacket> CODEC =
            PacketCodec.of(
                    (v, buf) -> {
                        buf.writeString(v.entryId());
                        buf.writeString(v.name());
                        buf.writeString(v.description() != null ? v.description() : "");
                        buf.writeString(v.anomalyType());
                        buf.writeBoolean(v.active());
                    },
                    buf -> new OpenAnomalyConfigS2CPacket(
                            buf.readString(), buf.readString(), buf.readString(),
                            buf.readString(), buf.readBoolean())
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    public static OpenAnomalyConfigS2CPacket from(AnomalyEntry e) {
        return new OpenAnomalyConfigS2CPacket(
                e.getEntryId(), e.getName(),
                e.getDescription() != null ? e.getDescription() : "",
                e.getType().name(), e.isActive());
    }
}