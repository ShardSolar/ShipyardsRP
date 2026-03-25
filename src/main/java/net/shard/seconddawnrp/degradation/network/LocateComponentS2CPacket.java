package net.shard.seconddawnrp.degradation.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;

/**
 * Sent server → specific player to trigger locator particles at a component position.
 * Client spawns END_ROD column + ring regardless of distance from the target.
 */
public record LocateComponentS2CPacket(
        String displayName,
        double x, double y, double z
) implements CustomPayload {

    public static final Id<LocateComponentS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "locate_component"));

    public static final PacketCodec<RegistryByteBuf, LocateComponentS2CPacket> CODEC =
            PacketCodec.of(
                    (v, buf) -> {
                        buf.writeString(v.displayName());
                        buf.writeDouble(v.x());
                        buf.writeDouble(v.y());
                        buf.writeDouble(v.z());
                    },
                    buf -> new LocateComponentS2CPacket(
                            buf.readString(), buf.readDouble(),
                            buf.readDouble(), buf.readDouble())
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}