package net.shard.seconddawnrp.roster.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.division.Rank;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;
import net.shard.seconddawnrp.playerdata.ProgressionPath;
import net.shard.seconddawnrp.roster.data.RosterEntry;
import net.shard.seconddawnrp.roster.data.RosterOpenData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RosterService {

    private final PlayerProfileManager profileManager;
    private MinecraftServer server;

    public RosterService(PlayerProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public RosterOpenData buildForViewer(ServerPlayerEntity viewer) {
        PlayerProfile viewerProfile = profileManager.getLoadedProfile(viewer.getUuid());
        boolean canViewAll = SecondDawnRP.PERMISSION_SERVICE.canViewAllDivisions(viewer);

        Division filterDiv = (viewerProfile == null || canViewAll)
                ? null
                : viewerProfile.getDivision();

        String divisionTitle = filterDiv == null ? "ALL DIVISIONS" : filterDiv.name();

        List<RosterEntry> entries = new ArrayList<>();
        for (PlayerProfile profile : profileManager.getLoadedProfiles()) {
            if (filterDiv != null && profile.getDivision() != filterDiv) continue;
            entries.add(toEntry(profile));
        }

        entries.sort((a, b) -> {
            if (a.isOnline() != b.isOnline()) return a.isOnline() ? -1 : 1;
            Rank ra = parseRank(a.rankId());
            Rank rb = parseRank(b.rankId());
            if (ra != null && rb != null) {
                return Integer.compare(rb.getAuthorityLevel(), ra.getAuthorityLevel());
            }
            return 0;
        });

        int authority = viewerProfile != null && viewerProfile.getRank() != null
                ? viewerProfile.getRank().getAuthorityLevel()
                : (canViewAll ? 99 : 0);
        if (canViewAll) authority = 99;

        return new RosterOpenData(divisionTitle, entries, authority);
    }

    private RosterEntry toEntry(PlayerProfile p) {
        boolean isOnline = server != null
                && server.getPlayerManager().getPlayer(p.getPlayerId()) != null;

        int pointsToNext = computePointsToNext(p);

        List<String> certNames = new ArrayList<>();
        p.getCertifications().forEach(c -> certNames.add(c.name()));
        p.getBillets().forEach(b -> certNames.add("[" + b.name() + "]"));

        return new RosterEntry(
                p.getPlayerId().toString(),
                p.getDisplayName(),
                p.getServiceName() != null ? p.getServiceName() : "",
                p.getRank() != null ? p.getRank().name() : "UNKNOWN",
                formatRank(p),
                p.getDivision() != null ? p.getDivision().name() : "UNASSIGNED",
                p.getProgressionPath() != null ? p.getProgressionPath().name() : "ENLISTED",
                p.getRankPoints(),
                p.getServiceRecord(),
                certNames,
                p.isMustang(),
                p.getShipPosition() != null ? p.getShipPosition().name() : "NONE",
                isOnline,
                pointsToNext,
                ""
        );
    }

    private String formatRank(PlayerProfile p) {
        if (p.getRank() == null) return "Unknown";
        String id = p.getRank().getId().replace("_", " ");
        String[] words = id.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private int computePointsToNext(PlayerProfile p) {
        return -1;
    }

    public String executeAction(ServerPlayerEntity actor, String action,
                                UUID targetUuid, String stringArg, int intArg) {
        PlayerProfile actorProfile = profileManager.getLoadedProfile(actor.getUuid());
        if (actorProfile == null) return "Your profile is not loaded.";

        PlayerProfile target = profileManager.getLoadedProfile(targetUuid);
        if (target == null) return "Target player is not online.";

        if (!canActOnTarget(actor, actorProfile, target)) {
            return "You do not have authority over that roster entry.";
        }

        return switch (action) {
            case "PROMOTE"        -> handlePromote(actor, actorProfile, target);
            case "DEMOTE"         -> handleDemote(actor, actorProfile, target);
            case "CADET_ENROL"    -> handleCadetEnrol(actor, actorProfile, target.getPlayerId());
            case "CADET_PROMOTE"  -> handleCadetPromote(actor, actorProfile, target.getPlayerId());
            case "CADET_GRADUATE" -> handleCadetGraduate(actor, actorProfile, target.getPlayerId(), stringArg);
            case "CADET_APPROVE"  -> handleCadetApprove(actor, actorProfile, target.getPlayerId());
            case "COMMEND"        -> handleCommend(actor, actorProfile, target.getPlayerId(), intArg, stringArg);
            case "TRANSFER"       -> handleTransfer(actor, actorProfile, target, stringArg);
            case "DISMISS"        -> handleDismiss(actor, actorProfile, target);
            default               -> "Unknown action: " + action;
        };
    }

    private String handlePromote(ServerPlayerEntity actor, PlayerProfile actorProfile,
                                 PlayerProfile target) {
        if (!SecondDawnRP.PERMISSION_SERVICE.canManageRosterRanks(actor, actorProfile)) {
            return "You do not have permission to promote members.";
        }

        Rank current = target.getRank();
        if (current == null) return "Target has no rank.";
        if (current == Rank.CAPTAIN) return "Captain cannot be promoted further.";
        if (isCadetRank(current)) return "Use Cadet Promote for players on the cadet track.";

        Rank next = nextRank(current);
        if (next == null) return "No next rank defined.";

        if (!SecondDawnRP.PERMISSION_SERVICE.canPromote(actorProfile, next, actor)) {
            return "You do not have permission to promote to that rank.";
        }

        if (next.isOfficerTrack() && !isCadetRank(next) && next != Rank.CAPTAIN) {
            if (!SecondDawnRP.OFFICER_SLOT_SERVICE.hasSlot(next)) {
                SecondDawnRP.OFFICER_SLOT_SERVICE.enqueue(target.getPlayerId(), next);
                return target.getDisplayName() + " is eligible for "
                        + formatRankId(next) + " but the rank is full. Added to queue.";
            }
        }

        target.setRank(next);
        SecondDawnRP.PROFILE_MANAGER.markDirty(target.getPlayerId());

        ServerPlayerEntity targetPlayer = server != null
                ? server.getPlayerManager().getPlayer(target.getPlayerId()) : null;
        if (targetPlayer != null) {
            SecondDawnRP.PROFILE_SERVICE.syncAll(targetPlayer);
            targetPlayer.sendMessage(
                    net.minecraft.text.Text.literal(
                                    "[Roster] You have been promoted to "
                                            + formatRankId(next) + " by "
                                            + actor.getName().getString() + ".")
                            .formatted(net.minecraft.util.Formatting.GREEN), false);
        }
        return "Promoted " + target.getDisplayName() + " to " + formatRankId(next) + ".";
    }

    private String handleDemote(ServerPlayerEntity actor, PlayerProfile actorProfile,
                                PlayerProfile target) {
        if (!SecondDawnRP.PERMISSION_SERVICE.canManageRosterRanks(actor, actorProfile)) {
            return "You do not have permission to demote members.";
        }

        Rank current = target.getRank();
        if (current == null || current == Rank.JUNIOR_CREWMAN) {
            return "Cannot demote below Junior Crewman.";
        }
        if (isCadetRank(current)) {
            return "Cadets cannot be demoted with this button. Use Cadet commands.";
        }

        Rank prev = previousRank(current);
        if (prev == null) return "No previous rank defined.";

        boolean crossingToEnlisted = current.isOfficerTrack() && !isCadetRank(current)
                && !prev.isOfficerTrack();

        target.setRank(prev);
        if (crossingToEnlisted) {
            target.setProgressionPath(ProgressionPath.ENLISTED);
        }
        SecondDawnRP.PROFILE_MANAGER.markDirty(target.getPlayerId());

        if (current.isOfficerTrack() && !isCadetRank(current)) {
            SecondDawnRP.OFFICER_SLOT_SERVICE.onSlotOpened(current);
        }

        ServerPlayerEntity targetPlayer = server != null
                ? server.getPlayerManager().getPlayer(target.getPlayerId()) : null;
        if (targetPlayer != null) {
            SecondDawnRP.PROFILE_SERVICE.syncAll(targetPlayer);
        }

        return "Demoted " + target.getDisplayName() + " to " + formatRankId(prev) + ".";
    }

    private String handleCadetEnrol(ServerPlayerEntity actor, PlayerProfile actorProfile, UUID targetUuid) {
        if (!SecondDawnRP.PERMISSION_SERVICE.canCadetEnrol(actor, actorProfile)) return "No permission.";
        boolean ok = SecondDawnRP.CADET_SERVICE.enrol(targetUuid, actor);
        return ok ? "Enrolled in cadet track." : "Enrolment failed — check console.";
    }

    private String handleCadetPromote(ServerPlayerEntity actor, PlayerProfile actorProfile, UUID targetUuid) {
        if (!SecondDawnRP.PERMISSION_SERVICE.canCadetPromote(actor, actorProfile)) return "No permission.";
        return SecondDawnRP.CADET_SERVICE.promote(actor, targetUuid);
    }

    private String handleCadetGraduate(ServerPlayerEntity actor, PlayerProfile actorProfile,
                                       UUID targetUuid, String startRankId) {
        if (!SecondDawnRP.PERMISSION_SERVICE.canCadetGraduate(actor, actorProfile)) return "No permission.";
        Rank startRank = parseRankById(startRankId);
        if (startRank == null) return "Unknown rank: " + startRankId;
        return SecondDawnRP.CADET_SERVICE.proposeGraduation(actor, targetUuid, startRank);
    }

    private String handleCadetApprove(ServerPlayerEntity actor, PlayerProfile actorProfile, UUID targetUuid) {
        if (!SecondDawnRP.PERMISSION_SERVICE.canCadetApprove(actor, actorProfile)) return "No permission.";
        return SecondDawnRP.CADET_SERVICE.approveGraduation(actor, targetUuid);
    }

    private String handleCommend(ServerPlayerEntity actor, PlayerProfile actorProfile,
                                 UUID targetUuid, int points, String reason) {
        if (!SecondDawnRP.PERMISSION_SERVICE.canIssueCommendation(actor, actorProfile)) {
            return "No permission.";
        }
        return SecondDawnRP.COMMENDATION_SERVICE.commend(actor, targetUuid, points, reason);
    }

    private String handleTransfer(ServerPlayerEntity actor, PlayerProfile actorProfile,
                                  PlayerProfile target, String divisionName) {
        if (!SecondDawnRP.PERMISSION_SERVICE.canManageRosterMembers(actor, actorProfile)) {
            return "No permission.";
        }

        Division newDiv;
        try {
            newDiv = Division.valueOf(divisionName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Unknown division: " + divisionName;
        }

        Division oldDiv = target.getDivision();
        target.setDivision(newDiv);
        SecondDawnRP.PROFILE_MANAGER.markDirty(target.getPlayerId());

        ServerPlayerEntity targetPlayer = server != null
                ? server.getPlayerManager().getPlayer(target.getPlayerId()) : null;
        if (targetPlayer != null) {
            SecondDawnRP.PROFILE_SERVICE.syncAll(targetPlayer);
        }

        return "Transferred " + target.getDisplayName()
                + " from " + oldDiv + " to " + newDiv + ".";
    }

    private String handleDismiss(ServerPlayerEntity actor, PlayerProfile actorProfile,
                                 PlayerProfile target) {
        if (!SecondDawnRP.PERMISSION_SERVICE.canManageRosterMembers(actor, actorProfile)) {
            return "No permission.";
        }

        String name = target.getDisplayName();
        target.setDivision(Division.UNASSIGNED);
        SecondDawnRP.PROFILE_MANAGER.markDirty(target.getPlayerId());

        ServerPlayerEntity targetPlayer = server != null
                ? server.getPlayerManager().getPlayer(target.getPlayerId()) : null;
        if (targetPlayer != null) {
            SecondDawnRP.PROFILE_SERVICE.syncAll(targetPlayer);
            targetPlayer.sendMessage(
                    net.minecraft.text.Text.literal(
                                    "[Roster] You have been dismissed from your division.")
                            .formatted(net.minecraft.util.Formatting.RED), false);
        }
        return "Dismissed " + name + " from their division.";
    }

    private boolean canActOnTarget(ServerPlayerEntity actor, PlayerProfile actorProfile, PlayerProfile target) {
        if (SecondDawnRP.PERMISSION_SERVICE.canViewAllDivisions(actor)) {
            return true;
        }
        if (actorProfile == null) return false;
        if (actorProfile.getDivision() == null || target.getDivision() == null) return false;
        return actorProfile.getDivision() == target.getDivision();
    }

    private boolean isCadetRank(Rank r) {
        return r == Rank.CADET_1 || r == Rank.CADET_2
                || r == Rank.CADET_3 || r == Rank.CADET_4;
    }

    private Rank nextRank(Rank current) {
        Rank[] values = Rank.values();
        for (Rank r : values) {
            if (r.getTrack() == current.getTrack()
                    && r.getAuthorityLevel() == current.getAuthorityLevel() + 1
                    && !isCadetRank(r)) {
                return r;
            }
        }
        return null;
    }

    private Rank previousRank(Rank current) {
        Rank[] values = Rank.values();
        for (Rank r : values) {
            if (r.getTrack() == current.getTrack()
                    && r.getAuthorityLevel() == current.getAuthorityLevel() - 1
                    && !isCadetRank(r)) {
                return r;
            }
        }
        if (current == Rank.ENSIGN) return Rank.CHIEF_PETTY_OFFICER;
        return null;
    }

    private Rank parseRank(String name) {
        try {
            return Rank.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }

    private Rank parseRankById(String id) {
        for (Rank r : Rank.values()) {
            if (r.getId().equals(id) || r.name().equalsIgnoreCase(id)) return r;
        }
        return null;
    }

    private String formatRankId(Rank r) {
        return r.getId().replace("_", " ");
    }
}