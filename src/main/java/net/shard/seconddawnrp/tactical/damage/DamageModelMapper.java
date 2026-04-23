package net.shard.seconddawnrp.tactical.damage;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.tactical.data.DamageZone;

import java.util.List;
import java.util.Random;

/**
 * Pure executor — receives block lists from DamageZone objects and performs
 * world manipulation. Owns no state of its own.
 *
 * Progressive damage stages (one-way, threshold-gated):
 *   Stage 0 — Nominal      (>66% HP)  — no block changes
 *   Stage 1 — Light damage (33-66%)   — 30% of real blocks → GRAY_CONCRETE
 *   Stage 2 — Heavy damage (0-33%)    — 60% of real blocks → BLACK_CONCRETE + fire spots
 *   Stage 3 — Destroyed    (0% HP)    — 100% of real blocks → AIR/FIRE/BLACK/GRAY cycling
 *
 * applyProgressiveDamage() — call after every hit; no-ops if stage hasn't changed
 * destroyZone()            — full destruction (stage 3), also breaks model blocks
 * restoreZone()            — resets real blocks to STONE_BRICKS, clears stage
 */
public class DamageModelMapper {

    private static final Random RNG = new Random();

    // Destruction palette — cycles across all real blocks on full destroy
    private static final Block[] DESTROY_PALETTE = {
            Blocks.AIR,
            Blocks.FIRE,
            Blocks.BLACK_CONCRETE,
            Blocks.GRAY_CONCRETE
    };

    // ── Progressive damage entry point ────────────────────────────────────────

    /**
     * Evaluates the zone's current HP and applies the appropriate damage stage
     * to real ship blocks if the stage has advanced since last call.
     * Safe to call on every hit — will no-op if stage hasn't changed.
     */
    public static void applyProgressiveDamage(DamageZone zone, ServerWorld realWorld) {
        if (zone == null || realWorld == null) return;

        List<BlockPos> realBlocks = zone.getRealShipBlocks();
        if (realBlocks.isEmpty()) return;

        float hp = zone.getHealthPercent();
        int currentStage = zone.getDamageStageApplied();
        int targetStage;

        if (zone.isDestroyed()) {
            targetStage = 3;
        } else if (hp <= 0.33f) {
            targetStage = 2;
        } else if (hp <= 0.66f) {
            targetStage = 1;
        } else {
            targetStage = 0;
        }

        // One-way — never go backwards
        if (targetStage <= currentStage) return;

        switch (targetStage) {
            case 1 -> applyLightDamage(realBlocks, realWorld);
            case 2 -> applyHeavyDamage(realBlocks, realWorld);
            case 3 -> applyFullDestruction(zone, realWorld);
        }

        zone.setDamageStageApplied(targetStage);

        System.out.println("[Tactical] Zone " + zone.getZoneId()
                + " advanced to damage stage " + targetStage
                + " (" + realBlocks.size() + " real blocks).");
    }

    // ── Stage 1 — Light damage (33-66% HP) ───────────────────────────────────
    // 30% of blocks → GRAY_CONCRETE (scorching/scorch marks)

    private static void applyLightDamage(List<BlockPos> realBlocks, ServerWorld world) {
        int count = Math.max(1, (int)(realBlocks.size() * 0.30f));
        List<BlockPos> shuffled = shuffled(realBlocks);
        for (int i = 0; i < count; i++) {
            world.setBlockState(shuffled.get(i), Blocks.GRAY_CONCRETE.getDefaultState());
        }
    }

    // ── Stage 2 — Heavy damage (0-33% HP) ────────────────────────────────────
    // 60% of blocks → BLACK_CONCRETE, occasional FIRE spots

    private static void applyHeavyDamage(List<BlockPos> realBlocks, ServerWorld world) {
        int count = Math.max(1, (int)(realBlocks.size() * 0.60f));
        List<BlockPos> shuffled = shuffled(realBlocks);
        for (int i = 0; i < count; i++) {
            BlockPos pos = shuffled.get(i);
            // ~15% chance of fire instead of black concrete for organic look
            Block variant = (RNG.nextFloat() < 0.15f) ? Blocks.FIRE : Blocks.BLACK_CONCRETE;
            world.setBlockState(pos, variant.getDefaultState());
        }
    }

    // ── Stage 3 — Full destruction (0% HP) ───────────────────────────────────
    // 100% of blocks cycle through destroy palette

    private static void applyFullDestruction(DamageZone zone, ServerWorld realWorld) {
        List<BlockPos> realBlocks = zone.getRealShipBlocks();
        for (int i = 0; i < realBlocks.size(); i++) {
            Block variant = DESTROY_PALETTE[i % DESTROY_PALETTE.length];
            realWorld.setBlockState(realBlocks.get(i), variant.getDefaultState());
        }
    }

    // ── destroyZone — legacy full-destroy entry point ─────────────────────────
    // Called by onZoneDestroyed(). Breaks model blocks + applies stage 3 visuals.

    public static void destroyZone(DamageZone zone,
                                   ServerWorld modelWorld,
                                   ServerWorld realWorld) {
        if (zone == null) return;

        // Break model blocks
        if (modelWorld != null) {
            for (BlockPos pos : zone.getModelBlocks()) {
                modelWorld.breakBlock(pos, false);
            }
        }

        // Apply full destruction visuals (stage 3)
        if (realWorld != null) {
            applyFullDestruction(zone, realWorld);
            zone.setDamageStageApplied(3);
        }

        System.out.println("[Tactical] Zone " + zone.getZoneId()
                + " on " + zone.getShipId() + " physically destroyed.");
    }

    // ── restoreZone ───────────────────────────────────────────────────────────

    /**
     * Restore a zone's real ship blocks to stone bricks and reset damage stage.
     * Full implementation would snapshot original block states at registration time.
     */
    public static void restoreZone(DamageZone zone, ServerWorld realWorld) {
        if (zone == null || realWorld == null) return;

        for (BlockPos pos : zone.getRealShipBlocks()) {
            realWorld.setBlockState(pos, Blocks.STONE_BRICKS.getDefaultState());
        }

        zone.setDamageStageApplied(0);

        System.out.println("[Tactical] Zone " + zone.getZoneId()
                + " on " + zone.getShipId() + " physically restored.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns a new randomly-shuffled copy of the list.
     * Original list is not modified.
     */
    private static List<BlockPos> shuffled(List<BlockPos> source) {
        List<BlockPos> copy = new java.util.ArrayList<>(source);
        java.util.Collections.shuffle(copy, RNG);
        return copy;
    }
}