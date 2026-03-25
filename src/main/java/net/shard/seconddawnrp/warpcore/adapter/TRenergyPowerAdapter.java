package net.shard.seconddawnrp.warpcore.adapter;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import team.reborn.energy.api.EnergyStorage;

/**
 * Power adapter for any mod using the Team Reborn Energy API.
 *
 * <p>Compatible with both Tech Reborn and Energized Power. Reads energy from
 * the warp core controller block's OWN faces — cables must be physically
 * connected to the controller block. No remote source position is stored.
 *
 * <p>If any face of the controller exposes an energy storage, that storage
 * is used. Returns 0 if no cable is connected or no energy is available.
 *
 * <p>Power output is mapped linearly from energy fill level (0–100%).
 * Stability is tiered: >75% fill = 100 stability, >50% = 75, >25% = 50, else 25.
 */
public class TRenergyPowerAdapter implements PowerSourceAdapter {

    private final ServerWorld world;
    private final BlockPos controllerPos;

    private int notFoundLogCooldown = 0;

    public TRenergyPowerAdapter(ServerWorld world, BlockPos controllerPos) {
        this.world         = world;
        this.controllerPos = controllerPos;
    }

    @Override
    public int getPowerOutput() {
        EnergyStorage storage = findStorage();
        if (storage == null) return 0;
        long capacity = storage.getCapacity();
        long amount   = storage.getAmount();
        if (capacity <= 0) return 0;
        // TR cables report Integer.MAX_VALUE as capacity — use a 100k E reference max instead
        if (capacity > 1_000_000L) {
            // Scale: treat 100k E = 100% power output
            return (int) Math.min(100, amount * 100 / 100_000L);
        }
        return (int) Math.min(100, amount * 100 / capacity);
    }

    @Override
    public int getFuelLevelPercent() {
        EnergyStorage storage = findStorage();
        if (storage == null) return 0;
        long capacity = storage.getCapacity();
        long amount   = storage.getAmount();
        if (capacity <= 0) return 0;
        if (capacity > 1_000_000L) {
            return (int) Math.min(100, amount * 100 / 100_000L);
        }
        return (int) Math.min(100, amount * 100 / capacity);
    }

    @Override
    public int getStability() {
        int pct = getFuelLevelPercent();
        if (pct >= 75) return 100;
        if (pct >= 50) return 75;
        if (pct >= 25) return 50;
        if (pct > 0)   return 25;
        return 0;
    }

    @Override
    public String getPrimaryFuelLabel() { return "Energy"; }

    /** Stored energy in native TR units. */
    public long getStoredEnergy() {
        EnergyStorage s = findStorage();
        return s != null ? s.getAmount() : 0;
    }

    /** Max capacity — capped at 100k for display when cable reports Integer.MAX_VALUE. */
    public long getMaxCapacity() {
        EnergyStorage s = findStorage();
        if (s == null) return 0;
        return s.getCapacity() > 1_000_000L ? 100_000L : s.getCapacity();
    }

    /**
     * Checks all six adjacent blocks for a TREnergy storage exposed toward the controller.
     * Cables expose energy on THEIR own block, on the face pointing toward the consumer.
     * So we query: does the block next to me expose energy on the face pointing back at me?
     */
    private EnergyStorage findStorage() {
        if (world == null || controllerPos == null) return null;
        for (Direction dir : Direction.values()) {
            BlockPos adjacent = controllerPos.offset(dir);
            // Query the adjacent block's face that points back toward the controller
            EnergyStorage storage = EnergyStorage.SIDED.find(world, adjacent, dir.getOpposite());
            if (storage != null) return storage;
        }
        if (notFoundLogCooldown <= 0) {
            System.out.println("[SecondDawnRP] WarpCore at " + controllerPos
                    + ": no TREnergy cable found on adjacent faces — connect TR/EP cables.");
            notFoundLogCooldown = 200;
        } else {
            notFoundLogCooldown--;
        }
        return null;
    }
}