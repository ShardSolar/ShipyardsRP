package net.shard.seconddawnrp.degradation.data;

public enum ComponentStatus {

    /** Health 76–100. No drain acceleration. No task. */
    NOMINAL,

    /** Health 51–75. Particle warnings pulse every 60s. */
    DEGRADED,

    /** Health 26–50. Particles every 20s, auto-repair task generated. */
    CRITICAL,

    /** Health 0–25. Offline: block emits constant smoke, all actions locked. */
    OFFLINE;

    public static ComponentStatus fromHealth(int health) {
        if (health > 75) return NOMINAL;
        if (health > 50) return DEGRADED;
        if (health > 25) return CRITICAL;
        return OFFLINE;
    }
}