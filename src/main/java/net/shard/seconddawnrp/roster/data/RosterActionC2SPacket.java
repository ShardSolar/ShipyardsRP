package net.shard.seconddawnrp.roster.data;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

/**
 * Single unified C2S packet for all roster actions.
 *
 * action values:
 *   PROMOTE          — promote target one rank
 *   DEMOTE           — demote target one rank
 *   CADET_ENROL      — enrol target in cadet track
 *   CADET_PROMOTE    — promote cadet one step
 *   CADET_GRADUATE   — propose graduation (rankArg = proposed starting rank id)
 *   CADET_APPROVE    — approve pending graduation
 *   COMMEND          — issue commendation (intArg = points, stringArg = reason)
 *   TRANSFER         — transfer to another division (stringArg = division name)
 *   DISMISS          — dismiss from division
 *   OPEN_DEMERIT     — request demerit sub-screen (future)
 *
 * Not all fields are used by every action — unused fields are empty/0.
 */
public record RosterActionC2SPacket(
        String action,
        String targetUuidStr,
        String stringArg,   // rank id for graduate, division for transfer, reason for commend
        int    intArg       // points for commend
) implements CustomPayload {

    public static final CustomPayload.Id<RosterActionC2SPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("roster_action"));

    public static final PacketCodec<RegistryByteBuf, RosterActionC2SPacket> CODEC =
            PacketCodec.of(RosterActionC2SPacket::write, RosterActionC2SPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }

    private void write(RegistryByteBuf buf) {
        buf.writeString(action);
        buf.writeString(targetUuidStr);
        buf.writeString(stringArg != null ? stringArg : "");
        buf.writeInt(intArg);
    }

    private static RosterActionC2SPacket read(RegistryByteBuf buf) {
        return new RosterActionC2SPacket(
                buf.readString(),
                buf.readString(),
                buf.readString(),
                buf.readInt()
        );
    }
}