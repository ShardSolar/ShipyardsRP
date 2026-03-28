package net.shard.seconddawnrp.progression;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;
import net.shard.seconddawnrp.playerdata.ProgressionPath;

import java.util.UUID;

/**
 * Awards rank points to commissioned officers for administrative actions.
 *
 * Officers earn points through management work — reviewing PADDs, approving
 * tasks, confirming certifications — not through task completion.
 * All point values are driven by OfficerProgressionConfig.
 *
 * This service is called by other services at action completion time:
 *   - RpPaddSubmissionService calls awardReviewPaddSession()
 *   - TaskService calls awardApproveTask() when approving AWAITING_REVIEW tasks
 *   - (Future) CertConfirmationService calls awardConfirmCertGraduation()
 */
public class OfficerProgressionService {

    private final PlayerProfileManager profileManager;
    private final OfficerProgressionConfig config;
    private MinecraftServer server;

    public OfficerProgressionService(PlayerProfileManager profileManager,
                                     OfficerProgressionConfig config) {
        this.profileManager = profileManager;
        this.config = config;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── Action award methods ──────────────────────────────────────────────────

    /** Called when an officer reviews and confirms an RP PADD session log. */
    public void awardReviewPaddSession(UUID officerUuid) {
        award(officerUuid, config.getReviewPaddSession(), "RP PADD session review");
    }

    /** Called when an officer approves a task from AWAITING_REVIEW. */
    public void awardApproveTask(UUID officerUuid) {
        award(officerUuid, config.getApproveTask(), "task approval");
    }

    /** Called when an officer confirms a certification path task. */
    public void awardConfirmCertTask(UUID officerUuid) {
        award(officerUuid, config.getConfirmCertTask(), "certification task confirmation");
    }

    /** Called when an officer conducts and logs an approved counseling session. */
    public void awardConductCounseling(UUID officerUuid) {
        award(officerUuid, config.getConductCounseling(), "counseling session");
    }

    /** Called when a full certification graduation is confirmed. */
    public void awardConfirmCertGraduation(UUID officerUuid) {
        award(officerUuid, config.getConfirmCertGraduation(), "certification graduation confirmation");
    }

    // ── Core award logic ──────────────────────────────────────────────────────

    private void award(UUID officerUuid, int points, String reason) {
        if (points <= 0) return;

        PlayerProfile profile = profileManager.getLoadedProfile(officerUuid);
        if (profile == null) return;

        // Only award to commissioned officers — not enlisted, not cadets
        if (profile.getProgressionPath() != ProgressionPath.COMMISSIONED) return;
        if (profile.getRank() == null) return;
        if (profile.getRank().isEnlisted()) return;
        // Cadet ranks are OFFICER track but still in training — no admin action points
        if (isCadetRank(profile.getRank())) return;

        profile.addRankPoints(points);
        profileManager.markDirty(officerUuid);

        ServerPlayerEntity player = server != null
                ? server.getPlayerManager().getPlayer(officerUuid) : null;
        if (player != null) {
            player.sendMessage(Text.literal(
                    "§a[+] " + points + " points§7 for " + reason + "."), false);
        }
    }

    private boolean isCadetRank(net.shard.seconddawnrp.division.Rank rank) {
        return rank == net.shard.seconddawnrp.division.Rank.CADET_1
                || rank == net.shard.seconddawnrp.division.Rank.CADET_2
                || rank == net.shard.seconddawnrp.division.Rank.CADET_3
                || rank == net.shard.seconddawnrp.division.Rank.CADET_4;
    }
}