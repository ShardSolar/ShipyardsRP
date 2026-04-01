package net.shard.seconddawnrp.playerdata;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class NoOpPermissionService implements PermissionService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean hasPermission(ServerPlayerEntity player, String node) {
        return player != null && player.hasPermissionLevel(4);
    }

    @Override
    public boolean addNode(UUID playerUuid, String node) {
        return false;
    }

    @Override
    public boolean removeNode(UUID playerUuid, String node) {
        return false;
    }

    @Override
    public boolean addGroup(UUID playerUuid, String groupName) {
        return false;
    }

    @Override
    public boolean removeGroup(UUID playerUuid, String groupName) {
        return false;
    }
}