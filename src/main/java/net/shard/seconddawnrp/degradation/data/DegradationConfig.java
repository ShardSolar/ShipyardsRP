package net.shard.seconddawnrp.degradation.data;

/**
 * Runtime configuration for the engineering degradation system.
 *
 * <p>Loaded from {@code config/assets/seconddawnrp/degradation_config.json}
 * on server start. All durations are in real-time milliseconds.
 */
public class DegradationConfig {

    /** Milliseconds between each health drain tick per component. Default: 5 minutes. */
    private final long drainIntervalMs;

    /** Health points drained per tick at NOMINAL status. */
    private final int drainPerTickNominal;

    /** Health points drained per tick at DEGRADED status (accelerated). */
    private final int drainPerTickDegraded;

    /** Health points drained per tick at CRITICAL status (further accelerated). */
    private final int drainPerTickCritical;

    /**
     * Minimum milliseconds between auto-generated repair tasks for the same
     * component. Prevents task spam when a component sits at CRITICAL for
     * a long time. Default: 30 minutes.
     */
    private final long taskGenerationCooldownMs;

    /**
     * Health restored per repair interaction (player uses Engineering PAD
     * on the block with an active repair task for that component).
     */
    private final int healthPerRepair;

    /** How many blocks away players receive particle warning pulses. */
    private final int warningRadiusBlocks;

    /**
     * Server ticks between DEGRADED warning pulses broadcast to nearby players.
     * Default: 1200 ticks (60 seconds).
     */
    private final int warningPulseTicksDegraded;

    /**
     * Server ticks between CRITICAL warning pulses.
     * Default: 400 ticks (20 seconds).
     */
    private final int warningPulseTicksCritical;
    private final String defaultRepairItemId;
    private final int defaultRepairItemCount;

    public DegradationConfig(
            long drainIntervalMs,
            int drainPerTickNominal,
            int drainPerTickDegraded,
            int drainPerTickCritical,
            long taskGenerationCooldownMs,
            int healthPerRepair,
            int warningRadiusBlocks,
            int warningPulseTicksDegraded,
            int warningPulseTicksCritical,
            String defaultRepairItemId,
            int defaultRepairItemCount) {
        this.drainIntervalMs = drainIntervalMs;
        this.drainPerTickNominal = drainPerTickNominal;
        this.drainPerTickDegraded = drainPerTickDegraded;
        this.drainPerTickCritical = drainPerTickCritical;
        this.taskGenerationCooldownMs = taskGenerationCooldownMs;
        this.healthPerRepair = healthPerRepair;
        this.warningRadiusBlocks = warningRadiusBlocks;
        this.warningPulseTicksDegraded = warningPulseTicksDegraded;
        this.warningPulseTicksCritical = warningPulseTicksCritical;
        this.defaultRepairItemId = defaultRepairItemId != null ? defaultRepairItemId : "minecraft:iron_ingot";
        this.defaultRepairItemCount = defaultRepairItemCount > 0 ? defaultRepairItemCount : 1;
    }

    public static DegradationConfig defaults() {
        return new DegradationConfig(
                5 * 60 * 1000L,      // drainIntervalMs — 5 min
                1,                   // drainPerTickNominal
                2,                   // drainPerTickDegraded
                3,                   // drainPerTickCritical
                30 * 60 * 1000L,     // taskGenerationCooldownMs — 30 min
                20,                  // healthPerRepair
                16,                  // warningRadiusBlocks
                1200,                // warningPulseTicks DEGRADED — 60s
                400,                 // warningPulseTicks CRITICAL — 20s
                "minecraft:iron_ingot", // defaultRepairItemId
                1                    // defaultRepairItemCount
        );
    }

    public long getDrainIntervalMs() { return drainIntervalMs; }
    public int getDrainPerTickNominal() { return drainPerTickNominal; }
    public int getDrainPerTickDegraded() { return drainPerTickDegraded; }
    public int getDrainPerTickCritical() { return drainPerTickCritical; }
    public long getTaskGenerationCooldownMs() { return taskGenerationCooldownMs; }
    public int getHealthPerRepair() { return healthPerRepair; }
    public int getWarningRadiusBlocks() { return warningRadiusBlocks; }
    public int getWarningPulseTicksDegraded() { return warningPulseTicksDegraded; }
    public int getWarningPulseTicksCritical() { return warningPulseTicksCritical; }
    public String getDefaultRepairItemId() { return defaultRepairItemId; }
    public int getDefaultRepairItemCount() { return defaultRepairItemCount; }
}