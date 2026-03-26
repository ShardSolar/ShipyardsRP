package net.shard.seconddawnrp.dice.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;

/**
 * Sent when an officer clicks CONFIRM or DISPUTE on a submission in the Ops PADD.
 */
public record ReviewSubmissionC2SPacket(
        String submissionId,
        String action,       // "CONFIRM" or "DISPUTE"
        String linkedTaskId, // for CONFIRM — empty string if not linking a task
        String note          // for DISPUTE — empty string if no note
) implements CustomPayload {

    public static final Id<ReviewSubmissionC2SPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "review_submission"));

    public static final PacketCodec<RegistryByteBuf, ReviewSubmissionC2SPacket> CODEC =
            PacketCodec.of(
                    (v, buf) -> {
                        buf.writeString(v.submissionId());
                        buf.writeString(v.action());
                        buf.writeString(v.linkedTaskId());
                        buf.writeString(v.note());
                    },
                    buf -> new ReviewSubmissionC2SPacket(
                            buf.readString(), buf.readString(),
                            buf.readString(), buf.readString())
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    public static void handle(ReviewSubmissionC2SPacket packet, ServerPlayerEntity officer) {
        boolean ok = switch (packet.action()) {
            case "CONFIRM" -> SecondDawnRP.RP_PADD_SUBMISSION_SERVICE.confirm(
                    packet.submissionId(),
                    officer.getUuid(),
                    packet.linkedTaskId().isBlank() ? null : packet.linkedTaskId(),
                    officer);  // officer passed so archive PADD can be generated
            case "DISPUTE" -> SecondDawnRP.RP_PADD_SUBMISSION_SERVICE.dispute(
                    packet.submissionId(),
                    officer.getUuid(),
                    packet.note().isBlank() ? null : packet.note(),
                    officer);  // officer passed so archive PADD can be generated
            default -> false;
        };

        if (ok) {
            // Push refreshed submission list back to this officer
            ServerPlayNetworking.send(officer, PushSubmissionsS2CPacket.build(null));
        }
    }
}