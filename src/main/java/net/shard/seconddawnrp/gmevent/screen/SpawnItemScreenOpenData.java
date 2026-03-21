package net.shard.seconddawnrp.gmevent.screen;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.shard.seconddawnrp.gmevent.network.GmToolRefreshS2CPacket;

import java.util.ArrayList;
import java.util.List;

public record SpawnItemScreenOpenData(
        List<GmToolRefreshS2CPacket.TemplateEntry> templates,
        String currentTemplateId,
        int targetX, int targetY, int targetZ,
        String worldKey
) {
    public static final PacketCodec<RegistryByteBuf, SpawnItemScreenOpenData> PACKET_CODEC =
            PacketCodec.of(SpawnItemScreenOpenData::write, SpawnItemScreenOpenData::read);

    private void write(RegistryByteBuf buf) {
        buf.writeInt(templates.size());
        for (var t : templates) {
            buf.writeString(t.id()); buf.writeString(t.displayName());
            buf.writeString(t.mobTypeId());
            buf.writeDouble(t.maxHealth()); buf.writeDouble(t.armor());
            buf.writeInt(t.totalSpawnCount()); buf.writeInt(t.maxActiveAtOnce());
            buf.writeInt(t.spawnRadiusBlocks()); buf.writeInt(t.spawnIntervalTicks());
            buf.writeString(t.spawnBehaviour());
            buf.writeInt(t.statusEffects().size());
            for (String e : t.statusEffects()) buf.writeString(e);
        }
        buf.writeString(currentTemplateId != null ? currentTemplateId : "");
        buf.writeInt(targetX); buf.writeInt(targetY); buf.writeInt(targetZ);
        buf.writeString(worldKey);
    }

    private static SpawnItemScreenOpenData read(RegistryByteBuf buf) {
        int count = buf.readInt();
        List<GmToolRefreshS2CPacket.TemplateEntry> templates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = buf.readString(), dn = buf.readString(), mob = buf.readString();
            double hp = buf.readDouble(), armor = buf.readDouble();
            int total = buf.readInt(), max = buf.readInt(), r = buf.readInt(), iv = buf.readInt();
            String beh = buf.readString();
            int ec = buf.readInt();
            List<String> fx = new ArrayList<>();
            for (int j = 0; j < ec; j++) fx.add(buf.readString());
            templates.add(new GmToolRefreshS2CPacket.TemplateEntry(
                    id, dn, mob, hp, armor, total, max, r, iv, beh, fx));
        }
        String tid = buf.readString();
        int tx = buf.readInt(), ty = buf.readInt(), tz = buf.readInt();
        String wk = buf.readString();
        return new SpawnItemScreenOpenData(templates, tid.isBlank() ? null : tid, tx, ty, tz, wk);
    }
}