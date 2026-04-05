package net.shard.seconddawnrp.transporter.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.shard.seconddawnrp.transporter.TransporterControllerNetworking;
import net.shard.seconddawnrp.transporter.TransporterControllerNetworking.OpenPayload;

public class TransporterClientHandler {

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(OpenPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null) return;

                    PlayerInventory inv = client.player.getInventory();

                    TransporterScreenHandler handler = new TransporterScreenHandler(
                            0, inv,
                            payload.controllerPos(),
                            payload.readyPlayers(),
                            payload.dimensions(),
                            payload.shipLocations(),
                            payload.beamUpRequests()
                    );

                    client.setScreen(new TransporterControllerScreen(
                            handler, inv,
                            net.minecraft.text.Text.literal("Transporter Control")));
                })
        );
    }
}