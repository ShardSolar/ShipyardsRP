package net.shard.seconddawnrp.degradation.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.degradation.network.ComponentWarningS2CPacket;
import net.shard.seconddawnrp.degradation.network.OpenEngineeringPadS2CPacket;
import net.shard.seconddawnrp.degradation.screen.EngineeringPadScreen;

/**
 * Registers all client-side packet receivers for the degradation system.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@link ComponentWarningS2CPacket} — spawns particles at degraded blocks
 *   <li>{@link OpenEngineeringPadS2CPacket} — opens the Engineering PAD screen
 * </ul>
 *
 * <p>Must only be called from the client initializer.
 */
public final class ComponentWarningClientHandler {

    private ComponentWarningClientHandler() {}

    public static void register() {
        registerWarningReceiver();
        registerOpenPadReceiver();
    }

    // ── Warning particles ─────────────────────────────────────────────────────

    private static void registerWarningReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(
                ComponentWarningS2CPacket.ID,
                (payload, context) -> context.client().execute(() ->
                        handleWarning(payload, context.client().world)
                )
        );
    }

    private static void handleWarning(ComponentWarningS2CPacket payload, ClientWorld world) {
        if (world == null) return;
        BlockPos pos = BlockPos.fromLong(payload.blockPosLong());
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.1;
        double cz = pos.getZ() + 0.5;

        switch (payload.status()) {
            case DEGRADED -> {
                for (int i = 0; i < 8; i++) {
                    double ox = (world.random.nextDouble() - 0.5) * 0.6;
                    double oz = (world.random.nextDouble() - 0.5) * 0.6;
                    world.addParticle(ParticleTypes.FLAME,
                            cx + ox, cy, cz + oz,
                            0, 0.04 + world.random.nextDouble() * 0.03, 0);
                }
            }
            case CRITICAL -> {
                for (int i = 0; i < 14; i++) {
                    double ox = (world.random.nextDouble() - 0.5) * 0.8;
                    double oz = (world.random.nextDouble() - 0.5) * 0.8;
                    world.addParticle(ParticleTypes.LARGE_SMOKE,
                            cx + ox, cy, cz + oz,
                            0, 0.05 + world.random.nextDouble() * 0.05, 0);
                }
                for (int i = 0; i < 5; i++) {
                    double ox = (world.random.nextDouble() - 0.5) * 0.4;
                    double oz = (world.random.nextDouble() - 0.5) * 0.4;
                    world.addParticle(ParticleTypes.ANGRY_VILLAGER,
                            cx + ox, cy + world.random.nextDouble() * 0.5, cz + oz,
                            0, 0, 0);
                }
            }
            case OFFLINE -> {
                for (int i = 0; i < 20; i++) {
                    double ox = (world.random.nextDouble() - 0.5) * 1.0;
                    double oz = (world.random.nextDouble() - 0.5) * 1.0;
                    world.addParticle(ParticleTypes.LARGE_SMOKE,
                            cx + ox, cy + world.random.nextDouble() * 0.8, cz + oz,
                            (world.random.nextDouble() - 0.5) * 0.02,
                            0.06 + world.random.nextDouble() * 0.04,
                            (world.random.nextDouble() - 0.5) * 0.02);
                }
            }
            default -> { }
        }
    }

    // ── Open Engineering PAD screen ───────────────────────────────────────────

    private static void registerOpenPadReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(
                OpenEngineeringPadS2CPacket.ID,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(
                                new EngineeringPadScreen(
                                        payload.components(),
                                        payload.warpCoreState(),
                                        payload.warpCoreFuel(),
                                        payload.warpCoreMaxFuel(),
                                        payload.warpCorePower()))
                )
        );
    }
}