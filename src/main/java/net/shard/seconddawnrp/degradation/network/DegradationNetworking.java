package net.shard.seconddawnrp.degradation.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/*
 * Registers all networking payloads for the engineering degradation system.
 *
 * <p>Called from {@link net.shard.seconddawnrp.SecondDawnRP#onInitialize()}.
 * Client receivers are registered in the client initializer.
 */
public final class DegradationNetworking {

    private DegradationNetworking() {}

    /** Register all S2C payload types. Must be called on both sides. */
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(
                ComponentWarningS2CPacket.ID,
                ComponentWarningS2CPacket.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                OpenEngineeringPadS2CPacket.ID,
                OpenEngineeringPadS2CPacket.CODEC
        );
    }
}