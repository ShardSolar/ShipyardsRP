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
 * Handles manual commendations and demerits issued by senior officers.
 *
 * Positive points = commendation
 * Negative points = demerit
 *
 * Supports inline signed values at the start of the reason:
 *   "+15 Excellent leadership"
 *   "-10 Late to duty"
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

        ParsedCommendationInput parsed = parseInlinePoints(points, reason);
        points = parsed.points();
        reason = parsed.reason();

        if (reason == null || reason.isBlank()) {
            return "A written reason is required for commendations or demerits.";
        }

        int cap = config.getMaxCommendationPoints();
        if (points == 0 || points < -cap || points > cap) {
            return "Point amount must be between -" + cap + " and " + cap + ", and cannot be 0.";
        }

        PlayerProfile target = profileManager.getLoadedProfile(targetUuid);
        if (target == null) {
            return "Target player is not online or profile is not loaded.";
        }

        target.addRankPoints(points);
        SecondDawnRP.PROFILE_MANAGER.markDirty(targetUuid);

        boolean isCommendation = points > 0;
        int absPoints = Math.abs(points);

        System.out.println("[SecondDawnRP] " + (isCommendation ? "COMMENDATION" : "DEMERIT") + ": "
                + actor.getName().getString() + " -> " + target.getDisplayName()
                + " | " + points + " pts | Reason: " + reason);

        ServerPlayerEntity targetPlayer = server != null
                ? server.getPlayerManager().getPlayer(targetUuid) : null;
        if (targetPlayer != null) {
            if (isCommendation) {
                targetPlayer.sendMessage(Text.literal(
                                "[Commendation] You have been commended by "
                                        + actor.getName().getString() + " for: " + reason
                                        + " (+" + absPoints + " points).")
                        .formatted(Formatting.GOLD), false);
            } else {
                targetPlayer.sendMessage(Text.literal(
                                "[Demerit] You have received a demerit from "
                                        + actor.getName().getString() + " for: " + reason
                                        + " (-" + absPoints + " points).")
                        .formatted(Formatting.RED), false);
            }
        }

        if (server != null) {
            Text broadcast;
            if (isCommendation) {
                broadcast = Text.literal(
                                "[Commendation] " + target.getDisplayName()
                                        + " has been commended by " + actor.getName().getString()
                                        + " for: " + reason + ".")
                        .formatted(Formatting.GOLD);
            } else {
                broadcast = Text.literal(
                                "[Demerit] " + target.getDisplayName()
                                        + " has received a demerit from " + actor.getName().getString()
                                        + " for: " + reason + ".")
                        .formatted(Formatting.RED);
            }

            server.getPlayerManager().getPlayerList()
                    .forEach(p -> p.sendMessage(broadcast, false));
        }

        if (isCommendation) {
            return "Commendation issued to " + target.getDisplayName()
                    + " for " + absPoints + " points.";
        } else {
            return "Demerit issued to " + target.getDisplayName()
                    + " for " + absPoints + " points.";
        }
    }

    private ParsedCommendationInput parseInlinePoints(int fallbackPoints, String rawReason) {
        if (rawReason == null) {
            return new ParsedCommendationInput(fallbackPoints, "");
        }

        String trimmed = rawReason.trim();
        if (trimmed.isEmpty()) {
            return new ParsedCommendationInput(fallbackPoints, "");
        }

        // Accept leading signed integer only:
        // +15 Great work
        // -10 Late to duty
        // +25
        int idx = 0;
        char first = trimmed.charAt(0);
        if (first != '+' && first != '-') {
            return new ParsedCommendationInput(fallbackPoints, trimmed);
        }

        idx++;
        while (idx < trimmed.length() && Character.isDigit(trimmed.charAt(idx))) {
            idx++;
        }

        // No digits after sign
        if (idx == 1) {
            return new ParsedCommendationInput(fallbackPoints, trimmed);
        }

        String numberPart = trimmed.substring(0, idx);
        String remainder = trimmed.substring(idx).trim();

        try {
            int parsedPoints = Integer.parseInt(numberPart);
            return new ParsedCommendationInput(parsedPoints, remainder);
        } catch (NumberFormatException e) {
            return new ParsedCommendationInput(fallbackPoints, trimmed);
        }
    }

    private record ParsedCommendationInput(int points, String reason) {}
}