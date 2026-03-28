package net.shard.seconddawnrp.roster.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.shard.seconddawnrp.roster.data.RosterRefreshS2CPacket;
import net.shard.seconddawnrp.roster.screen.RosterScreen;

/**
 * Client-side networking for the Roster.
 * Registered from SecondDawnRPClient (the client mod initializer).
 *
 * When a RosterRefreshS2CPacket arrives, if the Roster screen is open,
 * the handler's member list is replaced and the screen re-renders.
 */
public class RosterClientNetworking {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                RosterRefreshS2CPacket.ID,
                (payload, context) -> {
                    MinecraftClient client = context.client();
                    client.execute(() -> {
                        Screen currentScreen = client.currentScreen;
                        if (currentScreen instanceof RosterScreen rosterScreen) {
                            rosterScreen.getScreenHandler().applyRefresh(
                                    payload.data(), payload.feedbackMessage());
                        }
                    });
                }
        );
    }
}