package net.shard.seconddawnrp.medical;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads and caches all medical condition templates from JSON files.
 *
 * Files live at: data/seconddawnrp/medical_conditions/<key>.json
 *
 * critical_trauma.json is a system condition baked into the jar and written
 * automatically on every reload if missing — it is required for the downed
 * state to function and must always be present.
 */
public class MedicalConditionRegistry {

    private static final Gson GSON = new Gson();
    private static final String CONDITIONS_DIR = "data/seconddawnrp/medical_conditions";

    private final Map<String, MedicalConditionTemplate> registry = new LinkedHashMap<>();

    // ── Load ──────────────────────────────────────────────────────────────────

    public void reload() {
        registry.clear();
        Path dir = Path.of(CONDITIONS_DIR);

        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
                writeDefaultFiles(dir);
            } catch (IOException e) {
                System.err.println("[SecondDawnRP] Failed to create medical_conditions directory: "
                        + e.getMessage());
            }
        } else {
            // Always ensure system-critical conditions exist even if directory already exists
            try {
                ensureSystemConditions(dir);
            } catch (IOException e) {
                System.err.println("[SecondDawnRP] Failed to write system conditions: "
                        + e.getMessage());
            }
        }

        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadFile);
        } catch (IOException e) {
            System.err.println("[SecondDawnRP] Failed to list medical_conditions directory: "
                    + e.getMessage());
        }

        System.out.println("[SecondDawnRP] Loaded " + registry.size()
                + " medical condition templates.");
    }

    private void loadFile(Path file) {
        try (InputStream in = Files.newInputStream(file);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {

            JsonObject obj = GSON.fromJson(reader, JsonObject.class);

            String key          = obj.get("key").getAsString();
            String displayName  = obj.get("displayName").getAsString();
            String severityStr  = obj.get("severity").getAsString();
            boolean requiresSurgery = obj.has("requiresSurgery")
                    && obj.get("requiresSurgery").getAsBoolean();
            String description  = obj.has("description")
                    ? obj.get("description").getAsString() : "";

            MedicalConditionTemplate.Severity severity;
            try {
                severity = MedicalConditionTemplate.Severity.valueOf(severityStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("[SecondDawnRP] Unknown severity '" + severityStr
                        + "' in " + file.getFileName() + " — defaulting to ACUTE.");
                severity = MedicalConditionTemplate.Severity.ACUTE;
            }

            // ── Active effects ────────────────────────────────────────────────
            List<MedicalConditionTemplate.ConditionEffect> activeEffects = new ArrayList<>();
            if (obj.has("activeEffects") && obj.get("activeEffects").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("activeEffects")) {
                    JsonObject eff = el.getAsJsonObject();
                    activeEffects.add(new MedicalConditionTemplate.ConditionEffect(
                            eff.get("effect").getAsString(),
                            eff.has("amplifier") ? eff.get("amplifier").getAsInt() : 0,
                            eff.has("durationTicks") ? eff.get("durationTicks").getAsInt() : 7200
                    ));
                }
            }

            // ── Milk interaction ──────────────────────────────────────────────
            MedicalConditionTemplate.MilkInteraction milkInteraction =
                    new MedicalConditionTemplate.MilkInteraction(false, 0, 0, false);
            if (obj.has("milkInteraction") && obj.get("milkInteraction").isJsonObject()) {
                JsonObject milk = obj.getAsJsonObject("milkInteraction");
                milkInteraction = new MedicalConditionTemplate.MilkInteraction(
                        milk.has("allowed") && milk.get("allowed").getAsBoolean(),
                        milk.has("suppressionSeconds") ? milk.get("suppressionSeconds").getAsInt() : 0,
                        milk.has("reapplyCooldownSeconds") ? milk.get("reapplyCooldownSeconds").getAsInt() : 0,
                        milk.has("clearOnUse") && milk.get("clearOnUse").getAsBoolean()
                );
            }

            // ── Treatment plan ────────────────────────────────────────────────
            List<MedicalConditionTemplate.TreatmentStep> steps = new ArrayList<>();
            if (obj.has("treatmentPlan") && obj.get("treatmentPlan").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("treatmentPlan")) {
                    JsonObject step = el.getAsJsonObject();

                    MedicalConditionTemplate.TimingConstraint timing = null;
                    if (step.has("timing") && step.get("timing").isJsonObject()) {
                        JsonObject t = step.getAsJsonObject("timing");

                        MedicalConditionTemplate.TimingTrigger trigger =
                                MedicalConditionTemplate.TimingTrigger.PREVIOUS_STEP;
                        if (t.has("triggerFrom")) {
                            try {
                                trigger = MedicalConditionTemplate.TimingTrigger.valueOf(
                                        t.get("triggerFrom").getAsString().toUpperCase());
                            } catch (IllegalArgumentException ignored) {}
                        }

                        Integer minSeconds = t.has("minSeconds")
                                ? t.get("minSeconds").getAsInt() : null;
                        Integer maxSeconds = t.has("maxSeconds")
                                ? t.get("maxSeconds").getAsInt() : null;
                        boolean showTimer  = t.has("showTimer")
                                && t.get("showTimer").getAsBoolean();
                        String failureEffect = t.has("failureEffect")
                                ? t.get("failureEffect").getAsString() : null;

                        timing = new MedicalConditionTemplate.TimingConstraint(
                                trigger, minSeconds, maxSeconds, showTimer, failureEffect);
                    }

                    steps.add(new MedicalConditionTemplate.TreatmentStep(
                            step.get("stepKey").getAsString(),
                            step.get("label").getAsString(),
                            step.get("item").getAsString(),
                            step.has("quantity") ? step.get("quantity").getAsInt() : 1,
                            step.has("requiresSurgery")
                                    && step.get("requiresSurgery").getAsBoolean(),
                            timing
                    ));
                }
            }

            registry.put(key, new MedicalConditionTemplate(
                    key,
                    displayName,
                    severity,
                    requiresSurgery,
                    description,
                    List.copyOf(activeEffects),
                    milkInteraction,
                    List.copyOf(steps)
            ));

        } catch (Exception e) {
            System.err.println("[SecondDawnRP] Failed to load condition file "
                    + file.getFileName() + ": " + e.getMessage());
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Optional<MedicalConditionTemplate> get(String key) {
        return Optional.ofNullable(registry.get(key));
    }

    public List<MedicalConditionTemplate> getAll() {
        return List.copyOf(registry.values());
    }

    public boolean exists(String key) {
        return registry.containsKey(key);
    }

    public List<MedicalConditionTemplate> getBySeverity(
            MedicalConditionTemplate.Severity severity) {
        return registry.values().stream()
                .filter(t -> t.severity() == severity)
                .sorted(Comparator.comparing(MedicalConditionTemplate::displayName))
                .toList();
    }

    // ── Default / system files ────────────────────────────────────────────────

    /**
     * Called only when the directory is created fresh for the first time.
     * Writes all example conditions AND system conditions.
     */
    private void writeDefaultFiles(Path dir) throws IOException {
        Files.writeString(dir.resolve("plasma_burn_severe.json"), PLASMA_BURN_JSON);
        Files.writeString(dir.resolve("radiation_exposure.json"), RADIATION_JSON);
        Files.writeString(dir.resolve("bone_fracture.json"),      BONE_FRACTURE_JSON);
        Files.writeString(dir.resolve("critical_trauma.json"),    CRITICAL_TRAUMA_JSON);
        System.out.println("[SecondDawnRP] Wrote default medical condition files.");
    }

    /**
     * Called every reload to ensure system-critical conditions always exist.
     * Only writes if missing — operators can customise the file freely.
     */
    private void ensureSystemConditions(Path dir) throws IOException {
        Path criticalTrauma = dir.resolve("critical_trauma.json");
        if (!Files.exists(criticalTrauma)) {
            Files.writeString(criticalTrauma, CRITICAL_TRAUMA_JSON);
            System.out.println("[SecondDawnRP] Wrote missing system condition: critical_trauma.json");
        }
    }

    // ── JSON constants ────────────────────────────────────────────────────────

    private static final String PLASMA_BURN_JSON = """
            {
              "key": "plasma_burn_severe",
              "displayName": "Severe Plasma Burn",
              "severity": "CRITICAL",
              "requiresSurgery": true,
              "description": "Severe tissue damage from plasma discharge. Surgical intervention required after the pain inhibitor takes effect.",
              "activeEffects": [
                { "effect": "minecraft:weakness", "amplifier": 1, "durationTicks": 7200 },
                { "effect": "minecraft:nausea",   "amplifier": 0, "durationTicks": 200  }
              ],
              "milkInteraction": {
                "allowed": true,
                "suppressionSeconds": 45,
                "reapplyCooldownSeconds": 120,
                "clearOnUse": true
              },
              "treatmentPlan": [
                {
                  "stepKey": "pain_inhibitor",
                  "label": "Administer Pain Inhibitor",
                  "item": "minecraft:honey_bottle",
                  "quantity": 1,
                  "requiresSurgery": false
                },
                {
                  "stepKey": "surgical_debride",
                  "label": "Surgical Debridement",
                  "item": "minecraft:shears",
                  "quantity": 1,
                  "requiresSurgery": true,
                  "timing": {
                    "triggerFrom": "PREVIOUS_STEP",
                    "minSeconds": 120,
                    "maxSeconds": 300,
                    "showTimer": true,
                    "failureEffect": "minecraft:nausea:1:200"
                  }
                },
                {
                  "stepKey": "dermal_regen",
                  "label": "Apply Dermal Regenerator",
                  "item": "minecraft:iron_ingot",
                  "quantity": 2,
                  "requiresSurgery": false
                }
              ]
            }
            """;

    private static final String RADIATION_JSON = """
            {
              "key": "radiation_exposure",
              "displayName": "Radiation Exposure",
              "severity": "CHRONIC",
              "requiresSurgery": false,
              "description": "Cellular damage from ionising radiation. Anti-radiation doses must be spaced to avoid compound shock.",
              "activeEffects": [
                { "effect": "minecraft:weakness", "amplifier": 0, "durationTicks": 7200 },
                { "effect": "minecraft:hunger",   "amplifier": 0, "durationTicks": 7200 }
              ],
              "milkInteraction": {
                "allowed": true,
                "suppressionSeconds": 30,
                "reapplyCooldownSeconds": 180,
                "clearOnUse": true
              },
              "treatmentPlan": [
                {
                  "stepKey": "anti_rad_dose_1",
                  "label": "Administer Anti-Radiation Compound (Dose 1)",
                  "item": "minecraft:potion",
                  "quantity": 1,
                  "requiresSurgery": false
                },
                {
                  "stepKey": "anti_rad_dose_2",
                  "label": "Administer Anti-Radiation Compound (Dose 2)",
                  "item": "minecraft:potion",
                  "quantity": 1,
                  "requiresSurgery": false,
                  "timing": {
                    "triggerFrom": "PREVIOUS_STEP",
                    "minSeconds": 180,
                    "showTimer": false,
                    "failureEffect": "minecraft:poison:0:100"
                  }
                },
                {
                  "stepKey": "cellular_stabiliser",
                  "label": "Administer Cellular Stabiliser",
                  "item": "minecraft:golden_apple",
                  "quantity": 1,
                  "requiresSurgery": false
                }
              ]
            }
            """;

    private static final String BONE_FRACTURE_JSON = """
            {
              "key": "bone_fracture",
              "displayName": "Bone Fracture",
              "severity": "ACUTE",
              "requiresSurgery": false,
              "description": "Hairline or complete fracture. Osteogenic regenerator treatment required.",
              "activeEffects": [
                { "effect": "minecraft:slowness",       "amplifier": 0, "durationTicks": 7200 },
                { "effect": "minecraft:mining_fatigue", "amplifier": 0, "durationTicks": 7200 }
              ],
              "milkInteraction": {
                "allowed": false,
                "suppressionSeconds": 0,
                "reapplyCooldownSeconds": 0,
                "clearOnUse": false
              },
              "treatmentPlan": [
                {
                  "stepKey": "stabilise",
                  "label": "Stabilise Fracture Site",
                  "item": "minecraft:stick",
                  "quantity": 2,
                  "requiresSurgery": false
                },
                {
                  "stepKey": "osteogenic_regen",
                  "label": "Apply Osteogenic Regenerator",
                  "item": "minecraft:bone_meal",
                  "quantity": 3,
                  "requiresSurgery": false
                }
              ]
            }
            """;

    // System condition — baked into jar, written automatically if missing.
    // Required for downed state. Resolving it calls DownedService.revivePlayer().
    private static final String CRITICAL_TRAUMA_JSON = """
            {
              "key": "critical_trauma",
              "displayName": "Critical Trauma",
              "severity": "CRITICAL",
              "requiresSurgery": false,
              "description": "Severe systemic trauma from a lethal event. The patient is down and non-ambulatory. Full stabilization protocol required before revival.",
              "activeEffects": [
                { "effect": "minecraft:weakness", "amplifier": 1, "durationTicks": 7200 }
              ],
              "treatmentPlan": [
                {
                  "stepKey": "airways",
                  "label": "Clear Airways",
                  "item": "minecraft:paper",
                  "quantity": 1,
                  "requiresSurgery": false
                },
                {
                  "stepKey": "haemostasis",
                  "label": "Apply Haemostatic Agent",
                  "item": "minecraft:red_dye",
                  "quantity": 2,
                  "requiresSurgery": false
                },
                {
                  "stepKey": "stabilise",
                  "label": "Administer Stabilizer",
                  "item": "minecraft:honey_bottle",
                  "quantity": 1,
                  "requiresSurgery": false,
                  "timing": {
                    "triggerFrom": "PREVIOUS_STEP",
                    "minSeconds": 30,
                    "maxSeconds": 300,
                    "showTimer": true,
                    "failureEffect": "minecraft:nausea:1:200"
                  }
                }
              ]
            }
            """;
}