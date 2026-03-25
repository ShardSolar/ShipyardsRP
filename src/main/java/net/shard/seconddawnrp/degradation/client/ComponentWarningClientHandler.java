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
                // Initial burst — large smoke + explosion effect
                for (int i = 0; i < 20; i++) {
                    double ox = (world.random.nextDouble() - 0.5) * 1.2;
                    double oz = (world.random.nextDouble() - 0.5) * 1.2;
                    world.addParticle(ParticleTypes.LARGE_SMOKE,
                            cx + ox, cy + world.random.nextDouble() * 1.0, cz + oz,
                            (world.random.nextDouble() - 0.5) * 0.04,
                            0.08 + world.random.nextDouble() * 0.06,
                            (world.random.nextDouble() - 0.5) * 0.04);
                }
                // Red persistent pulse — soul fire flame tinted with lava particles
                for (int i = 0; i < 6; i++) {
                    double ox = (world.random.nextDouble() - 0.5) * 0.5;
                    double oz = (world.random.nextDouble() - 0.5) * 0.5;
                    world.addParticle(ParticleTypes.LAVA,
                            cx + ox, cy, cz + oz, 0, 0, 0);
                }
                // Dark column — soul fire flame for visibility
                for (int i = 0; i < 3; i++) {
                    world.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                            cx, cy + i * 0.5, cz, 0, 0.02, 0);
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
                                        payload.warpCores(),
                                        payload.focusedCoreId(),
                                        payload.warpCoreState(),
                                        payload.warpCoreFuel(),
                                        payload.warpCoreMaxFuel(),
                                        payload.warpCorePower()))
                )
        );
    }

    /** Register locate packet receiver — spawns END_ROD beacon at component position. */
    public static void registerLocateReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(
                net.shard.seconddawnrp.degradation.network.LocateComponentS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> {
                    var world = context.client().world;
                    if (world == null) return;
                    double cx = payload.x(), cy = payload.y(), cz = payload.z();
                    // Tall END_ROD column
                    for (int h = 0; h < 12; h++) {
                        world.addParticle(net.minecraft.particle.ParticleTypes.END_ROD,
                                cx, cy + h, cz, 0, 0.02, 0);
                    }
                    // Ring at base
                    for (int i = 0; i < 16; i++) {
                        double angle = (Math.PI * 2 / 16) * i;
                        world.addParticle(net.minecraft.particle.ParticleTypes.END_ROD,
                                cx + Math.cos(angle) * 1.5, cy + 0.5,
                                cz + Math.sin(angle) * 1.5, 0, 0.01, 0);
                    }
                    // Second ring higher up
                    for (int i = 0; i < 8; i++) {
                        double angle = (Math.PI * 2 / 8) * i;
                        world.addParticle(net.minecraft.particle.ParticleTypes.END_ROD,
                                cx + Math.cos(angle) * 0.8, cy + 4,
                                cz + Math.sin(angle) * 0.8, 0, 0.01, 0);
                    }
                })
        );
    }
}