package net.shard.seconddawnrp.medical.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

/**
 * Sent client → server when the officer presses "Mark Resolved" on the Medical PADD.
 *
 * @param action      one of: "resolve"
 * @param conditionId target condition ID
 * @param stringArg   resolution note
 */
public record MedicalPadActionC2SPacket(
        String action,
        String conditionId,
        String stringArg
) implements CustomPayload {

    public static final Id<MedicalPadActionC2SPacket> ID =
            new Id<>(SecondDawnRP.id("medical_pad_action"));

    public static final PacketCodec<PacketByteBuf, MedicalPadActionC2SPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeString(value.action());
                        buf.writeString(value.conditionId());
                        buf.writeString(value.stringArg() != null ? value.stringArg() : "");
                    },
                    buf -> new MedicalPadActionC2SPacket(
                            buf.readString(),
                            buf.readString(),
                            buf.readString()
                    )
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}