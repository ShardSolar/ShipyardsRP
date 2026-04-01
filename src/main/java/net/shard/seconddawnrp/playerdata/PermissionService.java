package net.shard.seconddawnrp.playerdata;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.division.Rank;

import java.util.UUID;

public interface PermissionService {

    boolean isAvailable();

    boolean hasPermission(ServerPlayerEntity player, String node);

    boolean addNode(UUID playerUuid, String node);

    boolean removeNode(UUID playerUuid, String node);

    boolean addGroup(UUID playerUuid, String groupName);

    boolean removeGroup(UUID playerUuid, String groupName);

    default boolean hasPermission(ServerCommandSource source, String node) {
        if (source == null) return false;
        ServerPlayerEntity player = source.getPlayer();
        return player != null && hasPermission(player, node);
    }

    default boolean isAdmin(ServerPlayerEntity player) {
        return player != null && (
                hasPermission(player, "st.admin")
                        || player.hasPermissionLevel(3)
        );
    }

    default boolean isGm(ServerPlayerEntity player) {
        return player != null && (
                hasPermission(player, "st.gm")
                        || player.hasPermissionLevel(3)
        );
    }

    default boolean canUseTerminalDesignatorTool(ServerPlayerEntity player) {
        return player != null && (
                hasPermission(player, "st.terminal.designate")
                        || player.hasPermissionLevel(2)
        );
    }

    default boolean canUseTaskTerminalTool(ServerPlayerEntity player) {
        return player != null && (
                hasPermission(player, "st.task.terminal.designate")
                        || player.hasPermissionLevel(2)
        );
    }

    default boolean canOpenOperationsPad(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.ops.pad")
                        || hasPermission(player, "st.task.ops_pad")
                        || player.hasPermissionLevel(2)
                        || (profile != null && profile.getRank() != null
                        && profile.getRank().getAuthorityLevel() >= 2)
        );
    }

    default boolean canAssignTasks(PlayerProfile profile, ServerPlayerEntity player) {
        return player != null && (
                hasPermission(player, "st.task.assign")
                        || player.hasPermissionLevel(2)
                        || (profile != null && profile.getRank() != null
                        && profile.getRank().getAuthorityLevel() >= 2)
        );
    }

    default boolean canApproveTasks(PlayerProfile profile, ServerPlayerEntity player) {
        return player != null && (
                hasPermission(player, "st.task.approve")
                        || player.hasPermissionLevel(2)
                        || (profile != null && profile.getRank() != null
                        && profile.getRank().getAuthorityLevel() >= 3)
        );
    }

    default boolean canViewOpsPad(PlayerProfile profile, ServerPlayerEntity player) {
        return player != null && (
                hasPermission(player, "st.task.view")
                        || hasPermission(player, "st.ops.pad")
                        || player.hasPermissionLevel(2)
                        || (profile != null && profile.getRank() != null
                        && profile.getRank().getAuthorityLevel() >= 2)
        );
    }

    default boolean canPromote(PlayerProfile actorProfile, Rank targetRank, ServerPlayerEntity player) {
        return player != null && (
                hasPermission(player, "st.profile.promote")
                        || player.hasPermissionLevel(3)
                        || (actorProfile != null
                        && actorProfile.getRank() != null
                        && targetRank != null
                        && actorProfile.getRank().getAuthorityLevel() > targetRank.getAuthorityLevel())
        );
    }

    default boolean canUseEngineeringAdmin(ServerPlayerEntity player) {
        return player != null && (
                hasPermission(player, "st.engineering.admin")
                        || player.hasPermissionLevel(2)
        );
    }

    default boolean canUseMedicalActions(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.medical.treat")
                        || player.hasPermissionLevel(2)
                        || (profile != null && profile.getRank() != null
                        && profile.getRank().getAuthorityLevel() >= 1)
        );
    }

    // ── Roster / progression ────────────────────────────────────────────────

    default boolean canOpenRoster(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.roster.open")
                        || isAdmin(player)
                        || profile != null
        );
    }

    default boolean canViewAllDivisions(ServerPlayerEntity player) {
        return player != null && (
                hasPermission(player, "st.roster.view_all")
                        || isAdmin(player)
        );
    }

    default boolean canManageRosterMembers(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.roster.manage_members")
                        || player.hasPermissionLevel(2)
                        || (profile != null && profile.getRank() != null
                        && profile.getRank().getAuthorityLevel()
                        >= Rank.LIEUTENANT.getAuthorityLevel())
        );
    }

    default boolean canManageRosterRanks(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.roster.manage_ranks")
                        || player.hasPermissionLevel(2)
                        || (profile != null && profile.getRank() != null
                        && profile.getRank().getAuthorityLevel()
                        >= Rank.LIEUTENANT_COMMANDER.getAuthorityLevel())
        );
    }

    default boolean canCadetEnrol(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.cadet.enrol")
                        || canManageRosterMembers(player, profile)
        );
    }

    default boolean canCadetPromote(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.cadet.promote")
                        || canManageRosterMembers(player, profile)
        );
    }

    default boolean canCadetGraduate(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.cadet.graduate")
                        || canManageRosterRanks(player, profile)
        );
    }

    default boolean canCadetApprove(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.cadet.approve")
                        || canManageRosterRanks(player, profile)
        );
    }

    default boolean canIssueCommendation(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.commend.issue")
                        || canManageRosterMembers(player, profile)
        );
    }

    default boolean canManageOfficerSlots(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.progression.slots.manage")
                        || isAdmin(player)
        );
    }

    default boolean canAssignShipPosition(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.progression.position.assign")
                        || isAdmin(player)
        );
    }

    default boolean canGrantCertification(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.cert.grant")
                        || canManageRosterMembers(player, profile)
        );
    }

    default boolean canRevokeCertification(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.cert.revoke")
                        || canManageRosterMembers(player, profile)
        );
    }

    default boolean canAssignBillet(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.billet.assign")
                        || canManageRosterMembers(player, profile)
        );
    }

    default boolean canRevokeBillet(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.billet.revoke")
                        || canManageRosterMembers(player, profile)
        );
    }default boolean canViewOtherProfile(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.profile.view_other")
                        || isAdmin(player)
                        || canViewAllDivisions(player)
        );
    }

    default boolean canSetDivision(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.profile.set_division")
                        || canManageRosterMembers(player, profile)
        );
    }

    default boolean canSetRank(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.profile.set_rank")
                        || canManageRosterRanks(player, profile)
        );
    }

    default boolean canAddRankPoints(ServerPlayerEntity player, PlayerProfile profile) {
        return player != null && (
                hasPermission(player, "st.profile.add_points")
                        || canManageRosterMembers(player, profile)
        );
    }
}