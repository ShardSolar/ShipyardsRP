package net.shard.seconddawnrp.tasksystem.service;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.playerdata.PermissionService;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

public class TaskPermissionService {

    private final PermissionService permissionService;

    public TaskPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public boolean canCreateTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.task.create")
                || permissionService.canAssignTasks(profile, player);
    }

    public boolean canAssignTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.task.assign")
                || permissionService.canAssignTasks(profile, player);
    }

    public boolean canPublishTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.task.publish")
                || permissionService.canAssignTasks(profile, player);
    }

    public boolean canApproveTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.task.approve")
                || permissionService.canApproveTasks(profile, player);
    }

    public boolean canReturnTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.task.review")
                || permissionService.canApproveTasks(profile, player);
    }

    public boolean canFailTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.task.fail")
                || permissionService.canApproveTasks(profile, player);
    }

    public boolean canCancelTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.task.cancel")
                || permissionService.canAssignTasks(profile, player);
    }

    public boolean canEditTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.task.edit")
                || permissionService.canAssignTasks(profile, player);
    }

    public boolean canViewOpsPad(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.task.ops")
                || permissionService.canAssignTasks(profile, player)
                || permissionService.canApproveTasks(profile, player);
    }
}