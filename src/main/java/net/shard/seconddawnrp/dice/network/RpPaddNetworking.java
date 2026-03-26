package net.shard.seconddawnrp.dice.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registers ALL RP PADD and submission network payloads.
 * Call registerPayloads() in onInitialize() and registerServerReceivers() in onInitialize().
 */
public final class RpPaddNetworking {

    private RpPaddNetworking() {}

    public static void registerPayloads() {
        // RP PADD item GUI
        PayloadTypeRegistry.playS2C().register(OpenRpPaddS2CPacket.ID,        OpenRpPaddS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ToggleRpRecordingC2SPacket.ID, ToggleRpRecordingC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SignRpPaddC2SPacket.ID,        SignRpPaddC2SPacket.CODEC);

        // Submission review (Ops PADD)
        PayloadTypeRegistry.playS2C().register(PushSubmissionsS2CPacket.ID,   PushSubmissionsS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ReviewSubmissionC2SPacket.ID,  ReviewSubmissionC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectSubmissionC2SPacket.ID,  SelectSubmissionC2SPacket.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(
                ToggleRpRecordingC2SPacket.ID,
                (packet, context) -> context.server().execute(() ->
                        ToggleRpRecordingC2SPacket.handle(packet, context.player())));

        ServerPlayNetworking.registerGlobalReceiver(
                SignRpPaddC2SPacket.ID,
                (packet, context) -> context.server().execute(() ->
                        SignRpPaddC2SPacket.handle(packet, context.player())));

        ServerPlayNetworking.registerGlobalReceiver(
                ReviewSubmissionC2SPacket.ID,
                (packet, context) -> context.server().execute(() ->
                        ReviewSubmissionC2SPacket.handle(packet, context.player())));

        ServerPlayNetworking.registerGlobalReceiver(
                SelectSubmissionC2SPacket.ID,
                (packet, context) -> context.server().execute(() ->
                        SelectSubmissionC2SPacket.handle(packet, context.player())));
    }
}