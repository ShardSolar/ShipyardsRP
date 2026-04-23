package net.shard.seconddawnrp.tactical.data;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A named damage zone on the ship.
 * Maps model blocks (admin-designated) to real ship block positions.
 * When the zone takes damage past its threshold, ZoneDamageEvent fires.
 *
 * Bounding box is computed lazily from real ship block positions and used
 * to scope status effects to players physically inside the zone.
 */
public class DamageZone {

    private final String zoneId;
    private final String shipId;
    private int     currentHp;
    private int     maxHp;
    private boolean damaged;

    /**
     * Tracks which progressive damage stage has been visually applied to this zone.
     * 0 = none (nominal), 1 = light damage, 2 = heavy damage, 3 = destroyed
     * One-way only — never decreases without an explicit repair().
     */
    private int damageStageApplied = 0;

    // Model block positions — admin assigns via DamageZoneTool
    private final List<Long> modelBlockPositions    = new ArrayList<>();

    // Real ship block positions — blocks to replace when zone is damaged
    private final List<Long> realShipBlockPositions = new ArrayList<>();

    // Cached bounding box — invalidated when blocks are added
    private transient ZoneBounds cachedBounds = null;

    public DamageZone(String zoneId, String shipId, int maxHp) {
        this.zoneId    = zoneId;
        this.shipId    = shipId;
        this.maxHp     = maxHp;
        this.currentHp = maxHp;
        this.damaged   = false;
    }

    // ── HP ────────────────────────────────────────────────────────────────────

    public void applyDamage(int amount) {
        currentHp = Math.max(0, currentHp - amount);
    }

    public void repair() {
        currentHp         = maxHp;
        damaged           = false;
        damageStageApplied = 0;
    }

    public boolean isDestroyed()      { return currentHp <= 0; }
    public float getHealthPercent()   { return (float) currentHp / Math.max(1, maxHp); }

    // ── Block registration ────────────────────────────────────────────────────

    public void addModelBlock(BlockPos pos) {
        modelBlockPositions.add(pos.asLong());
    }

    public void addRealShipBlock(BlockPos pos) {
        realShipBlockPositions.add(pos.asLong());
        cachedBounds = null;
    }

    public boolean removeModelBlock(BlockPos pos) {
        return modelBlockPositions.remove(pos.asLong());
    }

    public boolean removeRealShipBlock(BlockPos pos) {
        boolean removed = realShipBlockPositions.remove(pos.asLong());
        if (removed) cachedBounds = null;
        return removed;
    }

    public void clearModelBlocks() {
        modelBlockPositions.clear();
    }

    public void clearRealShipBlocks() {
        realShipBlockPositions.clear();
        cachedBounds = null;
    }

    public List<BlockPos> getModelBlocks() {
        return modelBlockPositions.stream().map(BlockPos::fromLong).toList();
    }

    public List<BlockPos> getRealShipBlocks() {
        return realShipBlockPositions.stream().map(BlockPos::fromLong).toList();
    }

    // ── Bounding box ──────────────────────────────────────────────────────────

    public ZoneBounds getRealBlockBounds(int padding) {
        if (cachedBounds != null) return cachedBounds;
        if (realShipBlockPositions.isEmpty()) return null;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (long encoded : realShipBlockPositions) {
            BlockPos pos = BlockPos.fromLong(encoded);
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }

        cachedBounds = new ZoneBounds(
                minX - padding, minY - padding, minZ - padding,
                maxX + padding + 1, maxY + padding + 1, maxZ + padding + 1);
        return cachedBounds;
    }

    public boolean containsPlayer(PlayerEntity player) {
        ZoneBounds bounds = getRealBlockBounds(3);
        if (bounds == null) return false;
        return bounds.contains(player.getX(), player.getY(), player.getZ());
    }

    // ── ZoneBounds record ─────────────────────────────────────────────────────

    public record ZoneBounds(int minX, int minY, int minZ,
                             int maxX, int maxY, int maxZ) {
        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX
                    && y >= minY && y < maxY
                    && z >= minZ && z < maxZ;
        }
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String getZoneId()               { return zoneId; }
    public String getShipId()               { return shipId; }
    public int getCurrentHp()               { return currentHp; }
    public int getMaxHp()                   { return maxHp; }
    public boolean isDamaged()              { return damaged; }
    public void setDamaged(boolean b)       { this.damaged = b; }
    public void setMaxHp(int h)             { this.maxHp = h; }
    public int getDamageStageApplied()      { return damageStageApplied; }
    public void setDamageStageApplied(int s){ this.damageStageApplied = s; }
}