package net.shard.seconddawnrp.warpcore.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.shard.seconddawnrp.SecondDawnRP;

public final class WarpCoreNetworking {

    private WarpCoreNetworking() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(WarpCoreStatusS2CPacket.ID, WarpCoreStatusS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(WarpCoreActionC2SPacket.ID, WarpCoreActionC2SPacket.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(
                WarpCoreActionC2SPacket.ID,
                (payload, context) -> context.server().execute(() -> {
                    var player = context.player();
                    switch (payload.action()) {
                        case STARTUP  -> SecondDawnRP.WARP_CORE_SERVICE.initiateStartup(payload.entryId(), player);
                        case SHUTDOWN -> SecondDawnRP.WARP_CORE_SERVICE.initiateShutdown(payload.entryId(), player);
                        case RESET    -> SecondDawnRP.WARP_CORE_SERVICE.resetFromFailed(payload.entryId(), player);
                    }
                })
        );
    }
}