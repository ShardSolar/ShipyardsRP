package net.shard.seconddawnrp.gmevent.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.shard.seconddawnrp.gmevent.network.OpenAnomalyConfigS2CPacket;
import net.shard.seconddawnrp.gmevent.network.SaveAnomalyConfigC2SPacket;
import net.shard.seconddawnrp.gmevent.screen.AnomalyConfigScreen;

public final class AnomalyClientHandler {
    private AnomalyClientHandler() {}

    /** Call from client initializer. */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                OpenAnomalyConfigS2CPacket.ID,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(new AnomalyConfigScreen(
                                payload.entryId(), payload.name(), payload.description(),
                                payload.anomalyType(), payload.active()))
                )
        );
    }

    /** Call from server initializer (SecondDawnRP.onInitialize). */
    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(
                SaveAnomalyConfigC2SPacket.ID,
                (payload, context) -> context.server().execute(() -> {
                    var service = net.shard.seconddawnrp.SecondDawnRP.ANOMALY_SERVICE;
                    service.getAll().stream()
                            .filter(e -> e.getEntryId().equals(payload.entryId()))
                            .findFirst()
                            .ifPresent(entry -> {
                                entry.setName(payload.name());
                                entry.setDescription(payload.description());
                                try { entry.setType(
                                        net.shard.seconddawnrp.gmevent.data.AnomalyType
                                                .valueOf(payload.anomalyType()));
                                } catch (Exception ignored) {}
                                if (payload.activate() && !entry.isActive()) {
                                    service.activate(entry.getEntryId(), context.server());
                                } else if (!payload.activate() && entry.isActive()) {
                                    service.deactivate(entry.getEntryId());
                                }
                                service.saveEntry(entry);
                            });
                })
        );
    }
}