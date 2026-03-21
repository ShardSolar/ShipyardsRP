package net.shard.seconddawnrp.gmevent.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EncounterTemplate {

    private String id;
    private String displayName;
    private String mobTypeId;
    private double maxHealth;
    private double armor;
    private int totalSpawnCount;
    private int maxActiveAtOnce;
    private int spawnRadiusBlocks;
    private int spawnIntervalTicks;
    private SpawnBehaviour spawnBehaviour;
    private List<String> statusEffects;
    private List<String> heldItems;
    private List<String> drops;

    public EncounterTemplate() {
        this.statusEffects = new ArrayList<>();
        this.heldItems = new ArrayList<>();
        this.drops = new ArrayList<>();
    }

    public EncounterTemplate(
            String id,
            String displayName,
            String mobTypeId,
            double maxHealth,
            double armor,
            int totalSpawnCount,
            int maxActiveAtOnce,
            int spawnRadiusBlocks,
            int spawnIntervalTicks,
            SpawnBehaviour spawnBehaviour,
            List<String> statusEffects,
            List<String> heldItems,
            List<String> drops
    ) {
        this.id               = Objects.requireNonNull(id, "id");
        this.displayName      = Objects.requireNonNull(displayName, "displayName");
        this.mobTypeId        = Objects.requireNonNull(mobTypeId, "mobTypeId");
        this.maxHealth        = maxHealth > 0 ? maxHealth : 20.0;
        this.armor            = Math.max(0, armor);
        this.totalSpawnCount  = Math.max(1, totalSpawnCount);
        this.maxActiveAtOnce  = Math.max(1, maxActiveAtOnce);
        this.spawnRadiusBlocks= Math.max(1, spawnRadiusBlocks);
        this.spawnIntervalTicks = Math.max(1, spawnIntervalTicks);
        this.spawnBehaviour   = spawnBehaviour != null ? spawnBehaviour : SpawnBehaviour.INSTANT;
        this.statusEffects    = statusEffects != null ? new ArrayList<>(statusEffects) : new ArrayList<>();
        this.heldItems        = heldItems != null ? new ArrayList<>(heldItems) : new ArrayList<>();
        this.drops            = drops != null ? new ArrayList<>(drops) : new ArrayList<>();
    }

    public String getId()                  { return id; }
    public String getDisplayName()         { return displayName; }
    public String getMobTypeId()           { return mobTypeId; }
    public double getMaxHealth()           { return maxHealth; }
    public double getArmor()               { return armor; }
    public int getTotalSpawnCount()        { return totalSpawnCount; }
    public int getMaxActiveAtOnce()        { return maxActiveAtOnce; }
    public int getSpawnRadiusBlocks()      { return spawnRadiusBlocks; }
    public int getSpawnIntervalTicks()     { return spawnIntervalTicks; }
    public SpawnBehaviour getSpawnBehaviour() { return spawnBehaviour; }
    public List<String> getStatusEffects() { return statusEffects; }
    public List<String> getHeldItems()     { return heldItems; }
    public List<String> getDrops()         { return drops; }

    public void setDisplayName(String displayName)  { this.displayName = displayName; }
    public void setMobTypeId(String mobTypeId)      { this.mobTypeId = mobTypeId; }
    public void setMaxHealth(double maxHealth)      { this.maxHealth = maxHealth; }
    public void setArmor(double armor)              { this.armor = armor; }
    public void setTotalSpawnCount(int count)       { this.totalSpawnCount = Math.max(1, count); }
    public void setMaxActiveAtOnce(int max)         { this.maxActiveAtOnce = Math.max(1, max); }
    public void setSpawnRadiusBlocks(int r)         { this.spawnRadiusBlocks = Math.max(1, r); }
    public void setSpawnIntervalTicks(int t)        { this.spawnIntervalTicks = Math.max(1, t); }
    public void setSpawnBehaviour(SpawnBehaviour b) { this.spawnBehaviour = b; }
    public void setStatusEffects(List<String> e)    { this.statusEffects = new ArrayList<>(e); }
    public void setHeldItems(List<String> i)        { this.heldItems = new ArrayList<>(i); }
    public void setDrops(List<String> d)            { this.drops = new ArrayList<>(d); }

    // Per-template overrides — null means "use global config"
    private Boolean preventSunlightDamage = null;
    private Boolean preventNaturalDespawn = null;
    private Boolean preventSuffocation    = null;
    private Boolean preventDrowning       = null;
    private Boolean preventFallDamage     = null;

    public Boolean getPreventSunlightDamage() { return preventSunlightDamage; }
    public Boolean getPreventNaturalDespawn() { return preventNaturalDespawn; }
    public Boolean getPreventSuffocation()    { return preventSuffocation; }
    public Boolean getPreventDrowning()       { return preventDrowning; }
    public Boolean getPreventFallDamage()     { return preventFallDamage; }

    public void setPreventSunlightDamage(Boolean v) { preventSunlightDamage = v; }
    public void setPreventNaturalDespawn(Boolean v)  { preventNaturalDespawn = v; }
    public void setPreventSuffocation(Boolean v)     { preventSuffocation = v; }
    public void setPreventDrowning(Boolean v)        { preventDrowning = v; }
    public void setPreventFallDamage(Boolean v)      { preventFallDamage = v; }

    // Resolve against global config — template overrides win if set
    public boolean resolvePreventSunlight(GmEventConfig cfg) {
        return preventSunlightDamage != null ? preventSunlightDamage : cfg.isPreventSunlightDamage();
    }
    public boolean resolvePreventDespawn(GmEventConfig cfg) {
        return preventNaturalDespawn != null ? preventNaturalDespawn : cfg.isPreventNaturalDespawn();
    }
    public boolean resolvePreventSuffocation(GmEventConfig cfg) {
        return preventSuffocation != null ? preventSuffocation : cfg.isPreventSuffocation();
    }
    public boolean resolvePreventDrowning(GmEventConfig cfg) {
        return preventDrowning != null ? preventDrowning : cfg.isPreventDrowning();
    }
    public boolean resolvePreventFallDamage(GmEventConfig cfg) {
        return preventFallDamage != null ? preventFallDamage : cfg.isPreventFallDamage();
    }
}