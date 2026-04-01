package net.shard.seconddawnrp.playerdata;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class LuckPermsPermissionService implements PermissionService {

    private final LuckPerms luckPerms;

    public LuckPermsPermissionService(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public static LuckPermsPermissionService create(Object luckPerms) {
        return new LuckPermsPermissionService((LuckPerms) luckPerms);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean hasPermission(ServerPlayerEntity player, String node) {
        if (player == null) return false;

        User user = luckPerms.getUserManager().getUser(player.getUuid());
        if (user == null) {
            return false;
        }

        CachedPermissionData permissionData = user.getCachedData().getPermissionData();
        return permissionData.checkPermission(node).asBoolean();
    }

    @Override
    public boolean addNode(UUID playerUuid, String node) {
        User user = getOrLoadUser(playerUuid);
        if (user == null) return false;

        boolean changed = user.data().add(Node.builder(node).build()).wasSuccessful();
        if (changed) {
            luckPerms.getUserManager().saveUser(user);
        }
        return changed;
    }

    @Override
    public boolean removeNode(UUID playerUuid, String node) {
        User user = getOrLoadUser(playerUuid);
        if (user == null) return false;

        boolean changed = user.data().remove(Node.builder(node).build()).wasSuccessful();
        if (changed) {
            luckPerms.getUserManager().saveUser(user);
        }
        return changed;
    }

    @Override
    public boolean addGroup(UUID playerUuid, String groupName) {
        User user = getOrLoadUser(playerUuid);
        if (user == null) return false;

        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            return false;
        }

        boolean changed = user.data().add(InheritanceNode.builder(groupName).build()).wasSuccessful();
        if (changed) {
            luckPerms.getUserManager().saveUser(user);
        }
        return changed;
    }

    @Override
    public boolean removeGroup(UUID playerUuid, String groupName) {
        User user = getOrLoadUser(playerUuid);
        if (user == null) return false;

        boolean changed = user.data().remove(InheritanceNode.builder(groupName).build()).wasSuccessful();
        if (changed) {
            luckPerms.getUserManager().saveUser(user);
        }
        return changed;
    }

    private User getOrLoadUser(UUID uuid) {
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user != null) return user;

        try {
            return luckPerms.getUserManager().loadUser(uuid).join();
        } catch (Exception e) {
            return null;
        }
    }
}