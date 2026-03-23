package net.shard.seconddawnrp.warpcore.adapter;

import net.shard.seconddawnrp.warpcore.data.WarpCoreConfig;
import net.shard.seconddawnrp.warpcore.data.WarpCoreEntry;

/**
 * Standalone power adapter for Phase 5.
 *
 * <p>Derives all values directly from the {@link WarpCoreEntry} state —
 * fuel rod count, reactor state, and resonance coil health read from
 * the degradation system. No external mod dependency.
 *
 * <p>When Create or Mekanism adapters are activated in a later phase,
 * this adapter remains available as a fallback for servers without those mods.
 */
public class StandalonePowerAdapter implements PowerSourceAdapter {

    private final WarpCoreEntry entry;
    private final WarpCoreConfig config;
    private int coilHealth = 100; // updated each tick by WarpCoreService

    public StandalonePowerAdapter(WarpCoreEntry entry, WarpCoreConfig config) {
        this.entry = entry;
        this.config = config;
    }

    /** Called by WarpCoreService each tick to keep coil health current. */
    public void updateCoilHealth(int health) {
        this.coilHealth = Math.max(0, Math.min(100, health));
    }

    @Override
    public int getPowerOutput() {
        return entry.getCurrentPowerOutput();
    }

    @Override
    public int getFuelLevelPercent() {
        if (config.getMaxFuelRods() <= 0) return 0;
        return Math.min(100, entry.getFuelRods() * 100 / config.getMaxFuelRods());
    }

    @Override
    public int getStability() {
        return switch (entry.getState()) {
            case ONLINE    -> Math.min(100, Math.min(coilHealth, getFuelLevelPercent()));
            case UNSTABLE  -> Math.min(60, Math.min(coilHealth, getFuelLevelPercent()));
            case CRITICAL  -> Math.min(25, coilHealth);
            case STARTING  -> 50;
            default        -> 0;
        };
    }

    @Override
    public String getPrimaryFuelLabel() {
        return "Fuel Rods";
    }
}