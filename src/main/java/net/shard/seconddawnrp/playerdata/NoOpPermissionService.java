package net.shard.seconddawnrp.playerdata;

import net.minecraft.server.network.ServerPlayerEntity;

public class NoOpPermissionService implements PermissionService {

    @Override
    public boolean hasPermission(ServerPlayerEntity player, String node) {
        return player.hasPermissionLevel(4);
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}