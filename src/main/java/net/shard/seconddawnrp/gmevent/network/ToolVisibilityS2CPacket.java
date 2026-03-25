package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent server → client when a GM equips or unequips a registration tool.
 * toolType drives particle selection on the client:
 *   "component"  → FLAME (amber)
 *   "env"        → SOUL_FIRE_FLAME (blue-green)
 *   "trigger"    → WITCH (yellow)
 *   "anomaly"    → END_ROD (white/purple)
 *   "warpcore"   → SOUL_FIRE_FLAME (cyan)
 *   ""           → clear
 */
public record ToolVisibilityS2CPacket(
        List<Long> blockPositions,
        int particleColour,
        String worldKey,
        String toolType
) implements CustomPayload {

    public static final Id<ToolVisibilityS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "tool_visibility"));

    public static final PacketCodec<RegistryByteBuf, ToolVisibilityS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeInt(value.blockPositions().size());
                        for (long pos : value.blockPositions()) buf.writeLong(pos);
                        buf.writeInt(value.particleColour());
                        buf.writeString(value.worldKey());
                        buf.writeString(value.toolType());
                    },
                    buf -> {
                        int count = buf.readInt();
                        List<Long> positions = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) positions.add(buf.readLong());
                        int colour   = buf.readInt();
                        String world = buf.readString();
                        String tool  = buf.readString();
                        return new ToolVisibilityS2CPacket(positions, colour, world, tool);
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    public static ToolVisibilityS2CPacket clear() {
        return new ToolVisibilityS2CPacket(List.of(), 0, "", "");
    }
}