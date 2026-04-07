package net.shard.seconddawnrp.tactical.damage;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps model block positions to zone IDs, and zone IDs to real ship block positions.
 * When a zone takes damage past threshold, destroyZone() replaces real ship blocks
 * with damaged variants and destroys model blocks.
 */
public class DamageModelMapper {

    // shipId → zoneId → list of model block positions
    private static final Map<String, Map<String, List<Long>>> modelBlocks =
            new ConcurrentHashMap<>();

    // shipId → zoneId → list of real ship block positions
    private static final Map<String, Map<String, List<Long>>> realBlocks =
            new ConcurrentHashMap<>();

    // Blocks that replace damaged real ship blocks (in order of severity)
    private static final Block[] DAMAGE_VARIANTS = {
            Blocks.CRACKED_STONE_BRICKS,
            Blocks.COBBLESTONE,
            Blocks.FIRE
    };

    // ── Registration ──────────────────────────────────────────────────────────

    public static void registerModelBlock(String shipId, String zoneId, BlockPos pos) {
        modelBlocks
                .computeIfAbsent(shipId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(zoneId, k -> new ArrayList<>())
                .add(pos.asLong());
    }

    public static void registerRealBlock(String shipId, String zoneId, BlockPos pos) {
        realBlocks
                .computeIfAbsent(shipId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(zoneId, k -> new ArrayList<>())
                .add(pos.asLong());
    }

    // ── Destruction ───────────────────────────────────────────────────────────

    /**
     * Physically destroy a zone — replace real ship blocks with damaged variants,
     * destroy model blocks. Called when ZoneDamageEvent fires.
     */
    public static void destroyZone(String shipId, String zoneId,
                                   ServerWorld modelWorld, ServerWorld realWorld) {
        // Destroy model blocks
        Map<String, List<Long>> shipModel = modelBlocks.get(shipId);
        if (shipModel != null && modelWorld != null) {
            List<Long> mBlocks = shipModel.getOrDefault(zoneId, List.of());
            for (long encoded : mBlocks) {
                BlockPos pos = BlockPos.fromLong(encoded);
                modelWorld.breakBlock(pos, false);
            }
        }

        // Replace real ship blocks with damaged variants
        Map<String, List<Long>> shipReal = realBlocks.get(shipId);
        if (shipReal != null && realWorld != null) {
            List<Long> rBlocks = shipReal.getOrDefault(zoneId, List.of());
            for (int i = 0; i < rBlocks.size(); i++) {
                BlockPos pos = BlockPos.fromLong(rBlocks.get(i));
                // Cycle through damage variants based on index for visual variety
                Block damageBlock = DAMAGE_VARIANTS[i % DAMAGE_VARIANTS.length];
                realWorld.setBlockState(pos, damageBlock.getDefaultState());
            }
        }

        System.out.println("[Tactical] Zone " + zoneId + " on " + shipId + " physically destroyed.");
    }

    /**
     * Restore a zone — called by ZoneRepairListener when Engineering repairs.
     * In MVP, restores to stone bricks. Full implementation would snapshot original blocks.
     */
    public static void restoreZone(String shipId, String zoneId, ServerWorld realWorld) {
        Map<String, List<Long>> shipReal = realBlocks.get(shipId);
        if (shipReal == null || realWorld == null) return;

        List<Long> rBlocks = shipReal.getOrDefault(zoneId, List.of());
        for (long encoded : rBlocks) {
            BlockPos pos = BlockPos.fromLong(encoded);
            realWorld.setBlockState(pos, Blocks.STONE_BRICKS.getDefaultState());
        }
        System.out.println("[Tactical] Zone " + zoneId + " on " + shipId + " physically restored.");
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public static List<BlockPos> getModelBlocks(String shipId, String zoneId) {
        return modelBlocks
                .getOrDefault(shipId, Map.of())
                .getOrDefault(zoneId, List.of())
                .stream().map(BlockPos::fromLong).toList();
    }

    public static List<BlockPos> getRealBlocks(String shipId, String zoneId) {
        return realBlocks
                .getOrDefault(shipId, Map.of())
                .getOrDefault(zoneId, List.of())
                .stream().map(BlockPos::fromLong).toList();
    }

    public static Set<String> getZonesForShip(String shipId) {
        Set<String> zones = new HashSet<>();
        Map<String, List<Long>> m = modelBlocks.get(shipId);
        Map<String, List<Long>> r = realBlocks.get(shipId);
        if (m != null) zones.addAll(m.keySet());
        if (r != null) zones.addAll(r.keySet());
        return zones;
    }
}