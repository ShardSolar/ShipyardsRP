package net.shard.seconddawnrp.tactical.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defines base stats for a ship class.
 * Loaded from data/seconddawnrp/ships/*.json at startup.
 */
public class ShipClassDefinition {

    private String classId;
    private String displayName;
    private int hullMax;
    private int shieldMax;
    private int shieldRegenRate;
    private float maxSpeed;
    private float turnRate;
    private int powerCapacity;
    private int phaserDamage;
    private int phaserCooldown;
    private int torpedoDamage;
    private int sensorRange;
    private Map<Integer, Integer> warpSpeedThresholds; // warp factor -> required power
    private List<String> damageZones;

    // Default constructor for Gson
    public ShipClassDefinition() {
        this.warpSpeedThresholds = new HashMap<>();
        this.damageZones = new ArrayList<>();
    }

    // ── Registry ──────────────────────────────────────────────────────────────

    private static final Map<String, ShipClassDefinition> REGISTRY =
            new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void loadAll(Path dataDir) {
        REGISTRY.clear();
        Path shipsDir = dataDir.resolve("seconddawnrp/ships");
        if (!Files.exists(shipsDir)) {
            try { Files.createDirectories(shipsDir); } catch (IOException ignored) {}
            writeDefaults(shipsDir);
        }
        try {
            Files.list(shipsDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            String json = Files.readString(p);
                            ShipClassDefinition def = GSON.fromJson(json, ShipClassDefinition.class);
                            if (def != null && def.classId != null) {
                                REGISTRY.put(def.classId, def);
                                System.out.println("[Tactical] Loaded ship class: " + def.classId);
                            }
                        } catch (Exception e) {
                            System.err.println("[Tactical] Failed to load ship class " + p + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("[Tactical] Failed to read ships directory: " + e.getMessage());
        }
        System.out.println("[Tactical] Loaded " + REGISTRY.size() + " ship class(es).");
    }

    public static Optional<ShipClassDefinition> get(String classId) {
        return Optional.ofNullable(REGISTRY.get(classId));
    }

    public static Collection<ShipClassDefinition> getAll() {
        return REGISTRY.values();
    }

    private static void writeDefaults(Path shipsDir) {
        // Write a default heavy cruiser definition
        ShipClassDefinition cruiser = new ShipClassDefinition();
        cruiser.classId = "heavy_cruiser";
        cruiser.displayName = "Heavy Cruiser";
        cruiser.hullMax = 1000;
        cruiser.shieldMax = 400;
        cruiser.shieldRegenRate = 5;
        cruiser.maxSpeed = 3.0f;
        cruiser.turnRate = 5.0f;
        cruiser.powerCapacity = 1000;
        cruiser.phaserDamage = 80;
        cruiser.phaserCooldown = 20;
        cruiser.torpedoDamage = 200;
        cruiser.sensorRange = 500;
        cruiser.warpSpeedThresholds = Map.of(1, 200, 2, 400, 3, 600, 4, 800, 5, 1000);
        cruiser.damageZones = List.of(
                "zone.bridge", "zone.weapons_fore", "zone.weapons_aft",
                "zone.torpedo_bay", "zone.shield_emit", "zone.engines",
                "zone.sensors", "zone.engineering", "zone.life_support");

        ShipClassDefinition destroyer = new ShipClassDefinition();
        destroyer.classId = "light_destroyer";
        destroyer.displayName = "Light Destroyer";
        destroyer.hullMax = 600;
        destroyer.shieldMax = 250;
        destroyer.shieldRegenRate = 8;
        destroyer.maxSpeed = 5.0f;
        destroyer.turnRate = 10.0f;
        destroyer.powerCapacity = 600;
        destroyer.phaserDamage = 50;
        destroyer.phaserCooldown = 15;
        destroyer.torpedoDamage = 150;
        destroyer.sensorRange = 350;
        destroyer.warpSpeedThresholds = Map.of(1, 120, 2, 240, 3, 360);
        destroyer.damageZones = List.of(
                "zone.bridge", "zone.weapons_fore", "zone.engines",
                "zone.sensors", "zone.engineering");

        try {
            Files.writeString(shipsDir.resolve("heavy_cruiser.json"),
                    GSON.toJson(cruiser));
            Files.writeString(shipsDir.resolve("light_destroyer.json"),
                    GSON.toJson(destroyer));
        } catch (IOException e) {
            System.err.println("[Tactical] Failed to write default ship classes: " + e.getMessage());
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getClassId()         { return classId; }
    public String getDisplayName()     { return displayName; }
    public int getHullMax()            { return hullMax; }
    public int getShieldMax()          { return shieldMax; }
    public int getShieldRegenRate()    { return shieldRegenRate; }
    public float getMaxSpeed()         { return maxSpeed; }
    public float getTurnRate()         { return turnRate; }
    public int getPowerCapacity()      { return powerCapacity; }
    public int getPhaserDamage()       { return phaserDamage; }
    public int getPhaserCooldown()     { return phaserCooldown; }
    public int getTorpedoDamage()      { return torpedoDamage; }
    public int getSensorRange()        { return sensorRange; }
    public List<String> getDamageZones() { return damageZones != null ? damageZones : List.of(); }

    public int getRequiredPowerForWarp(int warpFactor) {
        return warpSpeedThresholds.getOrDefault(warpFactor, Integer.MAX_VALUE);
    }
}