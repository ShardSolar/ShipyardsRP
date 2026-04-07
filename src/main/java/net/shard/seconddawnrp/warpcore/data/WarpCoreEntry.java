package net.shard.seconddawnrp.warpcore.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Persisted state of a registered warp core controller block.
 *
 * <p>Multiple warp cores are supported. Each is identified by a unique
 * {@code entryId} generated at registration time.
 *
 * <p>TREnergy is read from the controller block's own faces — cables connect
 * directly to the controller. No remote source position is stored.
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
                ? new ArrayList<>(resonanceCoilIds)
                : new ArrayList<>();
        this.registeredByUuid = registeredByUuid;
    }

    // ── Mutators ─────────────────────────────────────────────────────────────

    public void setState(ReactorState state) {
        this.state = state;
    }

    public void setFuelRods(int fuelRods) {
        this.fuelRods = Math.max(0, fuelRods);
    }

    public void setStartupTicksRemaining(int startupTicksRemaining) {
        this.startupTicksRemaining = Math.max(0, startupTicksRemaining);
    }

    public void setShutdownTicksRemaining(int shutdownTicksRemaining) {
        this.shutdownTicksRemaining = Math.max(0, shutdownTicksRemaining);
    }

    public void setLastFuelDrainMs(long lastFuelDrainMs) {
        this.lastFuelDrainMs = lastFuelDrainMs;
    }

    public void setLastFaultTaskMs(long lastFaultTaskMs) {
        this.lastFaultTaskMs = lastFaultTaskMs;
    }

    public void setCurrentPowerOutput(int currentPowerOutput) {
        this.currentPowerOutput = Math.max(0, Math.min(100, currentPowerOutput));
    }

    public void setRegisteredByUuid(UUID registeredByUuid) {
        this.registeredByUuid = registeredByUuid;
    }

    public void addResonanceCoil(String componentId) {
        if (componentId != null && !componentId.isBlank() && !resonanceCoilIds.contains(componentId)) {
            resonanceCoilIds.add(componentId);
        }
    }

    public void removeResonanceCoil(String componentId) {
        resonanceCoilIds.remove(componentId);
    }

    public void clearResonanceCoils() {
        resonanceCoilIds.clear();
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public String getEntryId() {
        return entryId;
    }

    public String getWorldKey() {
        return worldKey;
    }

    public long getBlockPosLong() {
        return blockPosLong;
    }

    public ReactorState getState() {
        return state;
    }

    public int getFuelRods() {
        return fuelRods;
    }

    public int getStartupTicksRemaining() {
        return startupTicksRemaining;
    }

    public int getShutdownTicksRemaining() {
        return shutdownTicksRemaining;
    }

    public long getLastFuelDrainMs() {
        return lastFuelDrainMs;
    }

    public long getLastFaultTaskMs() {
        return lastFaultTaskMs;
    }

    public int getCurrentPowerOutput() {
        return currentPowerOutput;
    }

    public List<String> getResonanceCoilIds() {
        return Collections.unmodifiableList(resonanceCoilIds);
    }

    public UUID getRegisteredByUuid() {
        return registeredByUuid;
    }
}