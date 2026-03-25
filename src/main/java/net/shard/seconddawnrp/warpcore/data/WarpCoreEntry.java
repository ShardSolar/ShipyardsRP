package net.shard.seconddawnrp.warpcore.data;

import java.util.UUID;

/**
 * Persisted state of a registered warp core controller block.
 *
 * <p>Multiple warp cores are supported. Each is identified by a unique
 * {@code entryId} generated at registration time.
 *
 * <p>TREnergy (Tech Reborn / Energized Power) is read from the controller
 * block's own faces — cables connect directly to the controller. No remote
 * source position is stored.
 */
public class WarpCoreEntry {

    private final String entryId;       // unique identifier, e.g. "wc_3fc4_69d1"
    private final String worldKey;
    private final long blockPosLong;

    private ReactorState state;
    private int fuelRods;
    private int startupTicksRemaining;
    private int shutdownTicksRemaining;
    private long lastFuelDrainMs;
    private long lastFaultTaskMs;
    private int currentPowerOutput;
    private final java.util.List<String> resonanceCoilIds;
    private UUID registeredByUuid;

    public WarpCoreEntry(
            String entryId,
            String worldKey,
            long blockPosLong,
            ReactorState state,
            int fuelRods,
            long lastFuelDrainMs,
            long lastFaultTaskMs,
            int currentPowerOutput,
            java.util.List<String> resonanceCoilIds,
            UUID registeredByUuid) {
        this.entryId = entryId;
        this.worldKey = worldKey;
        this.blockPosLong = blockPosLong;
        this.state = state;
        this.fuelRods = fuelRods;
        this.startupTicksRemaining = 0;
        this.shutdownTicksRemaining = 0;
        this.lastFuelDrainMs = lastFuelDrainMs;
        this.lastFaultTaskMs = lastFaultTaskMs;
        this.currentPowerOutput = currentPowerOutput;
        this.resonanceCoilIds = resonanceCoilIds != null ? new java.util.ArrayList<>(resonanceCoilIds) : new java.util.ArrayList<>();
        this.registeredByUuid = registeredByUuid;
    }

    public void setState(ReactorState s)                { this.state = s; }
    public void setFuelRods(int n)                      { this.fuelRods = Math.max(0, n); }
    public void setStartupTicksRemaining(int t)         { this.startupTicksRemaining = Math.max(0, t); }
    public void setShutdownTicksRemaining(int t)        { this.shutdownTicksRemaining = Math.max(0, t); }
    public void setLastFuelDrainMs(long ms)             { this.lastFuelDrainMs = ms; }
    public void setLastFaultTaskMs(long ms)             { this.lastFaultTaskMs = ms; }
    public void setCurrentPowerOutput(int p)            { this.currentPowerOutput = Math.max(0, Math.min(100, p)); }
    public void addResonanceCoil(String id) { if (!resonanceCoilIds.contains(id)) resonanceCoilIds.add(id); }
    public void removeResonanceCoil(String id) { resonanceCoilIds.remove(id); }

    public String getEntryId()                  { return entryId; }
    public String getWorldKey()                 { return worldKey; }
    public long getBlockPosLong()               { return blockPosLong; }
    public ReactorState getState()              { return state; }
    public int getFuelRods()                    { return fuelRods; }
    public int getStartupTicksRemaining()       { return startupTicksRemaining; }
    public int getShutdownTicksRemaining()      { return shutdownTicksRemaining; }
    public long getLastFuelDrainMs()            { return lastFuelDrainMs; }
    public long getLastFaultTaskMs()            { return lastFaultTaskMs; }
    public int getCurrentPowerOutput()          { return currentPowerOutput; }
    public java.util.List<String> getResonanceCoilIds() { return java.util.Collections.unmodifiableList(resonanceCoilIds); }
    public UUID getRegisteredByUuid()           { return registeredByUuid; }
}