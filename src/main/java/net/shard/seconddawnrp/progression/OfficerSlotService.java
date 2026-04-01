package net.shard.seconddawnrp.progression;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.division.Rank;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;

import java.util.List;
import java.util.UUID;

/**
 * Manages commissioned officer slot caps and the promotion queue.
 *
 * Each rank has a maximum active count. When full, eligible players are queued
 * ordered by service record then time at current rank. When a slot opens, the
 * top queued player is notified and an officer must formally promote them.
 *
 * The system surfaces queue state — it never promotes automatically.
 * Captain is always exactly 1 slot and is admin-designated only.
 */
public class OfficerSlotService {

    private final OfficerSlotConfig config;
    private final SqlSlotQueueRepository queueRepo;
    private final PlayerProfileManager profileManager;
    private MinecraftServer server;

    public OfficerSlotService(OfficerSlotConfig config,
                              SqlSlotQueueRepository queueRepo,
                              PlayerProfileManager profileManager) {
        this.config = config;
        this.queueRepo = queueRepo;
        this.profileManager = profileManager;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── Slot availability ─────────────────────────────────────────────────────

    /**
     * Returns true if there is at least one open slot at the target rank.
     * Counts only online + loaded profiles at that rank as "active".
     */
    public boolean hasSlot(Rank targetRank) {
        if (targetRank == Rank.CAPTAIN) {
            // Captain: occupied if any loaded profile holds the rank
            return profileManager.getLoadedProfiles().stream()
                    .noneMatch(p -> p.getRank() == Rank.CAPTAIN);
        }
        int cap = config.getSlots(targetRank);
        long active = profileManager.getLoadedProfiles().stream()
                .filter(p -> p.getRank() == targetRank)
                .count();
        return active < cap;
    }

    /**
     * Count of active players at the given rank (loaded profiles only).
     */
    public int countActive(Rank rank) {
        return (int) profileManager.getLoadedProfiles().stream()
                .filter(p -> p.getRank() == rank)
                .count();
    }

    // ── Queue management ──────────────────────────────────────────────────────

    /**
     * Adds a player to the promotion queue for a target rank.
     * Called when a player is eligible but the rank is full.
     */
    public void enqueue(UUID playerUuid, Rank targetRank) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return;
        if (queueRepo.isQueued(playerUuid, targetRank)) return; // already queued

        long timeAtRank = System.currentTimeMillis(); // used as proxy — refined if rank-change timestamp added later
        SlotQueueEntry entry = new SlotQueueEntry(
                playerUuid, targetRank,
                profile.getServiceRecord(),
                System.currentTimeMillis(),
                timeAtRank
        );
        queueRepo.add(entry);

        ServerPlayerEntity player = server != null
                ? server.getPlayerManager().getPlayer(playerUuid) : null;
        if (player != null) {
            player.sendMessage(Text.literal(
                            "[Promotion] You are eligible for "
                                    + targetRank.getId().replace("_", " ").toUpperCase()
                                    + " but the rank is currently full. "
                                    + "You have been added to the promotion queue.")
                    .formatted(Formatting.YELLOW), false);
        }
    }

    /**
     * Removes a player from the queue (called after promotion completes or player is disqualified).
     */
    public void dequeue(UUID playerUuid, Rank targetRank) {
        queueRepo.remove(playerUuid, targetRank);
    }

    /**
     * Called when a slot opens at a rank (player leaves, is demoted, or a cap is raised).
     * Notifies the top queued player that a slot is available.
     */
    public void onSlotOpened(Rank rank) {
        List<SlotQueueEntry> queue = queueRepo.getQueue(rank);
        if (queue.isEmpty()) return;

        SlotQueueEntry next = queue.get(0);
        ServerPlayerEntity player = server != null
                ? server.getPlayerManager().getPlayer(next.getPlayerUuid()) : null;

        String msg = "[Promotion] A slot has opened at "
                + rank.getId().replace("_", " ").toUpperCase()
                + ". The next eligible player is: "
                + (player != null ? player.getName().getString() : next.getPlayerUuid().toString())
                + ". An officer must formally promote them via the Roster GUI.";

        // Notify all online officers of LT_COMMANDER+ rank
        if (server != null) {
            server.getPlayerManager().getPlayerList().stream()
                    .filter(p -> {
                        PlayerProfile pp = profileManager.getLoadedProfile(p.getUuid());
                        return pp != null
                                && pp.getRank() != null
                                && pp.getRank().getAuthorityLevel() >= Rank.LIEUTENANT_COMMANDER.getAuthorityLevel();
                    })
                    .forEach(p -> p.sendMessage(
                            Text.literal(msg).formatted(Formatting.GOLD), false));
        }

        if (player != null) {
            player.sendMessage(Text.literal(
                            "[Promotion] A slot has opened for your rank. "
                                    + "Contact your Chief Officer or First Officer to proceed.")
                    .formatted(Formatting.GREEN), false);
        }

        server.getPlayerManager().getPlayerList().stream()
                .filter(p -> {
                    PlayerProfile pp = profileManager.getLoadedProfile(p.getUuid());
                    return pp != null && SecondDawnRP.PERMISSION_SERVICE.canManageOfficerSlots(p, pp);
                })
                .forEach(p -> p.sendMessage(
                        Text.literal(msg).formatted(Formatting.GOLD), false));
    }

    // ── Admin slot management ─────────────────────────────────────────────────

    public void setSlots(Rank rank, int count, ServerPlayerEntity actor) {
        if (rank == Rank.CAPTAIN) {
            actor.sendMessage(Text.literal(
                    "[Slots] Captain slot count is fixed at 1 and cannot be changed."), false);
            return;
        }
        int previous = config.getSlots(rank);
        config.setSlots(rank, count);
        actor.sendMessage(Text.literal(
                "[Slots] " + rank.getId() + " slots: " + previous + " → " + count), false);

        // If slots increased, check if queue has entries to notify
        if (count > previous) {
            onSlotOpened(rank);
        }
    }

    public int getSlots(Rank rank) {
        return config.getSlots(rank);
    }

    public List<SlotQueueEntry> getQueue(Rank rank) {
        return queueRepo.getQueue(rank);
    }
}