package net.shard.seconddawnrp.roster.data;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;

/**
 * Network-safe DTO for ServiceRecordEntry — sent from server to client
 * as part of RosterOpenData. Uses primitive types only for safe serialization.
 */
public record ServiceRecordEntryDto(
        String entryId,
        long timestamp,
        String type,       // ServiceRecordEntry.Type name
        int pointsDelta,
        String actorName,
        String reason,
        String divisionContext
) {
    public static final PacketCodec<PacketByteBuf, ServiceRecordEntryDto> CODEC =
            PacketCodec.of(ServiceRecordEntryDto::write, ServiceRecordEntryDto::read);

    static ServiceRecordEntryDto read(PacketByteBuf buf) {
        return new ServiceRecordEntryDto(
                buf.readString(),
                buf.readLong(),
                buf.readString(),
                buf.readInt(),
                buf.readString(),
                buf.readString(),
                buf.readString()
        );
    }

    void write(PacketByteBuf buf) {
        buf.writeString(entryId);
        buf.writeLong(timestamp);
        buf.writeString(type);
        buf.writeInt(pointsDelta);
        buf.writeString(actorName != null ? actorName : "Unknown");
        buf.writeString(reason != null ? reason : "");
        buf.writeString(divisionContext != null ? divisionContext : "");
    }

    /** Friendly label for the type. */
    public String typeLabel() {
        return switch (type) {
            case "COMMENDATION"    -> "Commendation";
            case "DEMERIT"         -> "Demerit";
            case "PROMOTION"       -> "Promotion";
            case "DEMOTION"        -> "Demotion";
            case "TRANSFER"        -> "Transfer";
            case "ENLISTMENT"      -> "Enlisted";
            case "CADET_ENROLLED"  -> "Cadet Enrolled";
            case "CADET_GRADUATED" -> "Cadet Graduated";
            case "DISMISSED"       -> "Dismissed";
            case "NOTE"            -> "Note";
            default                -> type;
        };
    }

    /** Color for this entry type. */
    public int typeColor() {
        return switch (type) {
            case "COMMENDATION"    -> 0xFFFFB24A;
            case "DEMERIT"         -> 0xFFFF6060;
            case "PROMOTION"       -> 0xFF38FF9A;
            case "DEMOTION"        -> 0xFFFF9944;
            case "CADET_GRADUATED" -> 0xFF88AAFF;
            case "DISMISSED"       -> 0xFFFF4444;
            default                -> 0xFFAAAAAA;
        };
    }

    /** Signed points string, e.g. "+15" or "-10". Empty if no points. */
    public String pointsLabel() {
        if (pointsDelta == 0) return "";
        return pointsDelta > 0 ? "+" + pointsDelta : String.valueOf(pointsDelta);
    }
}