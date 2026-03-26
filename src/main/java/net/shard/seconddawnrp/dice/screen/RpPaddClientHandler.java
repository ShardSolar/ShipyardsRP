package net.shard.seconddawnrp.dice.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.shard.seconddawnrp.dice.network.OpenRpPaddS2CPacket;

public final class RpPaddClientHandler {

    private RpPaddClientHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                OpenRpPaddS2CPacket.ID,
                (packet, context) -> context.client().execute(() ->
                        MinecraftClient.getInstance().setScreen(new RpPaddScreen(packet))));
    }
}