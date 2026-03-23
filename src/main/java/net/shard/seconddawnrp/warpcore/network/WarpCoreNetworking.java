package net.shard.seconddawnrp.warpcore.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class WarpCoreNetworking {

    private WarpCoreNetworking() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(
                WarpCoreStatusS2CPacket.ID,
                WarpCoreStatusS2CPacket.CODEC
        );
    }
}