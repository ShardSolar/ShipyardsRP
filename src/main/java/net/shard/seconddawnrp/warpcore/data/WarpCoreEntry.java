package net.shard.seconddawnrp.warpcore.data;

import java.util.UUID;

/**
 * Persisted state of a registered warp core controller block.
 *
 * <p>There is intentionally only one warp core per server — the system
 * enforces this at registration time. The entry is keyed by world + position.
 *
 * <p>The resonance coil is tracked as a {@link net.shard.seconddawnrp.degradation.data.ComponentEntry}
 * in the degradation system. Its component ID is stored here for cross-system lookup.
 */
public class WarpCoreEntry {

    private final String worldKey;
    private final long blockPosLong;

    private ReactorState state;
    private int fuelRods;
    private int startupTicksRemaining;
    private int shutdownTicksRemaining;
    private long lastFuelDrainMs;
    private long lastFaultTaskMs;
    private int currentPowerOutput;

    /**
     * Component ID of the resonance coil registered in the degradation system.
     * Null until a coil component is linked via {@code /warpcore linkcoil}.
     */
    private String resonanceCoilComponentId;

    private UUID registeredByUuid;

    public WarpCoreEntry(
            String worldKey,
            long blockPosLong,
            ReactorState state,
            int fuelRods,
            long lastFuelDrainMs,
            long lastFaultTaskMs,
            int currentPowerOutput,
            String resonanceCoilComponentId,
            UUID registeredByUuid) {
        this.worldKey = worldKey;
        this.blockPosLong = blockPosLong;
        this.state = state;
        this.fuelRods = fuelRods;
        this.startupTicksRemaining = 0;
        this.shutdownTicksRemaining = 0;
        this.lastFuelDrainMs = lastFuelDrainMs;
        this.lastFaultTaskMs = lastFaultTaskMs;
        this.currentPowerOutput = currentPowerOutput;
        this.resonanceCoilComponentId = resonanceCoilComponentId;
        this.registeredByUuid = registeredByUuid;
    }

    // ── Mutators ──────────────────────────────────────────────────────────────

    public void setState(ReactorState state) { this.state = state; }
    public void setFuelRods(int fuelRods) { this.fuelRods = Math.max(0, fuelRods); }
    public void setStartupTicksRemaining(int ticks) { this.startupTicksRemaining = Math.max(0, ticks); }
    public void setShutdownTicksRemaining(int ticks) { this.shutdownTicksRemaining = Math.max(0, ticks); }
    public void setLastFuelDrainMs(long ms) { this.lastFuelDrainMs = ms; }
    public void setLastFaultTaskMs(long ms) { this.lastFaultTaskMs = ms; }
    public void setCurrentPowerOutput(int power) { this.currentPowerOutput = Math.max(0, Math.min(100, power)); }
    public void setResonanceCoilComponentId(String id) { this.resonanceCoilComponentId = id; }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getWorldKey() { return worldKey; }
    public long getBlockPosLong() { return blockPosLong; }
    public ReactorState getState() { return state; }
    public int getFuelRods() { return fuelRods; }
    public int getStartupTicksRemaining() { return startupTicksRemaining; }
    public int getShutdownTicksRemaining() { return shutdownTicksRemaining; }
    public long getLastFuelDrainMs() { return lastFuelDrainMs; }
    public long getLastFaultTaskMs() { return lastFaultTaskMs; }
    public int getCurrentPowerOutput() { return currentPowerOutput; }
    public String getResonanceCoilComponentId() { return resonanceCoilComponentId; }
    public UUID getRegisteredByUuid() { return registeredByUuid; }
}