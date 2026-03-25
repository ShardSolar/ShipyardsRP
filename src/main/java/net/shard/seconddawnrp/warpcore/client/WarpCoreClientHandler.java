package net.shard.seconddawnrp.warpcore.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.shard.seconddawnrp.warpcore.network.WarpCoreStatusS2CPacket;
import net.shard.seconddawnrp.warpcore.screen.WarpCoreMonitorScreen;

public final class WarpCoreClientHandler {

    private WarpCoreClientHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                WarpCoreStatusS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> {
                    var current = context.client().currentScreen;
                    if (current instanceof WarpCoreMonitorScreen screen) {
                        screen.updateData(payload);
                    } else {
                        // entryId is carried in the packet — pass it to the screen for button actions
                        context.client().setScreen(
                                new WarpCoreMonitorScreen(payload, payload.entryId()));
                    }
                })
        );
    }
}