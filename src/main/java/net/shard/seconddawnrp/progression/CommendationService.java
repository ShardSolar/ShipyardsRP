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
 * Phase X: now writes to ServiceRecordService for history tracking.
 */
public class CommendationService {

    private final PlayerProfileManager profileManager;
    private final OfficerProgressionConfig config;
    private MinecraftServer server;

    // Injected after construction — avoids circular dependency
    private ServiceRecordService serviceRecordService;

    public CommendationService(PlayerProfileManager profileManager,
                               OfficerProgressionConfig config) {
        this.profileManager = profileManager;
        this.config = config;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    public void setServiceRecordService(ServiceRecordService srs) {
        this.serviceRecordService = srs;
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
            return "Point amount must be between -" + cap + " and " + cap
                    + ", and cannot be 0.";
        }

        PlayerProfile target = profileManager.getLoadedProfile(targetUuid);
        if (target == null) {
            return "Target player is not online or profile is not loaded.";
        }

        target.addRankPoints(points);
        SecondDawnRP.PROFILE_MANAGER.markDirty(targetUuid);

        // Write service record entry
        if (serviceRecordService != null) {
            String divisionCtx = target.getDivision() != null
                    ? target.getDivision().name() : "";
            serviceRecordService.logCommendation(
                    targetUuid, divisionCtx, points, reason,
                    actor.getUuid().toString(), actor.getName().getString());
        }

        boolean isCommendation = points > 0;
        int absPoints = Math.abs(points);

        System.out.println("[SecondDawnRP] "
                + (isCommendation ? "COMMENDATION" : "DEMERIT") + ": "
                + actor.getName().getString() + " -> " + target.getDisplayName()
                + " | " + points + " pts | Reason: " + reason);

        // Notify target
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

        // Broadcast
        if (server != null) {
            Text broadcast = isCommendation
                    ? Text.literal("[Commendation] " + target.getDisplayName()
                            + " commended by " + actor.getName().getString()
                            + " for: " + reason + ".")
                    .formatted(Formatting.GOLD)
                    : Text.literal("[Demerit] " + target.getDisplayName()
                            + " received a demerit from " + actor.getName().getString()
                            + " for: " + reason + ".")
                    .formatted(Formatting.RED);
            server.getPlayerManager().getPlayerList()
                    .forEach(p -> p.sendMessage(broadcast, false));
        }

        return isCommendation
                ? "Commendation issued to " + target.getDisplayName()
                + " for " + absPoints + " points."
                : "Demerit issued to " + target.getDisplayName()
                + " for " + absPoints + " points.";
    }

    private ParsedCommendationInput parseInlinePoints(int fallbackPoints, String rawReason) {
        if (rawReason == null) return new ParsedCommendationInput(fallbackPoints, "");
        String trimmed = rawReason.trim();
        if (trimmed.isEmpty()) return new ParsedCommendationInput(fallbackPoints, "");

        int idx = 0;
        char first = trimmed.charAt(0);
        if (first != '+' && first != '-') return new ParsedCommendationInput(fallbackPoints, trimmed);
        idx++;
        while (idx < trimmed.length() && Character.isDigit(trimmed.charAt(idx))) idx++;
        if (idx == 1) return new ParsedCommendationInput(fallbackPoints, trimmed);

        String numberPart = trimmed.substring(0, idx);
        String remainder  = trimmed.substring(idx).trim();
        try {
            return new ParsedCommendationInput(Integer.parseInt(numberPart), remainder);
        } catch (NumberFormatException e) {
            return new ParsedCommendationInput(fallbackPoints, trimmed);
        }
    }

    private record ParsedCommendationInput(int points, String reason) {}
}