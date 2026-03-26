package net.shard.seconddawnrp.dice.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.shard.seconddawnrp.tasksystem.pad.OperationsPadScreen;

public final class SubmissionClientHandler {

    private SubmissionClientHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                PushSubmissionsS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> {
                    var mc = MinecraftClient.getInstance();
                    if (mc.currentScreen instanceof OperationsPadScreen screen) {
                        screen.getScreenHandler().replaceSubmissions(
                                packet.submissions(),
                                packet.selectedId(),
                                packet.selectedLog());
                    }
                })
        );
    }
}