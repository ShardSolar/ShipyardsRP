package net.shard.seconddawnrp.warpcore.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/**
 * Block entity for the Warp Core Controller.
 * Holds an energy buffer that the reactor fills each tick.
 * Cables extract from this buffer via EnergyStorage.SIDED.
 */
public class WarpCoreControllerBlockEntity extends BlockEntity {

    /**
     * Set by ModBlocks.register() immediately after BlockEntityType creation.
     * Used by createBlockEntity() in WarpCoreControllerBlock.
     */
    public static BlockEntityType<WarpCoreControllerBlockEntity> TYPE;

    public final SimpleEnergyStorage energyStorage;

    public WarpCoreControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        // Use hardcoded defaults matching WarpCoreConfig.defaults()
        // Config values are read in fillBuffer() each tick instead
        long bufferSize = 20480L;
        long maxExtract = 2048L;

        this.energyStorage = new SimpleEnergyStorage(bufferSize, 0, maxExtract) {
            @Override
            protected void onFinalCommit() {
                markDirty();
            }
        };
    }

    /**
     * Convenience constructor used by createBlockEntity() — pulls from static TYPE.
     * Only called after ModBlocks.register() sets TYPE.
     */
    public WarpCoreControllerBlockEntity(BlockPos pos, BlockState state) {
        this(TYPE, pos, state);
    }

    /**
     * Called each server tick by WarpCoreService to fill the buffer.
     * @param powerOutputPercent 0-100
     */
    public void fillBuffer(int powerOutputPercent) {
        long maxOutput = 2048L;
        try {
            if (SecondDawnRP.WARP_CORE_SERVICE != null) {
                maxOutput = SecondDawnRP.WARP_CORE_SERVICE.getConfig().getMaxEnergyOutputPerTick();
            }
        } catch (Throwable ignored) {}

        long toAdd = maxOutput * powerOutputPercent / 100L;
        if (toAdd <= 0) return;

        long space = energyStorage.getCapacity() - energyStorage.getAmount();
        long actual = Math.min(toAdd, space);
        if (actual > 0) {
            energyStorage.amount += actual;
            markDirty();
        }
    }

    public long getStoredEnergy()  { return energyStorage.getAmount(); }
    public long getCapacity()      { return energyStorage.getCapacity(); }

    // Buffer intentionally not persisted — resets on restart, reactor refills immediately
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
    }
}