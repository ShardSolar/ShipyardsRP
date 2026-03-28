package net.shard.seconddawnrp.roster.data;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

/**
 * Sent server → client after any roster action completes successfully.
 * The client replaces its member list and re-renders.
 *
 * Also carries a feedback message shown at the top of the screen briefly.
 */
public record RosterRefreshS2CPacket(
        RosterOpenData data,
        String feedbackMessage   // shown briefly after action, empty = no message
) implements CustomPayload {

    public static final CustomPayload.Id<RosterRefreshS2CPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("roster_refresh"));

    public static final PacketCodec<RegistryByteBuf, RosterRefreshS2CPacket> CODEC =
            PacketCodec.of(RosterRefreshS2CPacket::write, RosterRefreshS2CPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }

    private void write(RegistryByteBuf buf) {
        RosterOpenData.PACKET_CODEC.encode(buf, data);
        buf.writeString(feedbackMessage != null ? feedbackMessage : "");
    }

    private static RosterRefreshS2CPacket read(RegistryByteBuf buf) {
        RosterOpenData data = RosterOpenData.PACKET_CODEC.decode(buf);
        String msg = buf.readString();
        return new RosterRefreshS2CPacket(data, msg);
    }
}