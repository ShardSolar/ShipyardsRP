package net.shard.seconddawnrp.warpcore.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;

/**
 * Sent client → server when a player clicks a button on the Warp Core Monitor screen.
 */
public record WarpCoreActionC2SPacket(
        String entryId,
        Action action
) implements CustomPayload {

    public enum Action { STARTUP, SHUTDOWN, RESET }

    public static final Id<WarpCoreActionC2SPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "warpcore_action"));

    public static final PacketCodec<RegistryByteBuf, WarpCoreActionC2SPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeString(value.entryId());
                        buf.writeString(value.action().name());
                    },
                    buf -> new WarpCoreActionC2SPacket(
                            buf.readString(),
                            Action.valueOf(buf.readString()))
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}