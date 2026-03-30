package net.shard.seconddawnrp.medical;

import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-condition data sent to the Medical PADD client screen.
 */
public record MedicalPadConditionData(
        String conditionId,
        String displayName,
        String severityColour,   // e.g. "§c"
        String severityLabel,    // e.g. "CRITICAL"
        boolean requiresSurgery,
        boolean readyToResolve,
        List<MedicalPadStepData> steps
) {
    public void encode(PacketByteBuf buf) {
        buf.writeString(conditionId);
        buf.writeString(displayName);
        buf.writeString(severityColour);
        buf.writeString(severityLabel);
        buf.writeBoolean(requiresSurgery);
        buf.writeBoolean(readyToResolve);
        buf.writeInt(steps.size());
        for (MedicalPadStepData s : steps) s.encode(buf);
    }

    public static MedicalPadConditionData decode(PacketByteBuf buf) {
        String conditionId    = buf.readString();
        String displayName    = buf.readString();
        String severityColour = buf.readString();
        String severityLabel  = buf.readString();
        boolean requiresSurg  = buf.readBoolean();
        boolean readyToRes    = buf.readBoolean();
        int count             = buf.readInt();
        List<MedicalPadStepData> steps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) steps.add(MedicalPadStepData.decode(buf));
        return new MedicalPadConditionData(conditionId, displayName, severityColour,
                severityLabel, requiresSurg, readyToRes, steps);
    }
}