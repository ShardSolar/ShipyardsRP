package net.shard.seconddawnrp.progression;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.division.Rank;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;
import net.shard.seconddawnrp.playerdata.ProgressionPath;

import java.util.UUID;

/**
 * Manages the Cadet rank track — the entry path for commissioned officers.
 *
 * Cadets start at CADET_1, below all enlisted. The track exists outside the
 * standard rank table. Division is declared before CADET_2 promotion.
 *
 * Promotions are always officer-approved. This service checks eligibility and
 * executes the promotion — it never promotes automatically.
 *
 * Graduation is a two-step process:
 *   1. Instructor proposes a starting rank via /cadet graduate
 *   2. Captain (or admin) approves before the rank is applied
 *
 * Pending graduation proposals are stored as a session-only map since they
 * expire on server restart by design — GMs must repropose after restarts.
 */
public class CadetService {

    private final PlayerProfileManager profileManager;
    private final CadetRankConfig config;
    private MinecraftServer server;

    /**
     * Session-only pending graduations: cadet UUID → proposed starting Rank.
     * Cleared on server restart by design.
     */
    private final java.util.Map<UUID, Rank> pendingGraduations = new java.util.HashMap<>();

    public CadetService(PlayerProfileManager profileManager, CadetRankConfig config) {
        this.profileManager = profileManager;
        this.config = config;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── Enrolment ─────────────────────────────────────────────────────────────

    /**
     * Enrol a player in the Cadet track. Sets rank to CADET_1 and path to COMMISSIONED.
     * Called from the Path Declaration Terminal or by admin command.
     *
     * @return false if the player is already on the officer track or slots are full
     */
    public boolean enrol(UUID playerUuid, ServerPlayerEntity actorIfOnline) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return false;
        if (profile.getProgressionPath() == ProgressionPath.COMMISSIONED) {
            if (actorIfOnline != null)
                actorIfOnline.sendMessage(Text.literal(
                        "[Cadet] Player is already on the commissioned track."), false);
            return false;
        }

        // Check Ensign slot availability — cadets occupy a slot on graduation
        // We don't reserve a slot at enrolment, only at graduation.

        profile.setProgressionPath(ProgressionPath.COMMISSIONED);
        profile.setRank(Rank.CADET_1);
        profile.setDivision(Division.UNASSIGNED); // Division declared at CADET_2
        profileManager.markDirty(playerUuid);

        ServerPlayerEntity target = server != null
                ? server.getPlayerManager().getPlayer(playerUuid) : null;
        if (target != null) {
            target.sendMessage(Text.literal(
                            "[Cadet] You have been enrolled in the Cadet track. "
                                    + "You are now Cadet 1. Declare your division before your next promotion.")
                    .formatted(Formatting.AQUA), false);
        }
        System.out.println("[SecondDawnRP] Cadet enrolled: " + playerUuid);
        return true;
    }

    // ── Promotion ─────────────────────────────────────────────────────────────

    /**
     * Promotes a cadet one step up the cadet ladder (CADET_1 → CADET_2, etc.).
     * Requires officer approval — this method IS the approval step.
     *
     * @param promotingOfficer the officer executing the promotion
     * @param cadetUuid        the cadet being promoted
     * @return result message to send back to the promoting officer
     */
    public String promote(ServerPlayerEntity promotingOfficer, UUID cadetUuid) {
        PlayerProfile profile = profileManager.getLoadedProfile(cadetUuid);
        if (profile == null) return "Player profile not loaded.";
        if (profile.getProgressionPath() != ProgressionPath.COMMISSIONED)
            return "Player is not on the commissioned track.";

        Rank current = profile.getRank();
        Rank next = nextCadetRank(current);
        if (next == null) return "Player is at CADET_4 — use /cadet graduate to proceed.";

        // CADET_1 → CADET_2 requires division declared
        if (current == Rank.CADET_1 && profile.getDivision() == Division.UNASSIGNED)
            return "Cadet must declare a division before promotion to Cadet 2.";

        // Point gate
        int required = config.getPointsRequired(current);
        if (profile.getRankPoints() < required)
            return "Cadet needs " + required + " points. Current: " + profile.getRankPoints() + ".";

        profile.setRank(next);
        profileManager.markDirty(cadetUuid);
        SecondDawnRP.PROFILE_SERVICE.syncAll(
                server != null ? server.getPlayerManager().getPlayer(cadetUuid) : null);

        String msg = "[Cadet] " + profile.getDisplayName() + " promoted to "
                + next.getId().replace("_", " ").toUpperCase()
                + " by " + promotingOfficer.getName().getString() + ".";

        broadcastAll(Text.literal(msg).formatted(Formatting.GOLD));

        ServerPlayerEntity target = server != null
                ? server.getPlayerManager().getPlayer(cadetUuid) : null;
        if (target != null) {
            target.sendMessage(
                    Text.literal("[Cadet] You have been promoted to "
                                    + next.getId().replace("_", " ").toUpperCase() + ".")
                            .formatted(Formatting.GREEN), false);
        }
        return "Promoted " + profile.getDisplayName() + " to " + next.getId() + ".";
    }

    // ── Graduation ────────────────────────────────────────────────────────────

    /**
     * Step 1: Instructor proposes a starting rank for graduation.
     * Stores the proposal session-only and pings all online Captains for approval.
     *
     * @param instructor   the instructor proposing
     * @param cadetUuid    the cadet graduating
     * @param startingRank the proposed commissioned starting rank (must be ENSIGN or above)
     */
    public String proposeGraduation(ServerPlayerEntity instructor,
                                    UUID cadetUuid, Rank startingRank) {
        PlayerProfile cadetProfile = profileManager.getLoadedProfile(cadetUuid);
        if (cadetProfile == null) return "Cadet profile not loaded.";
        if (cadetProfile.getProgressionPath() != ProgressionPath.COMMISSIONED)
            return "Player is not on the commissioned track.";
        if (cadetProfile.getRank() != Rank.CADET_4)
            return "Player must be Cadet 4 before graduation.";
        if (!isCommissionedRank(startingRank))
            return "Starting rank must be ENSIGN or above (not a cadet rank).";

        int required = config.getPointsRequired(Rank.CADET_4);
        if (cadetProfile.getRankPoints() < required)
            return "Cadet needs " + required + " points for graduation eligibility. "
                    + "Current: " + cadetProfile.getRankPoints() + ".";

        // Check minimum days in cadet track
        long daysInTrack = (System.currentTimeMillis() - cadetProfile.getCharacterCreatedAt())
                / (1000L * 60 * 60 * 24);
        if (daysInTrack < config.getMinimumCadetDays())
            return "Cadet must spend at least " + config.getMinimumCadetDays()
                    + " days in the cadet track. Current: " + daysInTrack + " days.";

        pendingGraduations.put(cadetUuid, startingRank);

        String notice = "[Cadet] Graduation proposed for " + cadetProfile.getDisplayName()
                + " → " + startingRank.getId().replace("_", " ").toUpperCase()
                + " by " + instructor.getName().getString()
                + ". Captain approval required: /cadet approve " + cadetUuid;

        // Notify all online players with Captain rank
        if (server != null) {
            server.getPlayerManager().getPlayerList().stream()
                    .filter(p -> {
                        PlayerProfile pp = profileManager.getLoadedProfile(p.getUuid());
                        return pp != null && pp.getRank() == Rank.CAPTAIN;
                    })
                    .forEach(p -> p.sendMessage(
                            Text.literal(notice).formatted(Formatting.GOLD), false));
        }
        return "Graduation proposed. Awaiting Captain approval.";
    }

    /**
     * Step 2: Captain (or admin, op level 4) approves the graduation.
     * Applies the proposed starting rank, converts cadet points, broadcasts.
     */
    public String approveGraduation(ServerPlayerEntity approver, UUID cadetUuid) {
        if (!canApproveGraduation(approver))
            return "Only the Captain or an admin can approve graduations.";

        Rank startingRank = pendingGraduations.remove(cadetUuid);
        if (startingRank == null)
            return "No pending graduation proposal found for that player. "
                    + "The instructor must re-run /cadet graduate first.";

        PlayerProfile profile = profileManager.getLoadedProfile(cadetUuid);
        if (profile == null) return "Player profile not loaded.";

        // Convert cadet points to commissioned service record
        int cadetPoints = profile.getRankPoints();
        int converted = (int) (cadetPoints * config.getCadetPointsConversionRate());

        profile.setRank(startingRank);
        profile.setProgressionPath(ProgressionPath.COMMISSIONED);
        profile.setRankPoints(converted);
        profile.setServiceRecord(profile.getServiceRecord() + converted);
        profileManager.markDirty(cadetUuid);

        ServerPlayerEntity target = server != null
                ? server.getPlayerManager().getPlayer(cadetUuid) : null;
        if (target != null) {
            SecondDawnRP.PROFILE_SERVICE.syncAll(target);
            target.sendMessage(
                    Text.literal("[Cadet] Congratulations! You have graduated as "
                                    + startingRank.getId().replace("_", " ").toUpperCase()
                                    + ". Welcome to the crew, officer.")
                            .formatted(Formatting.GREEN), false);
        }

        String announcement = "[Graduation] " + profile.getDisplayName()
                + " has graduated from the Cadet track as "
                + startingRank.getId().replace("_", " ").toUpperCase()
                + ". Approved by " + approver.getName().getString() + ".";
        broadcastAll(Text.literal(announcement).formatted(Formatting.GOLD));

        System.out.println("[SecondDawnRP] Cadet graduated: " + cadetUuid
                + " → " + startingRank.getId());
        return "Graduation approved. " + profile.getDisplayName()
                + " is now " + startingRank.getId() + ".";
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean hasPendingGraduation(UUID cadetUuid) {
        return pendingGraduations.containsKey(cadetUuid);
    }

    public Rank getPendingGraduationRank(UUID cadetUuid) {
        return pendingGraduations.get(cadetUuid);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Rank nextCadetRank(Rank current) {
        return switch (current) {
            case CADET_1 -> Rank.CADET_2;
            case CADET_2 -> Rank.CADET_3;
            case CADET_3 -> Rank.CADET_4;
            default      -> null;
        };
    }

    private boolean isCommissionedRank(Rank rank) {
        return rank == Rank.ENSIGN
                || rank == Rank.LIEUTENANT_JG
                || rank == Rank.LIEUTENANT
                || rank == Rank.LIEUTENANT_COMMANDER
                || rank == Rank.COMMANDER
                || rank == Rank.CAPTAIN;
    }

    private boolean canApproveGraduation(ServerPlayerEntity player) {
        if (player.hasPermissionLevel(4)) return true; // admin override
        PlayerProfile profile = profileManager.getLoadedProfile(player.getUuid());
        return profile != null && profile.getRank() == Rank.CAPTAIN;
    }

    private void broadcastAll(Text message) {
        if (server != null)
            server.getPlayerManager().getPlayerList()
                    .forEach(p -> p.sendMessage(message, false));
    }
}