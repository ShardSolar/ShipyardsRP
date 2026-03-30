package net.shard.seconddawnrp.medical;

import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight patient record sent to the Medical PADD client screen.
 * Contains everything needed to render both the patient list and detail panel.
 */
public record MedicalPadPatientData(
        String uuid,
        String characterName,
        String rankDisplay,
        boolean online,
        List<MedicalPadConditionData> conditions
) {
    public void encode(PacketByteBuf buf) {
        buf.writeString(uuid);
        buf.writeString(characterName);
        buf.writeString(rankDisplay);
        buf.writeBoolean(online);
        buf.writeInt(conditions.size());
        for (MedicalPadConditionData c : conditions) c.encode(buf);
    }

    public static MedicalPadPatientData decode(PacketByteBuf buf) {
        String uuid          = buf.readString();
        String characterName = buf.readString();
        String rankDisplay   = buf.readString();
        boolean online       = buf.readBoolean();
        int count            = buf.readInt();
        List<MedicalPadConditionData> conditions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) conditions.add(MedicalPadConditionData.decode(buf));
        return new MedicalPadPatientData(uuid, characterName, rankDisplay, online, conditions);
    }
}