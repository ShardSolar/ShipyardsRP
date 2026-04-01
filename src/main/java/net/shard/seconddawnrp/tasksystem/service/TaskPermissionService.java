package net.shard.seconddawnrp.tasksystem.service;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.playerdata.PermissionService;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

public class TaskPermissionService {

    private final PermissionService permissionService;

    public TaskPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public boolean canAssignTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.canAssignTasks(profile, player);
    }

    public boolean canApproveTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.canApproveTasks(profile, player);
    }

    public boolean canViewOpsPad(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.canViewOpsPad(profile, player);
    }

    public boolean canOpenOperationsPad(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.canOpenOperationsPad(player, profile);
    }

    // Compatibility methods expected by ModNetworking

    public boolean canCreateTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return canAssignTasks(player, profile);
    }

    public boolean canPublishTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return canAssignTasks(player, profile);
    }

    public boolean canReturnTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return canApproveTasks(player, profile);
    }

    public boolean canFailTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return canApproveTasks(player, profile);
    }

    public boolean canCancelTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return canApproveTasks(player, profile);
    }

    public boolean canEditTasks(ServerPlayerEntity player, PlayerProfile profile) {
        return canAssignTasks(player, profile);
    }
}