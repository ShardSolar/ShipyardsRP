package net.shard.seconddawnrp.warpcore.adapter;

/**
 * Adapter interface for reading power output from an underlying power source.
 *
 * <p>In Phase 5, the {@link StandalonePowerAdapter} is the only active
 * implementation — it derives all values from the warp core's own fuel
 * and coil state.
 *
 * <p>In future phases, {@link MekanismPowerAdapter} and
 * {@link CreatePowerAdapter} will read from their respective mod APIs
 * and map values to the standardised 0-100 scale expected by the
 * {@link net.shard.seconddawnrp.warpcore.service.WarpCoreService}.
 *
 * <p>All adapters must be safe to call from the server tick thread.
 * Adapters must never throw — return 0 / OFFLINE on any error.
 */
public interface PowerSourceAdapter {

    /**
     * Current power output on a 0–100 scale.
     * 100 = full rated output. 0 = offline or failed.
     */
    int getPowerOutput();

    /**
     * Primary fuel level as a percentage 0–100.
     * For the standalone adapter this is fuel rods remaining / max.
     * For tech mod adapters this maps to the mod's primary fuel resource.
     */
    int getFuelLevelPercent();

    /**
     * Overall stability on a 0–100 scale.
     * 100 = fully stable. Below 50 = UNSTABLE. Below 25 = CRITICAL.
     * Derived from coil health, fuel level, and any external faults.
     */
    int getStability();

    /**
     * Human-readable label for the primary fuel resource.
     * Shown on the Engineering Console — never hardcode lore names here.
     * Default: "Primary Fuel"
     */
    default String getPrimaryFuelLabel() {
        return "Primary Fuel";
    }

    /**
     * Human-readable label for the secondary resource if applicable.
     * Return null if this adapter has no secondary resource.
     */
    default String getSecondaryResourceLabel() {
        return null;
    }

    /**
     * Secondary resource level as a percentage 0–100.
     * Return 0 if this adapter has no secondary resource.
     */
    default int getSecondaryResourcePercent() {
        return 0;
    }
}