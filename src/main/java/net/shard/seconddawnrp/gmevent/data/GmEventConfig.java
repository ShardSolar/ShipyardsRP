package net.shard.seconddawnrp.gmevent.data;

public class GmEventConfig {

    // Global defaults — apply to all event mobs
    private boolean preventSunlightDamage = true;
    private boolean preventNaturalDespawn = true;
    private boolean preventSuffocation    = false;
    private boolean preventDrowning       = false;
    private boolean preventFallDamage     = false;

    public GmEventConfig() {}

    public boolean isPreventSunlightDamage() { return preventSunlightDamage; }
    public boolean isPreventNaturalDespawn() { return preventNaturalDespawn; }
    public boolean isPreventSuffocation()    { return preventSuffocation; }
    public boolean isPreventDrowning()       { return preventDrowning; }
    public boolean isPreventFallDamage()     { return preventFallDamage; }

    public void setPreventSunlightDamage(boolean v) { preventSunlightDamage = v; }
    public void setPreventNaturalDespawn(boolean v)  { preventNaturalDespawn = v; }
    public void setPreventSuffocation(boolean v)     { preventSuffocation = v; }
    public void setPreventDrowning(boolean v)        { preventDrowning = v; }
    public void setPreventFallDamage(boolean v)      { preventFallDamage = v; }
}