package net.shard.seconddawnrp.medical.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.shard.seconddawnrp.medical.network.OpenMedicalPadS2CPacket;

/**
 * Registers the client-side receiver for {@link OpenMedicalPadS2CPacket}.
 * Called from the client mod initializer.
 */
public class MedicalPadClientHandler {

    public static void registerClientReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(
                OpenMedicalPadS2CPacket.ID,
                (payload, context) -> {
                    MinecraftClient client = context.client();
                    client.execute(() -> {
                        if (client.player == null) return;

                        if (client.currentScreen instanceof MedicalPadScreen existing) {
                            // Screen already open — refresh data without reopening
                            existing.refresh(payload.data());
                        } else {
                            client.setScreen(new MedicalPadScreen(payload.data()));
                        }
                    });
                }
        );
    }
}