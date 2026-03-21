package net.shard.seconddawnrp.gmevent.data;

public enum GmSkill {
    WEAKNESS_AOE("Weakness AOE",      "Pulses weakness to nearby players"),
    KNOCKBACK_STRIKE("Knockback Strike", "Extra knockback on hit"),
    REGENERATION("Regeneration",      "Mob slowly heals over time"),
    FIRE_AURA("Fire Aura",            "Sets nearby players on fire"),
    ENRAGE("Enrage",                  "Speed + strength below 50% HP"),
    SHIELD_ALLIES("Shield Allies",    "Nearby mobs take reduced damage"),
    TELEPORT_BEHIND("Teleport Behind","Teleports behind attacker on hit"),
    SUMMON_ADDS("Summon Adds",        "Spawns smaller mobs on death");

    private final String displayName;
    private final String description;

    GmSkill(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    // Skills are stored as "skill:SKILL_NAME" in the statusEffects list
    public String toStorageKey() { return "skill:" + this.name(); }

    public static GmSkill fromStorageKey(String key) {
        if (key == null || !key.startsWith("skill:")) return null;
        try { return GmSkill.valueOf(key.substring(6)); }
        catch (Exception e) { return null; }
    }
}