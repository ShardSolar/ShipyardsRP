package net.shard.seconddawnrp.degradation.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the ExplosionMixin entirely.
 *
 * <p>Runs two jobs on a periodic tick schedule:
 *
 * <p><b>Job 1 — Explosion damage detection (every 5 ticks)</b><br>
 * After an explosion removes blocks, those positions become air on the
 * next tick. We snapshot each component's block state and compare. If
 * a component block has become air since the last snapshot, we know it
 * was destroyed by an explosion (or other external cause). We apply
 * heavy damage and, if the block is still air, auto-unregister.
 *
 * <p><b>Job 2 — Stale world purge (once on server start)</b><br>
 * When a Minecraft world is deleted and recreated, the component JSON
 * persists with positions from the old world. On first run we check
 * every registered component: if its world key doesn't match any loaded
 * world, it is purged immediately.
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
        // Startup purge — run once after server is fully loaded
        if (!startupPurgeDone) {
            purgeStaleWorlds(server);
            startupPurgeDone = true;
        }

        tickCounter++;
        if (tickCounter < CHECK_INTERVAL_TICKS) return;
        tickCounter = 0;

        checkBlockIntegrity(server);
    }

    // ── Job 1: Block integrity check ─────────────────────────────────────────

    private void checkBlockIntegrity(MinecraftServer server) {
        List<ComponentEntry> toUnregister = new ArrayList<>();

        for (ComponentEntry entry : service.getAllComponents()) {
            ServerWorld world = resolveWorld(server, entry.getWorldKey());
            if (world == null) continue;

            BlockPos pos = BlockPos.fromLong(entry.getBlockPosLong());
            boolean isAir = world.getBlockState(pos).isAir();

            if (!isAir) continue;

            // Block is gone — was it OFFLINE already (expected) or sudden?
            if (entry.getStatus() == ComponentStatus.OFFLINE) {
                // Already offline — just clean up the orphaned registration
                toUnregister.add(entry);
                System.out.println("[SecondDawnRP] Purging offline component with missing block: "
                        + entry.getComponentId());
            } else {
                // Sudden destruction — apply explosion damage and unregister
                System.out.println("[SecondDawnRP] Component '" + entry.getDisplayName()
                        + "' block destroyed externally — applying damage and unregistering.");
                service.applyDamage(entry.getWorldKey(), entry.getBlockPosLong(), 100);
                toUnregister.add(entry);
            }
        }

        for (ComponentEntry entry : toUnregister) {
            service.unregister(entry.getWorldKey(), entry.getBlockPosLong());
        }
    }

    // ── Job 2: Stale world purge ──────────────────────────────────────────────

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
            if (w.getRegistryKey().getValue().toString().equals(worldKey)) return w;
        }
        return null;
    }
}