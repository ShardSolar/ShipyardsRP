package net.shard.seconddawnrp.gmevent.service;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.playerdata.PermissionService;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

public class GmPermissionService {

    private final PermissionService permissionService;

    public GmPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public boolean canUseGmTools(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.gm.use")
                || permissionService.canAssignTasks(profile, player);
    }

    public boolean canConfigureSpawnBlocks(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.gm.spawnblock")
                || permissionService.canAssignTasks(profile, player);
    }

    public boolean canTriggerEvents(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.gm.trigger")
                || permissionService.canAssignTasks(profile, player);
    }

    public boolean canStopEvents(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.gm.stop")
                || permissionService.canAssignTasks(profile, player);
    }

    public boolean canManageTemplates(ServerPlayerEntity player, PlayerProfile profile) {
        return permissionService.hasPermission(player, "st.gm.templates")
                || permissionService.canAssignTasks(profile, player);
    }
}