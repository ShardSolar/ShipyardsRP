package net.shard.seconddawnrp.playerdata;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.minecraft.server.network.ServerPlayerEntity;

public class LuckPermsPermissionService implements PermissionService {

    private final LuckPerms luckPerms;

    public LuckPermsPermissionService(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public static LuckPermsPermissionService create(Object luckPerms) {
        return new LuckPermsPermissionService((LuckPerms) luckPerms);
    }

    @Override
    public boolean hasPermission(ServerPlayerEntity player, String node) {
        User user = luckPerms.getUserManager().getUser(player.getUuid());
        if (user == null) {
            user = luckPerms.getUserManager().loadUser(player.getUuid()).join();
        }
        if (user == null) {
            return false;
        }

        CachedPermissionData permissionData = user.getCachedData().getPermissionData();
        return permissionData.checkPermission(node).asBoolean();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}