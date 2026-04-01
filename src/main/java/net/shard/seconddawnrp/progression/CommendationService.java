package net.shard.seconddawnrp.progression;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;

import java.util.UUID;

/**
 * Handles manual commendations issued by senior officers.
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

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public String commend(ServerPlayerEntity actor, UUID targetUuid,
                          int points, String reason) {
        PlayerProfile actorProfile = profileManager.getLoadedProfile(actor.getUuid());
        if (!SecondDawnRP.PERMISSION_SERVICE.canIssueCommendation(actor, actorProfile)) {
            return "You do not have permission to issue commendations.";
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

        System.out.println("[SecondDawnRP] COMMENDATION: "
                + actor.getName().getString() + " -> " + target.getDisplayName()
                + " | " + points + " pts | Reason: " + reason);

        ServerPlayerEntity targetPlayer = server != null
                ? server.getPlayerManager().getPlayer(targetUuid) : null;
        if (targetPlayer != null) {
            targetPlayer.sendMessage(Text.literal(
                            "[Commendation] You have been commended by "
                                    + actor.getName().getString() + " for: " + reason
                                    + " (+" + points + " points).")
                    .formatted(Formatting.GOLD), false);
        }

        if (server != null) {
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
}