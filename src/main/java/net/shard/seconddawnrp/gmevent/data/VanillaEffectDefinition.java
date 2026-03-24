package net.shard.seconddawnrp.gmevent.data;

/**
 * A registry entry for a vanilla status effect available in the env effect GUI.
 * Stored in {@code config/assets/seconddawnrp/vanilla_effects_registry.json}.
 */
public class VanillaEffectDefinition {

    private final String effectId;        // e.g. "minecraft:slowness"
    private final String displayName;     // e.g. "Slowness"
    private final int defaultAmplitude;   // 0-4
    private final int defaultDurationTicks; // e.g. 200

    public VanillaEffectDefinition(String effectId, String displayName,
                                   int defaultAmplitude, int defaultDurationTicks) {
        this.effectId             = effectId;
        this.displayName          = displayName;
        this.defaultAmplitude     = defaultAmplitude;
        this.defaultDurationTicks = defaultDurationTicks;
    }

    public String getEffectId()           { return effectId; }
    public String getDisplayName()        { return displayName; }
    public int getDefaultAmplitude()      { return defaultAmplitude; }
    public int getDefaultDurationTicks()  { return defaultDurationTicks; }

    /** Format used in EnvironmentalEffectEntry.vanillaEffects list. */
    public String toEffectString(int amplitude, int durationTicks) {
        String[] parts = effectId.split(":", 2);
        return (parts.length == 2 ? effectId : "minecraft:" + effectId)
                + ":" + amplitude + ":" + durationTicks;
    }
}