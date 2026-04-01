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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CadetService {

    private final PlayerProfileManager profileManager;
    private final CadetRankConfig config;
    private MinecraftServer server;

    private final Map<UUID, Rank> pendingGraduations = new HashMap<>();

    public CadetService(PlayerProfileManager profileManager, CadetRankConfig config) {
        this.profileManager = profileManager;
        this.config = config;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public boolean enrol(UUID playerUuid, ServerPlayerEntity actorIfOnline) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return false;

        if (profile.getProgressionPath() == ProgressionPath.COMMISSIONED) {
            if (actorIfOnline != null) {
                actorIfOnline.sendMessage(Text.literal(
                        "[Cadet] Player is already on the commissioned track."), false);
            }
            return false;
        }

        profile.setProgressionPath(ProgressionPath.COMMISSIONED);
        profile.setRank(Rank.CADET_1);
        profile.setDivision(Division.UNASSIGNED);
        profileManager.markDirty(playerUuid);

        ServerPlayerEntity target = server != null
                ? server.getPlayerManager().getPlayer(playerUuid) : null;
        if (target != null) {
            SecondDawnRP.PROFILE_SERVICE.syncAll(target);
            target.sendMessage(Text.literal(
                            "[Cadet] You have been enrolled in the Cadet track. "
                                    + "You are now Cadet 1. Declare your division before your next promotion.")
                    .formatted(Formatting.AQUA), false);
        }

        System.out.println("[SecondDawnRP] Cadet enrolled: " + playerUuid);
        return true;
    }

    public String promote(ServerPlayerEntity promotingOfficer, UUID cadetUuid) {
        PlayerProfile profile = profileManager.getLoadedProfile(cadetUuid);
        if (profile == null) return "Player profile not loaded.";
        if (profile.getProgressionPath() != ProgressionPath.COMMISSIONED) {
            return "Player is not on the commissioned track.";
        }

        Rank current = profile.getRank();
        Rank next = nextCadetRank(current);
        if (next == null) return "Player is at CADET_4 — use /cadet graduate to proceed.";

        if (current == Rank.CADET_1 && profile.getDivision() == Division.UNASSIGNED) {
            return "Cadet must declare a division before promotion to Cadet 2.";
        }

        int required = config.getPointsRequired(current);
        if (profile.getRankPoints() < required) {
            return "Cadet needs " + required + " points. Current: " + profile.getRankPoints() + ".";
        }

        profile.setRank(next);
        profileManager.markDirty(cadetUuid);

        ServerPlayerEntity target = server != null
                ? server.getPlayerManager().getPlayer(cadetUuid) : null;
        if (target != null) {
            SecondDawnRP.PROFILE_SERVICE.syncAll(target);
            target.sendMessage(
                    Text.literal("[Cadet] You have been promoted to "
                                    + next.getId().replace("_", " ").toUpperCase() + ".")
                            .formatted(Formatting.GREEN), false);
        }

        String msg = "[Cadet] " + profile.getDisplayName() + " promoted to "
                + next.getId().replace("_", " ").toUpperCase()
                + " by " + promotingOfficer.getName().getString() + ".";

        broadcastAll(Text.literal(msg).formatted(Formatting.GOLD));
        return "Promoted " + profile.getDisplayName() + " to " + next.getId() + ".";
    }

    public String proposeGraduation(ServerPlayerEntity instructor,
                                    UUID cadetUuid, Rank startingRank) {
        PlayerProfile cadetProfile = profileManager.getLoadedProfile(cadetUuid);
        if (cadetProfile == null) return "Cadet profile not loaded.";
        if (cadetProfile.getProgressionPath() != ProgressionPath.COMMISSIONED) {
            return "Player is not on the commissioned track.";
        }
        if (cadetProfile.getRank() != Rank.CADET_4) {
            return "Player must be Cadet 4 before graduation.";
        }
        if (!isCommissionedRank(startingRank)) {
            return "Starting rank must be ENSIGN or above (not a cadet rank).";
        }

        int required = config.getPointsRequired(Rank.CADET_4);
        if (cadetProfile.getRankPoints() < required) {
            return "Cadet needs " + required + " points for graduation eligibility. "
                    + "Current: " + cadetProfile.getRankPoints() + ".";
        }

        long daysInTrack = (System.currentTimeMillis() - cadetProfile.getCharacterCreatedAt())
                / (1000L * 60 * 60 * 24);
        if (daysInTrack < config.getMinimumCadetDays()) {
            return "Cadet must spend at least " + config.getMinimumCadetDays()
                    + " days in the cadet track. Current: " + daysInTrack + " days.";
        }

        pendingGraduations.put(cadetUuid, startingRank);

        String notice = "[Cadet] Graduation proposed for " + cadetProfile.getDisplayName()
                + " -> " + startingRank.getId().replace("_", " ").toUpperCase()
                + " by " + instructor.getName().getString()
                + ". Approval required: /cadet approve " + cadetUuid;

        if (server != null) {
            server.getPlayerManager().getPlayerList().stream()
                    .filter(p -> {
                        PlayerProfile pp = profileManager.getLoadedProfile(p.getUuid());
                        return pp != null && SecondDawnRP.PERMISSION_SERVICE.canCadetApprove(p, pp);
                    })
                    .forEach(p -> p.sendMessage(
                            Text.literal(notice).formatted(Formatting.GOLD), false));
        }

        return "Graduation proposed. Awaiting approval.";
    }

    public String approveGraduation(ServerPlayerEntity approver, UUID cadetUuid) {
        PlayerProfile approverProfile = profileManager.getLoadedProfile(approver.getUuid());
        if (!SecondDawnRP.PERMISSION_SERVICE.canCadetApprove(approver, approverProfile)) {
            return "You do not have permission to approve graduations.";
        }

        Rank startingRank = pendingGraduations.remove(cadetUuid);
        if (startingRank == null) {
            return "No pending graduation proposal found for that player.";
        }

        PlayerProfile profile = profileManager.getLoadedProfile(cadetUuid);
        if (profile == null) return "Player profile not loaded.";

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
                + " -> " + startingRank.getId());
        return "Graduation approved. " + profile.getDisplayName()
                + " is now " + startingRank.getId() + ".";
    }

    public boolean hasPendingGraduation(UUID cadetUuid) {
        return pendingGraduations.containsKey(cadetUuid);
    }

    public Rank getPendingGraduationRank(UUID cadetUuid) {
        return pendingGraduations.get(cadetUuid);
    }

    private Rank nextCadetRank(Rank current) {
        return switch (current) {
            case CADET_1 -> Rank.CADET_2;
            case CADET_2 -> Rank.CADET_3;
            case CADET_3 -> Rank.CADET_4;
            default -> null;
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

    private void broadcastAll(Text message) {
        if (server != null) {
            server.getPlayerManager().getPlayerList()
                    .forEach(p -> p.sendMessage(message, false));
        }
    }
}