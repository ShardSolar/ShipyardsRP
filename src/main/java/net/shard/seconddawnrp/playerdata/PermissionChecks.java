package net.shard.seconddawnrp.playerdata;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.SecondDawnRP;

public final class PermissionChecks {

    private PermissionChecks() {
    }

    public static boolean isAdmin(ServerCommandSource source) {
        if (source == null) return false;
        ServerPlayerEntity player = source.getPlayer();
        return player != null && SecondDawnRP.PERMISSION_SERVICE.isAdmin(player);
    }

    public static boolean canViewAllRoster(ServerCommandSource source) {
        if (source == null) return false;
        ServerPlayerEntity player = source.getPlayer();
        return player != null && SecondDawnRP.PERMISSION_SERVICE.canViewAllDivisions(player);
    }
}