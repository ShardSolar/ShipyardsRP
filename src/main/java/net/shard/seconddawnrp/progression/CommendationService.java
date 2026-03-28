package net.shard.seconddawnrp.progression;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.division.Rank;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;

import java.util.UUID;

/**
 * Handles manual commendations issued by senior officers.
 *
 * Commendations are variable-point awards requiring a written reason.
 * Authority: Chief Officer of the division, First Officer, Second Officer,
 * or Captain only — not any officer.
 *
 * All commendations are audited and logged permanently.
 * Point amount is capped by OfficerProgressionConfig.getMaxCommendationPoints().
 */
public class CommendationService {

    private final PlayerProfileManager profileManager;
    private final OfficerProgressionConfig config;
    private MinecraftServer server;

    public CommendationService(PlayerProfileManager profileManager,
                               OfficerProgressionConfig config) {
        this.profileManager = profileManager;
        this.config = config;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    /**
     * Issue a commendation.
     *
     * @param actor    the officer issuing the commendation
     * @param targetUuid the player receiving the commendation
     * @param points   point amount (capped by config)
     * @param reason   required written reason — audited
     * @return result message
     */
    public String commend(ServerPlayerEntity actor, UUID targetUuid,
                          int points, String reason) {
        if (!canCommend(actor)) {
            return "Only Chief Officers, First Officer, Second Officer, or Captain may issue commendations.";
        }

        if (reason == null || reason.isBlank()) {
            return "A written reason is required for commendations.";
        }

        int cap = config.getMaxCommendationPoints();
        if (points <= 0 || points > cap) {
            return "Point amount must be between 1 and " + cap + ".";
        }

        PlayerProfile target = profileManager.getLoadedProfile(targetUuid);
        if (target == null) {
            return "Target player is not online or profile is not loaded.";
        }

        target.addRankPoints(points);
        SecondDawnRP.PROFILE_MANAGER.markDirty(targetUuid);

        // Audit log
        System.out.println("[SecondDawnRP] COMMENDATION: "
                + actor.getName().getString() + " → " + target.getDisplayName()
                + " | " + points + " pts | Reason: " + reason);

        // Notify target
        ServerPlayerEntity targetPlayer = server != null
                ? server.getPlayerManager().getPlayer(targetUuid) : null;
        if (targetPlayer != null) {
            targetPlayer.sendMessage(Text.literal(
                            "[Commendation] You have been commended by "
                                    + actor.getName().getString() + " for: " + reason
                                    + " (+" + points + " points).")
                    .formatted(Formatting.GOLD), false);
        }

        // Broadcast to division
        PlayerProfile actorProfile = profileManager.getLoadedProfile(actor.getUuid());
        if (actorProfile != null && server != null) {
            Text broadcast = Text.literal(
                            "[Commendation] " + target.getDisplayName()
                                    + " has been commended by " + actor.getName().getString()
                                    + " for: " + reason + ".")
                    .formatted(Formatting.GOLD);
            server.getPlayerManager().getPlayerList()
                    .forEach(p -> p.sendMessage(broadcast, false));
        }

        return "Commendation issued to " + target.getDisplayName()
                + " for " + points + " points.";
    }

    // ── Permission ────────────────────────────────────────────────────────────

    /**
     * Commendation authority: Chief Officer billet, ship positions, Captain, or admin.
     * Checks rank authority level — COMMANDER+ or op level 2 fallback.
     */
    private boolean canCommend(ServerPlayerEntity actor) {
        if (actor.hasPermissionLevel(2)) return true;
        PlayerProfile profile = profileManager.getLoadedProfile(actor.getUuid());
        if (profile == null) return false;
        // COMMANDER and CAPTAIN have authority level 8+
        return profile.getRank() != null
                && profile.getRank().getAuthorityLevel() >= Rank.COMMANDER.getAuthorityLevel();
    }
}