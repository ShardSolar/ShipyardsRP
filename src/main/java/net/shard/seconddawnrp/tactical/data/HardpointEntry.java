package net.shard.seconddawnrp.tactical.data;

import net.minecraft.util.math.BlockPos;

/**
 * A weapon hardpoint registered on a physical ship build.
 * Registered by admin using the Hardpoint Registration Tool.
 * Hardpoints are also registered as degradation components.
 */
public class HardpointEntry {

    public enum WeaponType { PHASER_ARRAY, TORPEDO_TUBE }
    public enum Arc        { FORE, AFT, PORT, STARBOARD }

    private final String hardpointId;
    private final String shipId;
    private final long   blockPosLong;  // packed BlockPos
    private final WeaponType weaponType;
    private final Arc    arc;
    private final int    powerDraw;
    private final int    reloadTicks;
    private int          health;
    private int          cooldownRemaining;

    public HardpointEntry(String hardpointId, String shipId, BlockPos blockPos,
                          WeaponType weaponType, Arc arc,
                          int powerDraw, int reloadTicks, int health) {
        this.hardpointId       = hardpointId;
        this.shipId            = shipId;
        this.blockPosLong      = blockPos.asLong();
        this.weaponType        = weaponType;
        this.arc               = arc;
        this.powerDraw         = powerDraw;
        this.reloadTicks       = reloadTicks;
        this.health            = health;
        this.cooldownRemaining = 0;
    }

    public boolean isAvailable() {
        return health > 0 && cooldownRemaining <= 0;
    }

    public void fireCooldown() {
        this.cooldownRemaining = reloadTicks;
    }

    public void tickCooldown() {
        if (cooldownRemaining > 0) cooldownRemaining--;
    }

    public boolean canFireAt(ShipState.ShieldFacing targetFacing) {
        return switch (arc) {
            case FORE      -> targetFacing == ShipState.ShieldFacing.FORE;
            case AFT       -> targetFacing == ShipState.ShieldFacing.AFT;
            case PORT      -> targetFacing == ShipState.ShieldFacing.PORT;
            case STARBOARD -> targetFacing == ShipState.ShieldFacing.STARBOARD;
        };
    }

    public String getHardpointId()   { return hardpointId; }
    public String getShipId()        { return shipId; }
    public BlockPos getBlockPos()    { return BlockPos.fromLong(blockPosLong); }
    public long getBlockPosLong()    { return blockPosLong; }
    public WeaponType getWeaponType() { return weaponType; }
    public Arc getArc()              { return arc; }
    public int getPowerDraw()        { return powerDraw; }
    public int getReloadTicks()      { return reloadTicks; }
    public int getHealth()           { return health; }
    public int getCooldownRemaining() { return cooldownRemaining; }
    public void setHealth(int h)     { this.health = Math.max(0, h); }
}