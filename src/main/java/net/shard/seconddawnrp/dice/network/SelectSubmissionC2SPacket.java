package net.shard.seconddawnrp.dice.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.dice.data.RpPaddSubmission;

import java.util.List;

/**
 * Sent when the officer clicks the PADS tab, selects a submission, or
 * toggles the show-resolved filter.
 *
 * showResolved=false → server sends only PENDING (default)
 * showResolved=true  → server sends all including CONFIRMED/DISPUTED
 */
public record SelectSubmissionC2SPacket(
        String submissionId,
        boolean showResolved
) implements CustomPayload {

    public static final Id<SelectSubmissionC2SPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "select_submission"));

    public static final PacketCodec<RegistryByteBuf, SelectSubmissionC2SPacket> CODEC =
            PacketCodec.of(
                    (v, buf) -> { buf.writeString(v.submissionId()); buf.writeBoolean(v.showResolved()); },
                    buf -> new SelectSubmissionC2SPacket(buf.readString(), buf.readBoolean())
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    public static void handle(SelectSubmissionC2SPacket packet, ServerPlayerEntity player) {
        String id = packet.submissionId().isBlank() ? null : packet.submissionId();

        var submissions = packet.showResolved()
                ? SecondDawnRP.RP_PADD_SUBMISSION_SERVICE.getAll()
                : SecondDawnRP.RP_PADD_SUBMISSION_SERVICE.getPending();

        List<PushSubmissionsS2CPacket.SubmissionEntry> entries = submissions.stream().map(sub -> {
            String date = new java.text.SimpleDateFormat("MM/dd HH:mm")
                    .format(new java.util.Date(sub.getSubmittedAtMs()));
            return new PushSubmissionsS2CPacket.SubmissionEntry(
                    sub.getSubmissionId(), sub.getDisplayLabel(),
                    sub.getStatus().name(), date);
        }).toList();

        List<String> log = List.of();
        if (id != null) {
            log = SecondDawnRP.RP_PADD_SUBMISSION_SERVICE.getById(id)
                    .map(RpPaddSubmission::getEntries).orElse(List.of());
        } else if (!submissions.isEmpty()) {
            id = submissions.get(0).getSubmissionId();
            log = submissions.get(0).getEntries();
        }

        ServerPlayNetworking.send(player, new PushSubmissionsS2CPacket(entries, id, log));
    }
}