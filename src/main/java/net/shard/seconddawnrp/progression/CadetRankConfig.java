package net.shard.seconddawnrp.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON-driven configuration for cadet rank progression.
 * File: config/assets/seconddawnrp/cadet_config.json
 *
 * Cadet promotions are always officer-approved — these thresholds are
 * eligibility gates, not automatic triggers. The system surfaces eligibility;
 * humans make the call.
 */
public class CadetRankConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "cadet_config.json";

    private final Path file;

    // Points a cadet must accumulate before each promotion is even considered
    private int cadet1To2Points  = 0;    // Cadet 1→2: no point gate, division declaration required
    private int cadet2To3Points  = 50;   // Cadet 2→3: cert path milestones progressing
    private int cadet3To4Points  = 150;  // Cadet 3→4: continued cert progress
    private int cadet4GradPoints = 300;  // Cadet 4→Grad: cert complete, instructor proposes

    // Conversion rate: cadet points → commissioned service record on graduation
    private double cadetPointsConversionRate = 0.5;

    // Minimum real-world days in Cadet track before graduation is allowed
    private int minimumCadetDays = 7;

    public CadetRankConfig(Path configDir) {
        this.file = configDir.resolve("assets/seconddawnrp/" + FILE_NAME);
    }

    public void init() throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        if (!Files.exists(file)) save();
    }

    public void load() {
        if (!Files.exists(file)) return;
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject obj = GSON.fromJson(raw, JsonObject.class);
            if (obj == null) return;
            if (obj.has("cadet1To2Points"))           cadet1To2Points           = obj.get("cadet1To2Points").getAsInt();
            if (obj.has("cadet2To3Points"))           cadet2To3Points           = obj.get("cadet2To3Points").getAsInt();
            if (obj.has("cadet3To4Points"))           cadet3To4Points           = obj.get("cadet3To4Points").getAsInt();
            if (obj.has("cadet4GradPoints"))          cadet4GradPoints          = obj.get("cadet4GradPoints").getAsInt();
            if (obj.has("cadetPointsConversionRate")) cadetPointsConversionRate = obj.get("cadetPointsConversionRate").getAsDouble();
            if (obj.has("minimumCadetDays"))          minimumCadetDays          = obj.get("minimumCadetDays").getAsInt();
        } catch (IOException e) {
            System.out.println("[SecondDawnRP] Failed to load cadet config: " + e.getMessage());
        }
    }

    private void save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("cadet1To2Points",           cadet1To2Points);
        obj.addProperty("cadet2To3Points",           cadet2To3Points);
        obj.addProperty("cadet3To4Points",           cadet3To4Points);
        obj.addProperty("cadet4GradPoints",          cadet4GradPoints);
        obj.addProperty("cadetPointsConversionRate", cadetPointsConversionRate);
        obj.addProperty("minimumCadetDays",          minimumCadetDays);
        try {
            Files.writeString(file, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[SecondDawnRP] Failed to save cadet config: " + e.getMessage());
        }
    }

    public int getPointsRequired(net.shard.seconddawnrp.division.Rank fromRank) {
        return switch (fromRank) {
            case CADET_1 -> cadet1To2Points;
            case CADET_2 -> cadet2To3Points;
            case CADET_3 -> cadet3To4Points;
            case CADET_4 -> cadet4GradPoints;
            default      -> Integer.MAX_VALUE;
        };
    }

    public double getCadetPointsConversionRate()  { return cadetPointsConversionRate; }
    public int    getMinimumCadetDays()           { return minimumCadetDays; }
}