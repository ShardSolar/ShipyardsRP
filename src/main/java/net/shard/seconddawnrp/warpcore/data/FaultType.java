package net.shard.seconddawnrp.warpcore.data;

/**
 * The cause of a reactor fault event.
 * Used to generate appropriately-named repair tasks and log entries.
 */
public enum FaultType {

    /** Fuel level dropped below the critical threshold. */
    FUEL_DEPLETED("Fuel Depletion", "Reactor fuel levels critical — resupply required."),

    /** Resonance coil health dropped below the instability threshold. */
    COIL_DEGRADED("Coil Degradation", "Resonance coil integrity failing — replacement required."),

    /** Startup sequence failed due to degraded coil or insufficient fuel. */
    STARTUP_FAILURE("Startup Failure", "Reactor startup aborted — diagnose and reset."),

    /** Manual fault injected by GM for narrative purposes. */
    GM_INJECTED("System Fault", "Unspecified reactor fault — Engineering respond immediately."),

    /** Multiple simultaneous faults escalated to catastrophic failure. */
    CASCADING_FAILURE("Cascading Failure", "Catastrophic reactor failure — all Engineering emergency response.");

    private final String displayName;
    private final String taskDescription;

    FaultType(String displayName, String taskDescription) {
        this.displayName = displayName;
        this.taskDescription = taskDescription;
    }

    public String getDisplayName() { return displayName; }
    public String getTaskDescription() { return taskDescription; }
}