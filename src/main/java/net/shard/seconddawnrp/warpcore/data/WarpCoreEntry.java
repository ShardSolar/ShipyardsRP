package net.shard.seconddawnrp.warpcore.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Persisted state of a registered warp core controller block.
 *
 * V15: adds optional shipId — binds the warp core to a registered ship.
 * When set, the Engineering Pad only shows this core to players standing
 * on that ship. Null = legacy / unbound (shown to all, backward-compatible).
 */
public class WarpCoreEntry {

    private final String entryId;
    private final String worldKey;
    private final long blockPosLong;

    private ReactorState state;
    private int fuelRods;
    private int startupTicksRemaining;
    private int shutdownTicksRemaining;
    private long lastFuelDrainMs;
    private long lastFaultTaskMs;
    private int currentPowerOutput;
    private final List<String> resonanceCoilIds;
    private UUID registeredByUuid;

    /** Optional ship binding. Null = unbound (shown regardless of ship context). */
    private String shipId;

    // ── Full constructor (V15) ────────────────────────────────────────────────

    public WarpCoreEntry(
            String entryId,
            String worldKey,
            long blockPosLong,
            ReactorState state,
            int fuelRods,
            long lastFuelDrainMs,
            long lastFaultTaskMs,
            int currentPowerOutput,
            List<String> resonanceCoilIds,
            UUID registeredByUuid,
            String shipId
    ) {
        this.entryId = entryId;
        this.worldKey = worldKey;
        this.blockPosLong = blockPosLong;
        this.state = state;
        this.fuelRods = Math.max(0, fuelRods);
        this.startupTicksRemaining = 0;
        this.shutdownTicksRemaining = 0;
        this.lastFuelDrainMs = lastFuelDrainMs;
        this.lastFaultTaskMs = lastFaultTaskMs;
        this.currentPowerOutput = Math.max(0, Math.min(100, currentPowerOutput));
        this.resonanceCoilIds = resonanceCoilIds != null
                ? new ArrayList<>(resonanceCoilIds) : new ArrayList<>();
        this.registeredByUuid = registeredByUuid;
        this.shipId = shipId;
    }

    /** Legacy constructor without shipId — shipId defaults to null. */
    public WarpCoreEntry(
            String entryId,
            String worldKey,
            long blockPosLong,
            ReactorState state,
            int fuelRods,
            long lastFuelDrainMs,
            long lastFaultTaskMs,
            int currentPowerOutput,
            List<String> resonanceCoilIds,
            UUID registeredByUuid
    ) {
        this(entryId, worldKey, blockPosLong, state, fuelRods,
                lastFuelDrainMs, lastFaultTaskMs, currentPowerOutput,
                resonanceCoilIds, registeredByUuid, null);
    }

    // ── Ship binding ──────────────────────────────────────────────────────────

    public String getShipId()             { return shipId; }
    public boolean hasShipBinding()       { return shipId != null; }
    public void setShipId(String shipId)  { this.shipId = shipId; }

    // ── Mutators ──────────────────────────────────────────────────────────────

    public void setState(ReactorState state)                           { this.state = state; }
    public void setFuelRods(int fuelRods)                              { this.fuelRods = Math.max(0, fuelRods); }
    public void setStartupTicksRemaining(int t)                        { this.startupTicksRemaining = Math.max(0, t); }
    public void setShutdownTicksRemaining(int t)                       { this.shutdownTicksRemaining = Math.max(0, t); }
    public void setLastFuelDrainMs(long ms)                            { this.lastFuelDrainMs = ms; }
    public void setLastFaultTaskMs(long ms)                            { this.lastFaultTaskMs = ms; }
    public void setCurrentPowerOutput(int p)                           { this.currentPowerOutput = Math.max(0, Math.min(100, p)); }
    public void setRegisteredByUuid(UUID uuid)                         { this.registeredByUuid = uuid; }
    public void addResonanceCoil(String id)                            { if (id != null && !id.isBlank() && !resonanceCoilIds.contains(id)) resonanceCoilIds.add(id); }
    public void removeResonanceCoil(String id)                         { resonanceCoilIds.remove(id); }
    public void clearResonanceCoils()                                  { resonanceCoilIds.clear(); }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getEntryId()                          { return entryId; }
    public String getWorldKey()                         { return worldKey; }
    public long getBlockPosLong()                       { return blockPosLong; }
    public ReactorState getState()                      { return state; }
    public int getFuelRods()                            { return fuelRods; }
    public int getStartupTicksRemaining()               { return startupTicksRemaining; }
    public int getShutdownTicksRemaining()              { return shutdownTicksRemaining; }
    public long getLastFuelDrainMs()                    { return lastFuelDrainMs; }
    public long getLastFaultTaskMs()                    { return lastFaultTaskMs; }
    public int getCurrentPowerOutput()                  { return currentPowerOutput; }
    public List<String> getResonanceCoilIds()           { return Collections.unmodifiableList(resonanceCoilIds); }
    public UUID getRegisteredByUuid()                   { return registeredByUuid; }
}