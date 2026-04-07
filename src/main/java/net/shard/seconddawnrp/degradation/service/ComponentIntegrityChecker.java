package net.shard.seconddawnrp.degradation.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Periodically verifies registered components against live world state.
 *
 * <p>Job 1 — Missing block detection:
 * If a registered component's block is now gone, it is marked missing and kept
 * registered so engineering can restore it.
 *
 * <p>Job 2 — Stale world purge:
 * If a component references a world that no longer exists, it is removed.
 */
public class ComponentIntegrityChecker {

    private static final int CHECK_INTERVAL_TICKS = 5;

    private final DegradationService service;
    private int tickCounter = 0;
    private boolean startupPurgeDone = false;

    public ComponentIntegrityChecker(DegradationService service) {
        this.service = service;
    }

    public void tick(MinecraftServer server) {
        if (!startupPurgeDone) {
            purgeStaleWorlds(server);
            startupPurgeDone = true;
        }

        tickCounter++;
        if (tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        checkBlockIntegrity(server);
    }

    // ── Job 1: Block integrity check ─────────────────────────────────────────

    private void checkBlockIntegrity(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (ComponentEntry entry : service.getAllComponents()) {
            ServerWorld world = resolveWorld(server, entry.getWorldKey());
            if (world == null) {
                continue;
            }

            boolean isAir = world.getBlockState(
                    net.minecraft.util.math.BlockPos.fromLong(entry.getBlockPosLong())
            ).isAir();

            if (isAir) {
                if (!entry.isMissingBlock()) {
                    System.out.println("[SecondDawnRP] Component '" + entry.getDisplayName()
                            + "' block is missing — marking component as missing.");
                    service.markBlockMissing(entry.getWorldKey(), entry.getBlockPosLong(), now);
                }
            } else {
                // If the block has been restored, clear missing state and restore minimum health.
                if (entry.isMissingBlock()) {
                    System.out.println("[SecondDawnRP] Component '" + entry.getDisplayName()
                            + "' block restored — clearing missing state.");
                    service.refreshMissingState(entry, true, now);
                }
            }
        }
    }

    // ── Job 2: Stale world purge ─────────────────────────────────────────────

    private void purgeStaleWorlds(MinecraftServer server) {
        List<ComponentEntry> toRemove = new ArrayList<>();

        for (ComponentEntry entry : service.getAllComponents()) {
            ServerWorld world = resolveWorld(server, entry.getWorldKey());
            if (world == null) {
                toRemove.add(entry);
                System.out.println("[SecondDawnRP] Purging component '" + entry.getComponentId()
                        + "' — world '" + entry.getWorldKey() + "' no longer exists.");
            }
        }

        for (ComponentEntry entry : toRemove) {
            service.unregister(entry.getWorldKey(), entry.getBlockPosLong());
        }

        if (!toRemove.isEmpty()) {
            System.out.println("[SecondDawnRP] Purged " + toRemove.size()
                    + " stale component(s) from missing worlds.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ServerWorld resolveWorld(MinecraftServer server, String worldKey) {
        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(worldKey)) {
                return w;
            }
        }
        return null;
    }
}