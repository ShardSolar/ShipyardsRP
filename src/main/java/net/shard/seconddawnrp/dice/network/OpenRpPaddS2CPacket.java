package net.shard.seconddawnrp.dice.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent server → client when a player right-clicks their RP PADD.
 * Carries current recording state and the live log so the GUI can display it.
 */
public record OpenRpPaddS2CPacket(
        boolean isRecording,
        int entryCount,
        boolean isSigned,
        List<String> recentEntries   // last 20 entries for display
) implements CustomPayload {

    public static final Id<OpenRpPaddS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "open_rp_padd"));

    public static final PacketCodec<RegistryByteBuf, OpenRpPaddS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeBoolean(value.isRecording());
                        buf.writeInt(value.entryCount());
                        buf.writeBoolean(value.isSigned());
                        buf.writeInt(value.recentEntries().size());
                        for (String entry : value.recentEntries()) buf.writeString(entry);
                    },
                    buf -> {
                        boolean rec    = buf.readBoolean();
                        int count      = buf.readInt();
                        boolean signed = buf.readBoolean();
                        int size       = buf.readInt();
                        List<String> entries = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) entries.add(buf.readString());
                        return new OpenRpPaddS2CPacket(rec, count, signed, entries);
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}