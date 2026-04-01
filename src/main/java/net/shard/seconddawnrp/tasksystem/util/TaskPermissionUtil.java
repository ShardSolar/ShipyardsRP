package net.shard.seconddawnrp.tasksystem.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.SecondDawnRP;

public final class TaskPermissionUtil {

    private TaskPermissionUtil() {
    }

    public static boolean canOpenOperationsPad(ServerPlayerEntity player) {
        var profile = SecondDawnRP.PROFILE_MANAGER.getOrLoadProfile(
                player.getUuid(),
                player.getName().getString()
        );
        return SecondDawnRP.PERMISSION_SERVICE.canOpenOperationsPad(player, profile);
    }
}