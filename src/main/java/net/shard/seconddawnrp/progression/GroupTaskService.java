package net.shard.seconddawnrp.progression;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;

import java.util.*;

/**
 * Manages group tasks — tasks with participant capacity > 1.
 *
 * When a task is configured as a group task, all participants see it in their
 * PADD and contribute to shared progress. BREAK_BLOCK and COLLECT_ITEM
 * contributions from any group member count toward the shared total.
 * MANUAL_CONFIRM group tasks require the group leader to submit.
 * Point reward is distributed to all participants on completion.
 *
 * Group task state is session-only (in-memory). The underlying task still
 * persists in the SQL task system — this service tracks the group membership
 * overlay on top of it.
 *
 * Phase 5.5 baseline — full SQL persistence added in Phase 9.5 if needed.
 */
public class GroupTaskService {

    private final PlayerProfileManager profileManager;
    private MinecraftServer server;

    /**
     * taskId → GroupTaskSession
     */
    private final Map<String, GroupTaskSession> sessions = new HashMap<>();

    public GroupTaskService(PlayerProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── Session management ────────────────────────────────────────────────────

    /**
     * Creates a group task session for the given task.
     *
     * @param taskId       the task ID (must exist in the task pool)
     * @param leaderUuid   the player who initiated the group task
     * @param maxParticipants maximum group size (from task template groupCapacity)
     */
    public GroupTaskSession createSession(String taskId, UUID leaderUuid, int maxParticipants) {
        GroupTaskSession session = new GroupTaskSession(taskId, leaderUuid, maxParticipants);
        session.addParticipant(leaderUuid);
        sessions.put(taskId, session);
        return session;
    }

    public Optional<GroupTaskSession> getSession(String taskId) {
        return Optional.ofNullable(sessions.get(taskId));
    }

    public void removeSession(String taskId) {
        sessions.remove(taskId);
    }

    // ── Participant management ────────────────────────────────────────────────

    public String addParticipant(String taskId, UUID playerUuid) {
        GroupTaskSession session = sessions.get(taskId);
        if (session == null) return "No group task session found for: " + taskId;
        if (session.isFull()) return "Group is full (" + session.getMaxParticipants() + " max).";
        if (session.hasParticipant(playerUuid)) return "Already a participant.";

        session.addParticipant(playerUuid);
        notifyParticipants(session, playerUuid + " joined the group.");
        return "Added to group task: " + taskId;
    }

    public String removeParticipant(String taskId, UUID playerUuid) {
        GroupTaskSession session = sessions.get(taskId);
        if (session == null) return "No active group session for: " + taskId;
        if (!session.hasParticipant(playerUuid)) return "Player is not in this group.";

        session.removeParticipant(playerUuid);
        notifyParticipants(session, playerUuid + " left the group.");

        // If leader left, promote first remaining participant
        if (playerUuid.equals(session.getLeaderUuid()) && !session.getParticipants().isEmpty()) {
            UUID newLeader = session.getParticipants().iterator().next();
            session.setLeaderUuid(newLeader);
            notifyParticipants(session, newLeader + " is now the group leader.");
        }
        return "Removed from group task.";
    }

    // ── Progress ──────────────────────────────────────────────────────────────

    /**
     * Records a progress contribution from a participant.
     * Returns true if the contribution was accepted (player is in the group).
     */
    public boolean contributeProgress(String taskId, UUID contributorUuid, int amount) {
        GroupTaskSession session = sessions.get(taskId);
        if (session == null) return false;
        if (!session.hasParticipant(contributorUuid)) return false;
        session.addProgress(amount);
        return true;
    }

    public int getSharedProgress(String taskId) {
        GroupTaskSession session = sessions.get(taskId);
        return session != null ? session.getProgress() : 0;
    }

    // ── Reward distribution ───────────────────────────────────────────────────

    /**
     * Distributes task reward points equally to all participants.
     * Called by TaskService on task completion.
     */
    public void distributeReward(String taskId, int totalPoints) {
        GroupTaskSession session = sessions.get(taskId);
        if (session == null) return;

        Set<UUID> participants = session.getParticipants();
        if (participants.isEmpty()) return;

        int each = totalPoints / participants.size();
        if (each <= 0) each = 1;

        for (UUID uuid : participants) {
            PlayerProfile profile = profileManager.getLoadedProfile(uuid);
            if (profile == null) continue;
            profile.addRankPoints(each);
            SecondDawnRP.PROFILE_MANAGER.markDirty(uuid);

            ServerPlayerEntity player = server != null
                    ? server.getPlayerManager().getPlayer(uuid) : null;
            if (player != null) {
                int finalEach = each;
                player.sendMessage(Text.literal(
                                "[Group Task] +" + finalEach + " points for completing: " + taskId),
                        false);
            }
        }
        sessions.remove(taskId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void notifyParticipants(GroupTaskSession session, String message) {
        if (server == null) return;
        for (UUID uuid : session.getParticipants()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                player.sendMessage(Text.literal("[Group] " + message), false);
            }
        }
    }

    // ── Inner data class ──────────────────────────────────────────────────────

    public static class GroupTaskSession {
        private final String taskId;
        private UUID leaderUuid;
        private final int maxParticipants;
        private final Set<UUID> participants = new LinkedHashSet<>();
        private int progress = 0;

        public GroupTaskSession(String taskId, UUID leaderUuid, int maxParticipants) {
            this.taskId = taskId;
            this.leaderUuid = leaderUuid;
            this.maxParticipants = maxParticipants;
        }

        public String getTaskId()            { return taskId; }
        public UUID   getLeaderUuid()        { return leaderUuid; }
        public void   setLeaderUuid(UUID u)  { this.leaderUuid = u; }
        public int    getMaxParticipants()   { return maxParticipants; }
        public Set<UUID> getParticipants()   { return Collections.unmodifiableSet(participants); }
        public boolean hasParticipant(UUID u){ return participants.contains(u); }
        public boolean isFull()              { return participants.size() >= maxParticipants; }
        public void addParticipant(UUID u)   { participants.add(u); }
        public void removeParticipant(UUID u){ participants.remove(u); }
        public int  getProgress()            { return progress; }
        public void addProgress(int amt)     { this.progress += amt; }
    }
}