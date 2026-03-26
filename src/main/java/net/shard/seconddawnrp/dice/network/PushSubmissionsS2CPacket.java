package net.shard.seconddawnrp.dice.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;

import java.util.ArrayList;
import java.util.List;

// ── S2C: push submission list to Ops PADD screen ──────────────────────────────

/**
 * Sent server → client to refresh the Submissions tab on the Ops PADD.
 * Contains lightweight view models for the list, plus the full log of the
 * selected submission (if any).
 */
public record PushSubmissionsS2CPacket(
        List<SubmissionEntry> submissions,
        String selectedId,
        List<String> selectedLog  // full log of selected submission
) implements CustomPayload {

    public static final Id<PushSubmissionsS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "push_submissions"));

    public record SubmissionEntry(
            String submissionId,
            String displayLabel,  // "CharName — N entries"
            String status,        // PENDING / CONFIRMED / DISPUTED
            String submittedAt    // formatted date string
    ) {}

    private static final PacketCodec<RegistryByteBuf, SubmissionEntry> ENTRY_CODEC =
            PacketCodec.of(
                    (v, buf) -> { buf.writeString(v.submissionId()); buf.writeString(v.displayLabel()); buf.writeString(v.status()); buf.writeString(v.submittedAt()); },
                    buf -> new SubmissionEntry(buf.readString(), buf.readString(), buf.readString(), buf.readString())
            );

    public static final PacketCodec<RegistryByteBuf, PushSubmissionsS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeInt(value.submissions().size());
                        for (SubmissionEntry e : value.submissions()) ENTRY_CODEC.encode(buf, e);
                        buf.writeString(value.selectedId() != null ? value.selectedId() : "");
                        buf.writeInt(value.selectedLog().size());
                        for (String line : value.selectedLog()) buf.writeString(line);
                    },
                    buf -> {
                        int count = buf.readInt();
                        List<SubmissionEntry> entries = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) entries.add(ENTRY_CODEC.decode(buf));
                        String selId = buf.readString();
                        int logCount = buf.readInt();
                        List<String> log = new ArrayList<>(logCount);
                        for (int i = 0; i < logCount; i++) log.add(buf.readString());
                        return new PushSubmissionsS2CPacket(entries, selId.isBlank() ? null : selId, log);
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    // Factory
    public static PushSubmissionsS2CPacket build(String selectedId) {
        var submissions = SecondDawnRP.RP_PADD_SUBMISSION_SERVICE.getPending();        List<SubmissionEntry> entries = submissions.stream().map(sub -> {
            String date = new java.text.SimpleDateFormat("MM/dd HH:mm")
                    .format(new java.util.Date(sub.getSubmittedAtMs()));
            return new SubmissionEntry(sub.getSubmissionId(), sub.getDisplayLabel(),
                    sub.getStatus().name(), date);
        }).toList();

        List<String> log = List.of();
        if (selectedId != null) {
            log = SecondDawnRP.RP_PADD_SUBMISSION_SERVICE.getById(selectedId)
                    .map(s -> s.getEntries())
                    .orElse(List.of());
        } else if (!submissions.isEmpty()) {
            log = submissions.get(0).getEntries();
            selectedId = submissions.get(0).getSubmissionId();
        }

        return new PushSubmissionsS2CPacket(entries, selectedId, log);
    }
}