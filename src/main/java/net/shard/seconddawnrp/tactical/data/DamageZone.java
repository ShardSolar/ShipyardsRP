package net.shard.seconddawnrp.tactical.data;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A named damage zone on the ship.
 * Maps model blocks (admin-designated) to real ship block positions.
 * When the zone takes damage past its threshold, ZoneDamageEvent fires.
 */
public class DamageZone {

    private final String zoneId;
    private final String shipId;
    private int    currentHp;
    private int    maxHp;
    private boolean damaged;

    // Model block positions — admin assigns via DamageZoneTool
    private final List<Long> modelBlockPositions = new ArrayList<>();

    // Real ship block positions — blocks to replace when zone is damaged
    private final List<Long> realShipBlockPositions = new ArrayList<>();

    public DamageZone(String zoneId, String shipId, int maxHp) {
        this.zoneId  = zoneId;
        this.shipId  = shipId;
        this.maxHp   = maxHp;
        this.currentHp = maxHp;
        this.damaged = false;
    }

    public void applyDamage(int amount) {
        currentHp = Math.max(0, currentHp - amount);
    }

    public void repair() {
        currentHp = maxHp;
        damaged   = false;
    }

    public boolean isDestroyed()  { return currentHp <= 0; }
    public float getHealthPercent() { return (float) currentHp / Math.max(1, maxHp); }

    public void addModelBlock(BlockPos pos)    { modelBlockPositions.add(pos.asLong()); }
    public void addRealShipBlock(BlockPos pos) { realShipBlockPositions.add(pos.asLong()); }

    public List<BlockPos> getModelBlocks() {
        return modelBlockPositions.stream().map(BlockPos::fromLong).toList();
    }

    public List<BlockPos> getRealShipBlocks() {
        return realShipBlockPositions.stream().map(BlockPos::fromLong).toList();
    }

    public String getZoneId()     { return zoneId; }
    public String getShipId()     { return shipId; }
    public int getCurrentHp()     { return currentHp; }
    public int getMaxHp()         { return maxHp; }
    public boolean isDamaged()    { return damaged; }
    public void setDamaged(boolean b) { this.damaged = b; }
    public void setMaxHp(int h)   { this.maxHp = h; }
}