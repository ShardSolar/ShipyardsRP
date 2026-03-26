package net.shard.seconddawnrp.dice.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.shard.seconddawnrp.division.Rank;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loaded from {@code config/assets/seconddawnrp/roll_modifiers.json}.
 *
 * <p>Example file:
 * <pre>
 * {
 *   "rankBonuses": {
 *     "JUNIOR_CREWMAN": 0,
 *     "CREWMAN": 0,
 *     "SENIOR_CREWMAN": 0,
 *     "PETTY_OFFICER": 1,
 *     "SENIOR_PETTY_OFFICER": 1,
 *     "CHIEF_PETTY_OFFICER": 2,
 *     "WARRANT_OFFICER": 2,
 *     "SENIOR_WARRANT_OFFICER": 2,
 *     "CHIEF_WARRANT_OFFICER": 3,
 *     "ENSIGN": 1,
 *     "LTJG": 2,
 *     "LIEUTENANT": 2,
 *     "LT_COMMANDER": 3,
 *     "COMMANDER": 3,
 *     "CAPTAIN": 4
 *   },
 *   "certBonuses": {
 *     "medical.surgeon": 2,
 *     "medical.field_doctor": 1,
 *     "science.xenobiologist": 1
 *   }
 * }
 * </pre>
 *
 * Demerit roll penalties are set per-demerit by officers (Phase 9.5).
 * Until then demerits have no roll penalty.
 */
public class RollModifierConfig {

    private static final Gson GSON = new Gson();
    private static final String DEFAULT_JSON = """
            {
              "rankBonuses": {
                "JUNIOR_CREWMAN": 0,
                "CREWMAN": 0,
                "SENIOR_CREWMAN": 0,
                "PETTY_OFFICER": 1,
                "SENIOR_PETTY_OFFICER": 1,
                "CHIEF_PETTY_OFFICER": 2,
                "WARRANT_OFFICER": 2,
                "SENIOR_WARRANT_OFFICER": 2,
                "CHIEF_WARRANT_OFFICER": 3,
                "ENSIGN": 1,
                "LTJG": 2,
                "LIEUTENANT": 2,
                "LT_COMMANDER": 3,
                "COMMANDER": 3,
                "CAPTAIN": 4
              },
              "certBonuses": {
                "medical.surgeon": 2,
                "medical.field_doctor": 1,
                "science.xenobiologist": 1,
                "engineering.systems_engineer": 1,
                "security.detective": 1
              }
            }
            """;

    private final Map<String, Integer> rankBonuses = new HashMap<>();
    private final Map<String, Integer> certBonuses = new HashMap<>();

    private final Path configPath;

    public RollModifierConfig(Path configDir) {
        this.configPath = configDir.resolve("assets/seconddawnrp/roll_modifiers.json");
    }

    public void load() {
        // Write default if missing
        if (!Files.exists(configPath)) {
            try {
                Files.createDirectories(configPath.getParent());
                Files.writeString(configPath, DEFAULT_JSON);
            } catch (IOException e) {
                System.err.println("[SecondDawnRP] Failed to write default roll_modifiers.json: " + e.getMessage());
            }
        }

        rankBonuses.clear();
        certBonuses.clear();

        try (var reader = new InputStreamReader(
                Files.newInputStream(configPath), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);

            if (root.has("rankBonuses")) {
                root.getAsJsonObject("rankBonuses").entrySet()
                        .forEach(e -> rankBonuses.put(e.getKey(), e.getValue().getAsInt()));
            }
            if (root.has("certBonuses")) {
                root.getAsJsonObject("certBonuses").entrySet()
                        .forEach(e -> certBonuses.put(e.getKey(), e.getValue().getAsInt()));
            }
        } catch (Exception e) {
            System.err.println("[SecondDawnRP] Failed to load roll_modifiers.json: " + e.getMessage());
        }
    }

    public int getRankBonus(Rank rank) {
        return rankBonuses.getOrDefault(rank.name(), 0);
    }

    public int getCertBonus(String certId) {
        return certBonuses.getOrDefault(certId, 0);
    }

    public Map<String, Integer> getCertBonuses() {
        return certBonuses;
    }
}