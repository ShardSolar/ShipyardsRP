package net.shard.seconddawnrp.warpcore.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.shard.seconddawnrp.warpcore.network.WarpCoreStatusS2CPacket;
import net.shard.seconddawnrp.warpcore.screen.WarpCoreMonitorScreen;

/**
 * Client-side receiver for warp core packets.
 * Must only be called from the client initializer.
 */
public final class WarpCoreClientHandler {

    private WarpCoreClientHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                WarpCoreStatusS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> {
                    var current = context.client().currentScreen;
                    if (current instanceof WarpCoreMonitorScreen screen) {
                        // Refresh live data if already open
                        screen.updateData(payload);
                    } else {
                        context.client().setScreen(new WarpCoreMonitorScreen(payload));
                    }
                })
        );
    }
}