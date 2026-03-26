package net.shard.seconddawnrp.dice.service;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.dice.data.RpPaddSubmission;
import net.shard.seconddawnrp.dice.item.RpPaddItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages RP PADD submissions.
 *
 * <p>On CONFIRM or DISPUTE:
 * <ol>
 *   <li>Marks the submission resolved in the database.</li>
 *   <li>Notifies the submitting player with the outcome.</li>
 *   <li>Generates a physical archived RP PADD item in the officer's inventory
 *       containing the full log stamped with the outcome, officer name, and date.
 *       The officer can hand this back to the player, file it in a chest, etc.</li>
 * </ol>
 *
 * <p>Resolved submissions are automatically purged after 7 days via the
 * daily cleanup tick. PENDING submissions never expire automatically.
 */
public class RpPaddSubmissionService {

    private static final long RESOLVED_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000; // 7 days
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final RpPaddSubmission.Repository repository;
    private MinecraftServer server;

    public RpPaddSubmissionService(RpPaddSubmission.Repository repository) {
        this.repository = repository;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── Submit ────────────────────────────────────────────────────────────────

    public RpPaddSubmission submit(UUID submitterUuid, String submitterName,
                                   List<String> entries) {
        RpPaddSubmission sub = RpPaddSubmission.createNew(submitterUuid, submitterName, entries);
        repository.save(sub);

        if (server != null) {
            Text notify = Text.literal("[RP PADD] ")
                    .formatted(Formatting.GOLD)
                    .append(Text.literal(submitterName).formatted(Formatting.YELLOW))
                    .append(Text.literal(" submitted a PADD (" + entries.size()
                                    + " entries). Open your Ops PADD to review.")
                            .formatted(Formatting.GOLD));
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.hasPermissionLevel(2)) p.sendMessage(notify, false);
            }
        }
        return sub;
    }

    // ── Review ────────────────────────────────────────────────────────────────

    public boolean confirm(String submissionId, UUID reviewerUuid,
                           String linkedTaskId, ServerPlayerEntity officer) {
        return review(submissionId, reviewerUuid,
                RpPaddSubmission.Status.CONFIRMED, null, linkedTaskId, officer);
    }

    public boolean dispute(String submissionId, UUID reviewerUuid,
                           String note, ServerPlayerEntity officer) {
        return review(submissionId, reviewerUuid,
                RpPaddSubmission.Status.DISPUTED, note, null, officer);
    }

    private boolean review(String submissionId, UUID reviewerUuid,
                           RpPaddSubmission.Status status, String note,
                           String linkedTaskId, ServerPlayerEntity officer) {
        Optional<RpPaddSubmission> opt = repository.loadById(submissionId);
        if (opt.isEmpty()) return false;

        RpPaddSubmission sub = opt.get();
        sub.setStatus(status);
        sub.setReviewedByUuid(reviewerUuid);
        sub.setReviewedAtMs(System.currentTimeMillis());
        if (note != null) sub.setReviewNote(note);
        if (linkedTaskId != null && !linkedTaskId.isBlank()) sub.setLinkedTaskId(linkedTaskId);
        repository.save(sub);

        // Notify submitter
        notifySubmitter(sub, status, note);

        // Generate archive PADD in officer's inventory
        if (officer != null) generateArchivePadd(officer, sub, status, note);

        return true;
    }

    // ── Archive PADD generation ───────────────────────────────────────────────

    /**
     * Creates a signed, stamped RP PADD item in the officer's inventory.
     * Contains the full log plus a header showing outcome, officer, and date.
     */
    private void generateArchivePadd(ServerPlayerEntity officer,
                                     RpPaddSubmission sub,
                                     RpPaddSubmission.Status status,
                                     String note) {
        ItemStack archive = new ItemStack(SecondDawnRP.RP_PADD_ITEM);

        NbtCompound root = new NbtCompound();
        root.putBoolean(RpPaddItem.NBT_SIGNED, true);
        root.putString(RpPaddItem.NBT_SUBMITTER, sub.getSubmitterName());

        // Build the log with a header prepended
        NbtList logList = new NbtList();

        // Stamp header
        String dateStr = DATE_FMT.format(new Date(sub.getSubmittedAtMs()));
        String outcomeStr = status == RpPaddSubmission.Status.CONFIRMED ? "CONFIRMED" : "DISPUTED";
        logList.add(NbtString.of("── Archive Record ──────────────────"));
        logList.add(NbtString.of("Submitter : " + sub.getSubmitterName()));
        logList.add(NbtString.of("Reviewed by : " + officer.getGameProfile().getName()));
        logList.add(NbtString.of("Date : " + dateStr));
        logList.add(NbtString.of("Outcome : " + outcomeStr));
        if (note != null && !note.isBlank()) {
            logList.add(NbtString.of("Note : " + note));
        }
        logList.add(NbtString.of("─────────────────────────────────────"));

        // Original entries
        for (String entry : sub.getEntries()) {
            logList.add(NbtString.of(entry));
        }

        root.put(RpPaddItem.NBT_LOG, logList);
        root.putInt(RpPaddItem.NBT_COUNT, sub.getEntryCount());

        NbtCompound top = new NbtCompound();
        top.put(RpPaddItem.NBT_ROOT, root);
        archive.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(top));

        // Display name: outcome + submitter name
        archive.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Archive PADD [" + outcomeStr + " — " + sub.getSubmitterName() + "]")
                        .formatted(status == RpPaddSubmission.Status.CONFIRMED
                                ? Formatting.GREEN : Formatting.RED));

        // Give to officer — try inventory first, then drop at feet
        if (!officer.getInventory().insertStack(archive)) {
            officer.dropItem(archive, false);
            officer.sendMessage(Text.literal("[RP PADD] Archive PADD dropped — inventory full.")
                    .formatted(Formatting.YELLOW), false);
        } else {
            officer.sendMessage(Text.literal("[RP PADD] Archive PADD added to your inventory.")
                    .formatted(Formatting.GREEN), false);
        }
    }

    // ── Cleanup tick ──────────────────────────────────────────────────────────

    /**
     * Called once per day from the server tick. Removes resolved submissions
     * older than 7 days. PENDING submissions are never removed automatically.
     */
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - RESOLVED_EXPIRY_MS;
        repository.deleteResolvedBefore(cutoff);
    }

    // ── Notify ────────────────────────────────────────────────────────────────

    private void notifySubmitter(RpPaddSubmission sub, RpPaddSubmission.Status status, String note) {
        if (server == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(sub.getSubmitterUuid());
        if (player == null) return;

        Text msg = switch (status) {
            case CONFIRMED -> Text.literal("[RP PADD] Your submitted PADD has been ")
                    .formatted(Formatting.GREEN)
                    .append(Text.literal("CONFIRMED").formatted(Formatting.BOLD, Formatting.GREEN))
                    .append(Text.literal(" by an officer.").formatted(Formatting.GREEN));
            case DISPUTED  -> Text.literal("[RP PADD] Your submitted PADD has been ")
                    .formatted(Formatting.RED)
                    .append(Text.literal("DISPUTED").formatted(Formatting.BOLD, Formatting.RED))
                    .append(Text.literal(" by an officer"
                                    + (note != null && !note.isBlank() ? ": " + note : "."))
                            .formatted(Formatting.RED));
            default -> null;
        };
        if (msg != null) player.sendMessage(msg, false);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<RpPaddSubmission> getPending() { return repository.loadPending(); }
    public List<RpPaddSubmission> getAll()     { return repository.loadAll(); }
    public Optional<RpPaddSubmission> getById(String id) { return repository.loadById(id); }
}