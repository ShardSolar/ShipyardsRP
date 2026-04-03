package net.shard.seconddawnrp.degradation.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;
import net.shard.seconddawnrp.degradation.network.LocateComponentS2CPacket;
import net.shard.seconddawnrp.degradation.network.OpenEngineeringPadS2CPacket;
import net.shard.seconddawnrp.degradation.screen.EngineeringPadScreen;

public final class EngineeringPadClientHandler {

    private EngineeringPadClientHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                LocateComponentS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> spawnLocatorParticles(payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(
                OpenEngineeringPadS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> openEngineeringPad(payload))
        );
    }

    private static void openEngineeringPad(OpenEngineeringPadS2CPacket payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        client.setScreen(new EngineeringPadScreen(
                payload.components(),
                payload.warpCores(),
                payload.focusedCoreId(),
                payload.warpCoreState(),
                payload.warpCoreFuel(),
                payload.warpCoreMaxFuel(),
                payload.warpCorePower()
        ));
    }

    private static void spawnLocatorParticles(LocateComponentS2CPacket payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        double x = payload.x();
        double y = payload.y();
        double z = payload.z();
        ComponentStatus status = payload.status();

        ParticleEffect mainParticle = switch (status) {
            case NOMINAL -> ParticleTypes.END_ROD;
            case DEGRADED -> ParticleTypes.END_ROD;
            case CRITICAL -> ParticleTypes.ELECTRIC_SPARK;
            case OFFLINE -> ParticleTypes.LARGE_SMOKE;
        };

        ParticleEffect accentParticle = switch (status) {
            case NOMINAL -> ParticleTypes.WAX_ON;
            case DEGRADED -> ParticleTypes.ENCHANT;
            case CRITICAL -> ParticleTypes.END_ROD;
            case OFFLINE -> ParticleTypes.SMOKE;
        };

        int faceBurstCount = switch (status) {
            case NOMINAL -> 2;
            case DEGRADED -> 3;
            case CRITICAL -> 4;
            case OFFLINE -> 5;
        };

        int coreCount = switch (status) {
            case NOMINAL -> 2;
            case DEGRADED -> 3;
            case CRITICAL -> 4;
            case OFFLINE -> 5;
        };

        double faceOutwardSpeed = switch (status) {
            case NOMINAL -> 0.018;
            case DEGRADED -> 0.024;
            case CRITICAL -> 0.030;
            case OFFLINE -> 0.026;
        };

        double faceJitter = switch (status) {
            case NOMINAL -> 0.012;
            case DEGRADED -> 0.015;
            case CRITICAL -> 0.018;
            case OFFLINE -> 0.014;
        };

        double riseMin = switch (status) {
            case NOMINAL -> 0.010;
            case DEGRADED -> 0.014;
            case CRITICAL -> 0.018;
            case OFFLINE -> 0.016;
        };

        double riseMax = switch (status) {
            case NOMINAL -> 0.018;
            case DEGRADED -> 0.024;
            case CRITICAL -> 0.032;
            case OFFLINE -> 0.028;
        };

        double faceOffset = 0.34;

        // Top face plume
        spawnFacePlume(client, x, y, z, 0.0, faceOffset, 0.0,
                0.0, 1.0, 0.0,
                faceBurstCount, mainParticle, accentParticle,
                faceOutwardSpeed, faceJitter, riseMin, riseMax, status);

        // Bottom face plume
        spawnFacePlume(client, x, y, z, 0.0, -faceOffset, 0.0,
                0.0, -1.0, 0.0,
                faceBurstCount, mainParticle, accentParticle,
                faceOutwardSpeed, faceJitter, riseMin, riseMax, status);

        // North face plume
        spawnFacePlume(client, x, y, z, 0.0, 0.0, -faceOffset,
                0.0, 0.0, -1.0,
                faceBurstCount, mainParticle, accentParticle,
                faceOutwardSpeed, faceJitter, riseMin, riseMax, status);

        // South face plume
        spawnFacePlume(client, x, y, z, 0.0, 0.0, faceOffset,
                0.0, 0.0, 1.0,
                faceBurstCount, mainParticle, accentParticle,
                faceOutwardSpeed, faceJitter, riseMin, riseMax, status);

        // West face plume
        spawnFacePlume(client, x, y, z, -faceOffset, 0.0, 0.0,
                -1.0, 0.0, 0.0,
                faceBurstCount, mainParticle, accentParticle,
                faceOutwardSpeed, faceJitter, riseMin, riseMax, status);

        // East face plume
        spawnFacePlume(client, x, y, z, faceOffset, 0.0, 0.0,
                1.0, 0.0, 0.0,
                faceBurstCount, mainParticle, accentParticle,
                faceOutwardSpeed, faceJitter, riseMin, riseMax, status);

        // Small center shimmer so the block still feels "tagged" as one object
        for (int i = 0; i < coreCount; i++) {
            double ox = (client.world.random.nextDouble() - 0.5) * 0.18;
            double oy = (client.world.random.nextDouble() - 0.5) * 0.18;
            double oz = (client.world.random.nextDouble() - 0.5) * 0.18;

            double vx = (client.world.random.nextDouble() - 0.5) * 0.010;
            double vy = riseMin * 0.7 + client.world.random.nextDouble() * (riseMax - riseMin) * 0.5;
            double vz = (client.world.random.nextDouble() - 0.5) * 0.010;

            client.world.addParticle(
                    accentParticle,
                    x + ox,
                    y + oy,
                    z + oz,
                    vx, vy, vz
            );
        }

        if (status == ComponentStatus.OFFLINE) {
            for (int i = 0; i < 4; i++) {
                double ox = (client.world.random.nextDouble() - 0.5) * 0.30;
                double oz = (client.world.random.nextDouble() - 0.5) * 0.30;
                double oy = (client.world.random.nextDouble() - 0.5) * 0.20;

                client.world.addParticle(
                        ParticleTypes.SMOKE,
                        x + ox,
                        y + oy,
                        z + oz,
                        ox * 0.03,
                        0.01 + client.world.random.nextDouble() * 0.01,
                        oz * 0.03
                );
            }
        }
    }

    private static void spawnFacePlume(
            MinecraftClient client,
            double x,
            double y,
            double z,
            double offsetX,
            double offsetY,
            double offsetZ,
            double normalX,
            double normalY,
            double normalZ,
            int count,
            ParticleEffect mainParticle,
            ParticleEffect accentParticle,
            double outwardSpeed,
            double jitter,
            double riseMin,
            double riseMax,
            ComponentStatus status
    ) {
        for (int i = 0; i < count; i++) {
            double px = x + offsetX + (client.world.random.nextDouble() - 0.5) * 0.08;
            double py = y + offsetY + (client.world.random.nextDouble() - 0.5) * 0.08;
            double pz = z + offsetZ + (client.world.random.nextDouble() - 0.5) * 0.08;

            double vx = normalX * outwardSpeed + (client.world.random.nextDouble() - 0.5) * jitter;
            double vy = normalY * outwardSpeed + (client.world.random.nextDouble() - 0.5) * jitter;
            double vz = normalZ * outwardSpeed + (client.world.random.nextDouble() - 0.5) * jitter;

            // Slight upward bias so they feel like visible plumes, not flat sprays
            vy += riseMin + client.world.random.nextDouble() * (riseMax - riseMin);

            client.world.addParticle(
                    mainParticle,
                    px, py, pz,
                    vx, vy, vz
            );

            boolean addAccent = switch (status) {
                case NOMINAL -> client.world.random.nextFloat() < 0.20f;
                case DEGRADED -> client.world.random.nextFloat() < 0.28f;
                case CRITICAL -> client.world.random.nextFloat() < 0.40f;
                case OFFLINE -> client.world.random.nextFloat() < 0.30f;
            };

            if (addAccent) {
                client.world.addParticle(
                        accentParticle,
                        px, py, pz,
                        vx * 0.65,
                        vy * 0.75,
                        vz * 0.65
                );
            }
        }
    }
}