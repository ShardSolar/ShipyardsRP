package net.shard.seconddawnrp.warpcore.data;

/**
 * Runtime configuration for the warp core system.
 * Loaded from {@code config/assets/seconddawnrp/warpcore_config.json}.
 */
public class WarpCoreConfig {

    /** Ticks for the startup sequence to complete. Default: 200 (10 seconds). */
    private final int startupDurationTicks;

    /** Ticks for the shutdown sequence to complete. Default: 100 (5 seconds). */
    private final int shutdownDurationTicks;

    /** Milliseconds between fuel consumption ticks. Default: 5 minutes. */
    private final long fuelDrainIntervalMs;

    /** Fuel rods consumed per drain tick at base (ONLINE) state. */
    private final int fuelDrainPerTickBase;

    /**
     * Additional fuel rods consumed per drain tick scaled by power output.
     * Formula: total drain = base + (powerOutput / 100 * outputScaleDrain)
     */
    private final int fuelDrainOutputScale;

    /** Fuel rod count below which the reactor transitions ONLINE → UNSTABLE. */
    private final int fuelWarningThreshold;

    /** Fuel rod count below which the reactor faults to CRITICAL. */
    private final int fuelCriticalThreshold;

    /**
     * Resonance coil health (0-100) below which the reactor becomes UNSTABLE.
     * The coil is tracked as a registered degradation component.
     */
    private final int coilInstabilityThreshold;

    /**
     * Resonance coil health below which startup is blocked entirely.
     * Attempting startup with coil below this value causes STARTUP_FAILURE.
     */
    private final int coilStartupMinimumHealth;

    /** Power output (0-100) reported while ONLINE. Fed to ShipState in Phase 12. */
    private final int powerOutputNominal;

    /** Power output while UNSTABLE. */
    private final int powerOutputUnstable;

    /** Power output while CRITICAL. */
    private final int powerOutputCritical;

    /** Power output while FAILED or OFFLINE. */
    private final int powerOutputOffline;

    /**
     * Multiplier applied to all degradation component drain rates while CRITICAL.
     * e.g. 2.0 means components drain at double speed when the reactor is critical.
     */
    private final double criticalDegradationMultiplier;

    /** Cooldown in ms between auto-generated fault tasks for the same fault type. */
    private final long faultTaskCooldownMs;

    /** Maximum number of fuel rods the controller block inventory can hold. */
    private final int maxFuelRods;

    /** Peak energy output per tick at 100% power (E/tick). Default: 2048. */
    private final long maxEnergyOutputPerTick;

    /** Internal energy buffer the block entity holds before cables pull. Default: 20480 (10 ticks). */
    private final long energyBufferSize;

    /**
     * Minimum generator energy on an adjacent face required to initiate startup.
     * Startup assist — generator must be connected and have at least this much energy.
     * Set to 0 to disable startup power requirement.
     */
    private final long startupMinGeneratorEnergy;

    public WarpCoreConfig(
            int startupDurationTicks,
            int shutdownDurationTicks,
            long fuelDrainIntervalMs,
            int fuelDrainPerTickBase,
            int fuelDrainOutputScale,
            int fuelWarningThreshold,
            int fuelCriticalThreshold,
            int coilInstabilityThreshold,
            int coilStartupMinimumHealth,
            int powerOutputNominal,
            int powerOutputUnstable,
            int powerOutputCritical,
            int powerOutputOffline,
            double criticalDegradationMultiplier,
            long faultTaskCooldownMs,
            int maxFuelRods,
            long maxEnergyOutputPerTick,
            long energyBufferSize,
            long startupMinGeneratorEnergy) {
        this.startupDurationTicks = startupDurationTicks;
        this.shutdownDurationTicks = shutdownDurationTicks;
        this.fuelDrainIntervalMs = fuelDrainIntervalMs;
        this.fuelDrainPerTickBase = fuelDrainPerTickBase;
        this.fuelDrainOutputScale = fuelDrainOutputScale;
        this.fuelWarningThreshold = fuelWarningThreshold;
        this.fuelCriticalThreshold = fuelCriticalThreshold;
        this.coilInstabilityThreshold = coilInstabilityThreshold;
        this.coilStartupMinimumHealth = coilStartupMinimumHealth;
        this.powerOutputNominal = powerOutputNominal;
        this.powerOutputUnstable = powerOutputUnstable;
        this.powerOutputCritical = powerOutputCritical;
        this.powerOutputOffline = powerOutputOffline;
        this.criticalDegradationMultiplier = criticalDegradationMultiplier;
        this.faultTaskCooldownMs = faultTaskCooldownMs;
        this.maxFuelRods = maxFuelRods;
        this.maxEnergyOutputPerTick = maxEnergyOutputPerTick;
        this.energyBufferSize = energyBufferSize;
        this.startupMinGeneratorEnergy = startupMinGeneratorEnergy;
    }

    public static WarpCoreConfig defaults() {
        return new WarpCoreConfig(
                200,              // startupDurationTicks — 10s
                100,              // shutdownDurationTicks — 5s
                5 * 60 * 1000L,   // fuelDrainIntervalMs — 5 min
                1,                // fuelDrainPerTickBase
                2,                // fuelDrainOutputScale
                20,               // fuelWarningThreshold
                5,                // fuelCriticalThreshold
                35,               // coilInstabilityThreshold
                20,               // coilStartupMinimumHealth
                100,              // powerOutputNominal
                60,               // powerOutputUnstable
                25,               // powerOutputCritical
                0,                // powerOutputOffline
                2.0,              // criticalDegradationMultiplier
                30 * 60 * 1000L,  // faultTaskCooldownMs — 30 min
                64,               // maxFuelRods
                2048L,            // maxEnergyOutputPerTick
                20480L,           // energyBufferSize (10 ticks)
                1000L             // startupMinGeneratorEnergy
        );
    }

    public int getStartupDurationTicks() { return startupDurationTicks; }
    public int getShutdownDurationTicks() { return shutdownDurationTicks; }
    public long getFuelDrainIntervalMs() { return fuelDrainIntervalMs; }
    public int getFuelDrainPerTickBase() { return fuelDrainPerTickBase; }
    public int getFuelDrainOutputScale() { return fuelDrainOutputScale; }
    public int getFuelWarningThreshold() { return fuelWarningThreshold; }
    public int getFuelCriticalThreshold() { return fuelCriticalThreshold; }
    public int getCoilInstabilityThreshold() { return coilInstabilityThreshold; }
    public int getCoilStartupMinimumHealth() { return coilStartupMinimumHealth; }
    public int getPowerOutputNominal() { return powerOutputNominal; }
    public int getPowerOutputUnstable() { return powerOutputUnstable; }
    public int getPowerOutputCritical() { return powerOutputCritical; }
    public int getPowerOutputOffline() { return powerOutputOffline; }
    public double getCriticalDegradationMultiplier() { return criticalDegradationMultiplier; }
    public long getFaultTaskCooldownMs() { return faultTaskCooldownMs; }
    public int getMaxFuelRods() { return maxFuelRods; }
    public long getMaxEnergyOutputPerTick() { return maxEnergyOutputPerTick; }
    public long getEnergyBufferSize() { return energyBufferSize; }
    public long getStartupMinGeneratorEnergy() { return startupMinGeneratorEnergy; }
}