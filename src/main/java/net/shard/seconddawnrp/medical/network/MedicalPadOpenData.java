package net.shard.seconddawnrp.medical.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.shard.seconddawnrp.medical.MedicalPadPatientData;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of Medical PADD state sent from server to client on open and refresh.
 *
 * @param patients      all players who have visited a Medical terminal
 * @param isSurgeon     whether the viewer holds the SURGEON billet
 */
public record MedicalPadOpenData(
        List<MedicalPadPatientData> patients,
        boolean isSurgeon
) {
    public static final PacketCodec<PacketByteBuf, MedicalPadOpenData> CODEC =
            PacketCodec.of(MedicalPadOpenData::encode, MedicalPadOpenData::decode);

    public void encode(PacketByteBuf buf) {
        buf.writeBoolean(isSurgeon);
        buf.writeInt(patients.size());
        for (MedicalPadPatientData p : patients) p.encode(buf);
    }

    public static MedicalPadOpenData decode(PacketByteBuf buf) {
        boolean isSurgeon = buf.readBoolean();
        int count = buf.readInt();
        List<MedicalPadPatientData> patients = new ArrayList<>(count);
        for (int i = 0; i < count; i++) patients.add(MedicalPadPatientData.decode(buf));
        return new MedicalPadOpenData(patients, isSurgeon);
    }
}