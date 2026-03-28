package net.shard.seconddawnrp.roster.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.roster.data.RosterActionC2SPacket;
import net.shard.seconddawnrp.roster.data.RosterOpenData;
import net.shard.seconddawnrp.roster.data.RosterRefreshS2CPacket;
import net.shard.seconddawnrp.roster.screen.RosterScreenHandlerFactory;

import java.util.UUID;

public class RosterNetworking {

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(
                RosterActionC2SPacket.ID,
                RosterActionC2SPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                RosterRefreshS2CPacket.ID,
                RosterRefreshS2CPacket.CODEC
        );
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(
                RosterActionC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleRosterAction(context.player(), payload))
        );
    }

    private static void handleRosterAction(ServerPlayerEntity actor,
                                           RosterActionC2SPacket packet) {
        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(packet.targetUuidStr());
        } catch (IllegalArgumentException e) {
            actor.sendMessage(Text.literal("[Roster] Invalid target UUID."), false);
            return;
        }

        String feedback = SecondDawnRP.ROSTER_SERVICE.executeAction(
                actor, packet.action(), targetUuid, packet.stringArg(), packet.intArg());

        RosterOpenData updated = SecondDawnRP.ROSTER_SERVICE.buildForViewer(actor);
        ServerPlayNetworking.send(actor, new RosterRefreshS2CPacket(updated, feedback));
    }

    /**
     * Opens the roster screen for a player.
     * Uses ExtendedScreenHandlerFactory so RosterOpenData is encoded via the
     * PACKET_CODEC and sent to the client as part of the screen-open packet —
     * identical to how TerminalScreenHandlerFactory works for TerminalScreen.
     */
    public static void openRoster(ServerPlayerEntity player) {
        RosterOpenData data = SecondDawnRP.ROSTER_SERVICE.buildForViewer(player);
        player.openHandledScreen(new RosterScreenHandlerFactory(data));
    }
}