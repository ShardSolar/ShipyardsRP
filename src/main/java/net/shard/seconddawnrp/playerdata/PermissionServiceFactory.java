package net.shard.seconddawnrp.playerdata;

import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

public final class PermissionServiceFactory {

    private PermissionServiceFactory() {
    }

    public static PermissionService create() {
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
            System.out.println("[SecondDawnRP] LuckPerms not found. Using NoOpPermissionService.");
            return new NoOpPermissionService();
        }

        try {
            LuckPerms api = LuckPermsProvider.get();
            System.out.println("[SecondDawnRP] LuckPerms detected. Using LuckPermsPermissionService.");
            return new LuckPermsPermissionService(api);
        } catch (Throwable t) {
            System.err.println("[SecondDawnRP] LuckPerms integration failed. Falling back to NoOpPermissionService.");
            t.printStackTrace();
            return new NoOpPermissionService();
        }
    }
}