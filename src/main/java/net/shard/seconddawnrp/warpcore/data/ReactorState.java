package net.shard.seconddawnrp.warpcore.data;

/**
 * The operational state of the warp core reactor.
 *
 * <p>State transitions:
 * <pre>
 *   OFFLINE → STARTING → ONLINE → UNSTABLE → CRITICAL → FAILED
 *                ↓          ↓
 *            OFFLINE     OFFLINE  (shutdown)
 * </pre>
 *
 * <p>FAILED is a one-way trap until Engineering manually resets via command.
 */
public enum ReactorState {

    /** Reactor is powered down. No fuel consumption. No power output. */
    OFFLINE,

    /**
     * Startup sequence in progress. Fuel consumption begins.
     * Resonance coil health checked — low coil health risks aborting to FAILED.
     * Duration configured in {@link WarpCoreConfig#getStartupDurationTicks()}.
     */
    STARTING,

    /** Normal operation. Full power output. Base fuel consumption. */
    ONLINE,

    /**
     * Output or parameters out of range. Reduced power output.
     * Accelerated fuel consumption. Fault task auto-generated.
     * Triggers if fuel drops below warning threshold or resonance coil
     * health drops below the instability threshold.
     */
    UNSTABLE,

    /**
     * Severe fault detected. Minimal power output.
     * All Engineering alerted. Fault task generated.
     * All registered degradation components drain at accelerated rate.
     */
    CRITICAL,

    /**
     * Complete shutdown due to catastrophic fault.
     * Zero power output. Emergency repair tasks generated.
     * Requires manual GM or admin reset to return to OFFLINE.
     */
    FAILED;

    public boolean isOperational() {
        return this == ONLINE || this == UNSTABLE;
    }

    public boolean isFaulted() {
        return this == CRITICAL || this == FAILED;
    }

    public boolean canStartup() {
        return this == OFFLINE;
    }

    public boolean canShutdown() {
        return this == ONLINE || this == UNSTABLE || this == STARTING;
    }
}