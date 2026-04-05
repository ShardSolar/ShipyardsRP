package net.shard.seconddawnrp.roster.data;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Data carried when the server opens the Roster screen on the client.
 * Phase X: now includes service record history per member (capped at 20 entries).
 */
public record RosterOpenData(
        String divisionName,
        List<RosterEntry> members,
        int viewerAuthority,
        Map<String, List<ServiceRecordEntryDto>> serviceRecords // playerUuidStr -> entries
) {
    public static final PacketCodec<RegistryByteBuf, RosterOpenData> PACKET_CODEC =
            PacketCodec.of(RosterOpenData::write, RosterOpenData::read);

    /** Backward-compatible constructor without service records. */
    public RosterOpenData(String divisionName, List<RosterEntry> members, int viewerAuthority) {
        this(divisionName, members, viewerAuthority, Map.of());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(divisionName);
        buf.writeInt(members.size());
        for (RosterEntry e : members) RosterEntry.CODEC.encode(buf, e);
        buf.writeInt(viewerAuthority);

        // Write service records map
        buf.writeInt(serviceRecords.size());
        for (Map.Entry<String, List<ServiceRecordEntryDto>> entry : serviceRecords.entrySet()) {
            buf.writeString(entry.getKey());
            List<ServiceRecordEntryDto> entries = entry.getValue();
            buf.writeInt(entries.size());
            for (ServiceRecordEntryDto dto : entries) {
                ServiceRecordEntryDto.CODEC.encode(buf, dto);
            }
        }
    }

    private static RosterOpenData read(RegistryByteBuf buf) {
        String division = buf.readString();
        int count = buf.readInt();
        List<RosterEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(RosterEntry.CODEC.decode(buf));
        int authority = buf.readInt();

        // Read service records map
        int mapSize = buf.readInt();
        Map<String, List<ServiceRecordEntryDto>> records = new HashMap<>(mapSize);
        for (int i = 0; i < mapSize; i++) {
            String uuid = buf.readString();
            int entryCount = buf.readInt();
            List<ServiceRecordEntryDto> dtos = new ArrayList<>(entryCount);
            for (int j = 0; j < entryCount; j++) {
                dtos.add(ServiceRecordEntryDto.CODEC.decode(buf));
            }
            records.put(uuid, dtos);
        }

        return new RosterOpenData(division, entries, authority, records);
    }

    /** Get service record entries for a specific player. */
    public List<ServiceRecordEntryDto> getRecordsFor(String playerUuidStr) {
        return serviceRecords.getOrDefault(playerUuidStr, List.of());
    }
}