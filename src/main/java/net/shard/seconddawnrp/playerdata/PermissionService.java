package net.shard.seconddawnrp.playerdata;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.division.Rank;

public interface PermissionService {

    boolean hasPermission(ServerPlayerEntity player, String node);

    default boolean isAvailable() {
        return false;
    }

    default boolean canAssignTasks(PlayerProfile profile, ServerPlayerEntity player) {
        return hasPermission(player, "st.task.assign") || profile.getRank().getAuthorityLevel() >= 2;
    }

    default boolean canApproveTasks(PlayerProfile profile, ServerPlayerEntity player) {
        return hasPermission(player, "st.task.approve") || profile.getRank().getAuthorityLevel() >= 3;
    }

    default boolean canPromote(PlayerProfile profile, Rank targetRank, ServerPlayerEntity player) {
        return hasPermission(player, "st.profile.promote")
                || profile.getRank().getAuthorityLevel() > targetRank.getAuthorityLevel();
    }
}