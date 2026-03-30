package net.shard.seconddawnrp.medical.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

// ── S2C: open Medical PADD screen ────────────────────────────────────────────

/**
 * Sent server → client to open (or refresh) the Medical PADD screen.
 */
public record OpenMedicalPadS2CPacket(MedicalPadOpenData data) implements CustomPayload {

    public static final CustomPayload.Id<OpenMedicalPadS2CPacket> ID =
            new CustomPayload.Id<>(net.shard.seconddawnrp.SecondDawnRP.id("open_medical_pad"));

    public static final PacketCodec<PacketByteBuf, OpenMedicalPadS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> value.data().encode(buf),
                    buf -> new OpenMedicalPadS2CPacket(MedicalPadOpenData.decode(buf))
            );

    @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}