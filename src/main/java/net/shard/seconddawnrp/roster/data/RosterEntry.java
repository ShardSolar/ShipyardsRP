package net.shard.seconddawnrp.roster.data;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.List;

/**
 * Lightweight snapshot of a single division member.
 * Sent to the client as part of RosterOpenData — no server calls from the screen.
 *
 * All display data is pre-built server-side in RosterService.buildEntries().
 */
public record RosterEntry(
        String playerUuidStr,        // for action packets
        String characterName,        // in-character name (or Minecraft name if none)
        String minecraftName,
        String rankId,               // Rank.name()
        String rankDisplayName,      // formatted for display
        String divisionName,
        String progressionPath,      // ENLISTED or COMMISSIONED
        int    rankPoints,
        long   serviceRecord,
        List<String> certifications, // cert/billet display names
        boolean mustang,
        String shipPosition,         // NONE, FIRST_OFFICER, SECOND_OFFICER
        boolean isOnline,
        int    pointsToNextRank,     // computed server-side, -1 if at cap
        String notes                 // officer notes field (future roster service)
) {
    public static final PacketCodec<RegistryByteBuf, RosterEntry> CODEC =
            PacketCodec.of(RosterEntry::write, RosterEntry::read);

    private void write(RegistryByteBuf buf) {
        buf.writeString(playerUuidStr);
        buf.writeString(characterName);
        buf.writeString(minecraftName);
        buf.writeString(rankId);
        buf.writeString(rankDisplayName);
        buf.writeString(divisionName);
        buf.writeString(progressionPath);
        buf.writeInt(rankPoints);
        buf.writeLong(serviceRecord);
        buf.writeInt(certifications.size());
        for (String c : certifications) buf.writeString(c);
        buf.writeBoolean(mustang);
        buf.writeString(shipPosition);
        buf.writeBoolean(isOnline);
        buf.writeInt(pointsToNextRank);
        buf.writeString(notes != null ? notes : "");
    }

    private static RosterEntry read(RegistryByteBuf buf) {
        String playerUuidStr   = buf.readString();
        String characterName   = buf.readString();
        String minecraftName   = buf.readString();
        String rankId          = buf.readString();
        String rankDisplayName = buf.readString();
        String divisionName    = buf.readString();
        String progressionPath = buf.readString();
        int    rankPoints      = buf.readInt();
        long   serviceRecord   = buf.readLong();
        int    certCount       = buf.readInt();
        List<String> certs     = new java.util.ArrayList<>(certCount);
        for (int i = 0; i < certCount; i++) certs.add(buf.readString());
        boolean mustang        = buf.readBoolean();
        String shipPosition    = buf.readString();
        boolean isOnline       = buf.readBoolean();
        int     pointsToNext   = buf.readInt();
        String  notes          = buf.readString();
        return new RosterEntry(playerUuidStr, characterName, minecraftName,
                rankId, rankDisplayName, divisionName, progressionPath,
                rankPoints, serviceRecord, certs, mustang, shipPosition,
                isOnline, pointsToNext, notes);
    }
}