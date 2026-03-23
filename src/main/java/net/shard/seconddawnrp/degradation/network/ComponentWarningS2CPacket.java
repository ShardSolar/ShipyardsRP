package net.shard.seconddawnrp.degradation.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;

/**
 * Sent by the server to nearby players when a component crosses a warning
 * threshold or its warning pulse timer fires.
 *
 * <p>The client uses this packet to spawn particle effects at the
 * component's block position. Particle type and density scale with
 * {@link ComponentStatus}.
 */
public record ComponentWarningS2CPacket(
        String componentId,
        String worldKey,
        long blockPosLong,
        ComponentStatus status,
        int health
) implements CustomPayload {

    public static final Id<ComponentWarningS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "component_warning"));

    public static final PacketCodec<RegistryByteBuf, ComponentWarningS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeString(value.componentId());
                        buf.writeString(value.worldKey());
                        buf.writeLong(value.blockPosLong());
                        buf.writeString(value.status().name());
                        buf.writeInt(value.health());
                    },
                    buf -> new ComponentWarningS2CPacket(
                            buf.readString(),
                            buf.readString(),
                            buf.readLong(),
                            ComponentStatus.valueOf(buf.readString()),
                            buf.readInt()
                    )
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}