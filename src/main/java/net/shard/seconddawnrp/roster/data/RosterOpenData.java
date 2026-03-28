package net.shard.seconddawnrp.roster.data;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Data carried when the server opens the Roster screen on the client.
 *
 * Includes:
 *  - The filtered member list (the viewer's own division, or all divisions for admins)
 *  - The viewer's own authority level (drives which buttons render)
 *  - The division name for the screen title
 */
public record RosterOpenData(
        String        divisionName,    // display title
        List<RosterEntry> members,
        int           viewerAuthority  // Rank.authorityLevel of the viewer, or 99 for admin
) {
    public static final PacketCodec<RegistryByteBuf, RosterOpenData> PACKET_CODEC =
            PacketCodec.of(RosterOpenData::write, RosterOpenData::read);

    private void write(RegistryByteBuf buf) {
        buf.writeString(divisionName);
        buf.writeInt(members.size());
        for (RosterEntry e : members) RosterEntry.CODEC.encode(buf, e);
        buf.writeInt(viewerAuthority);
    }

    private static RosterOpenData read(RegistryByteBuf buf) {
        String division = buf.readString();
        int count = buf.readInt();
        List<RosterEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(RosterEntry.CODEC.decode(buf));
        int authority = buf.readInt();
        return new RosterOpenData(division, entries, authority);
    }
}