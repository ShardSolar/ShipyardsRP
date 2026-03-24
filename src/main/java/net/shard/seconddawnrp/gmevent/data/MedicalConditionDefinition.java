package net.shard.seconddawnrp.gmevent.data;

/**
 * A registry entry for a medical condition available in the env effect GUI.
 * Stored in {@code config/assets/seconddawnrp/medical_conditions_registry.json}.
 */
public class MedicalConditionDefinition {

    private final String conditionId;    // e.g. "radiation_sickness"
    private final String displayName;    // e.g. "Radiation Sickness"
    private final String defaultSeverity; // e.g. "Moderate"
    private final String description;    // shown in GUI tooltip

    public MedicalConditionDefinition(String conditionId, String displayName,
                                      String defaultSeverity, String description) {
        this.conditionId     = conditionId;
        this.displayName     = displayName;
        this.defaultSeverity = defaultSeverity;
        this.description     = description;
    }

    public String getConditionId()     { return conditionId; }
    public String getDisplayName()     { return displayName; }
    public String getDefaultSeverity() { return defaultSeverity; }
    public String getDescription()     { return description; }
}