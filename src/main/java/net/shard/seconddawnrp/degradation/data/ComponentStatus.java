package net.shard.seconddawnrp.degradation.data;

public enum ComponentStatus {

    /** Health 76–100. Fully functional. */
    NOMINAL,

    /** Health 51–75. Warning state. Still functional. */
    DEGRADED,

    /** Health 1–50. Functionally locked, repair required. */
    CRITICAL,

    /** Health 0 or block physically missing. Fully failed/offline. */
    OFFLINE;

    public static ComponentStatus fromHealth(int health) {
        if (health <= 0) return OFFLINE;
        if (health <= 50) return CRITICAL;
        if (health <= 75) return DEGRADED;
        return NOMINAL;
    }
}