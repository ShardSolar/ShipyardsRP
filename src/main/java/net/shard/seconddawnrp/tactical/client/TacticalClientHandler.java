package net.shard.seconddawnrp.tactical.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.shard.seconddawnrp.tactical.console.GmShipConsoleScreen;
import net.shard.seconddawnrp.tactical.console.TacticalScreen;
import net.shard.seconddawnrp.tactical.console.TacticalScreenHandler;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking.*;

/**
 * Client-side handler for Tactical packets.
 * Registered in SecondDawnRPClient.onInitializeClient() only.
 */
public class TacticalClientHandler {

    public static void registerClientReceivers() {
        // Receive encounter update — push to open Tactical screens
        ClientPlayNetworking.registerGlobalReceiver(EncounterUpdatePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    MinecraftClient mc = MinecraftClient.getInstance();

                    if (mc.currentScreen instanceof TacticalScreen screen) {
                        screen.getScreenHandler().applyUpdate(payload);
                    } else if (mc.currentScreen instanceof GmShipConsoleScreen gmScreen) {
                        gmScreen.applyUpdate(payload);
                    }
                    // If no screen is open, data is discarded — screens request full
                    // state on open via TacticalNetworking.sendEncounterUpdate()
                }));

        // Receive open packet — sent by TacticalConsoleBlock on right-click
        ClientPlayNetworking.registerGlobalReceiver(OpenTacticalPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player == null) return;

                    if (payload.gmMode()) {
                        // Open GM console
                        mc.setScreen(new GmShipConsoleScreen(
                                payload.encounterId(),
                                payload.ships(),
                                payload.combatLog()));
                    } else {
                        // Open standard Tactical screen via HandledScreen
                        var handler = new TacticalScreenHandler(0, mc.player.getInventory());
                        handler.applyUpdate(new EncounterUpdatePayload(
                                payload.encounterId(), payload.status(),
                                payload.ships(), payload.combatLog()));
                        mc.setScreen(new TacticalScreen(handler, mc.player.getInventory(),
                                net.minecraft.text.Text.literal("Tactical Console")));
                    }
                }));
    }
}