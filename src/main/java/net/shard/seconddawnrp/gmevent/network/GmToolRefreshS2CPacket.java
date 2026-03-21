package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

import java.util.ArrayList;
import java.util.List;

public record GmToolRefreshS2CPacket(List<TemplateEntry> templates) implements CustomPayload {

    public static final CustomPayload.Id<GmToolRefreshS2CPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("gm_tool_refresh"));

    public static final PacketCodec<RegistryByteBuf, GmToolRefreshS2CPacket> CODEC =
            PacketCodec.of(GmToolRefreshS2CPacket::write, GmToolRefreshS2CPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }

    public record TemplateEntry(
            String id, String displayName, String mobTypeId,
            double maxHealth, double armor,
            int totalSpawnCount, int maxActiveAtOnce,
            int spawnRadiusBlocks, int spawnIntervalTicks,
            String spawnBehaviour, List<String> statusEffects
    ) {}

    private void write(RegistryByteBuf buf) {
        buf.writeInt(templates.size());
        for (TemplateEntry t : templates) {
            buf.writeString(t.id());
            buf.writeString(t.displayName());
            buf.writeString(t.mobTypeId());
            buf.writeDouble(t.maxHealth());
            buf.writeDouble(t.armor());
            buf.writeInt(t.totalSpawnCount());
            buf.writeInt(t.maxActiveAtOnce());
            buf.writeInt(t.spawnRadiusBlocks());
            buf.writeInt(t.spawnIntervalTicks());
            buf.writeString(t.spawnBehaviour());
            buf.writeInt(t.statusEffects().size());
            for (String e : t.statusEffects()) buf.writeString(e);
        }
    }

    private static GmToolRefreshS2CPacket read(RegistryByteBuf buf) {
        int count = buf.readInt();
        List<TemplateEntry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = buf.readString(), displayName = buf.readString(), mobTypeId = buf.readString();
            double maxHealth = buf.readDouble(), armor = buf.readDouble();
            int total = buf.readInt(), maxActive = buf.readInt(),
                    radius = buf.readInt(), interval = buf.readInt();
            String behaviour = buf.readString();
            int ec = buf.readInt();
            List<String> effects = new ArrayList<>();
            for (int j = 0; j < ec; j++) effects.add(buf.readString());
            entries.add(new TemplateEntry(id, displayName, mobTypeId, maxHealth, armor,
                    total, maxActive, radius, interval, behaviour, effects));
        }
        return new GmToolRefreshS2CPacket(entries);
    }
}