package net.shard.seconddawnrp.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON-driven point values for all automatic officer progression action types.
 * File: config/assets/seconddawnrp/officer_progression.json
 *
 * Officers earn points through administrative actions, not task completion.
 * All values are configurable here — never hardcoded.
 */
public class OfficerProgressionConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "officer_progression.json";

    private final Path file;

    // Points awarded per action type
    private int reviewPaddSession     = 5;   // Reviewing and confirming an RP PADD session log
    private int approveTask           = 3;   // Approving a task from AWAITING_REVIEW
    private int confirmCertTask       = 5;   // Confirming a certification path task
    private int conductCounseling     = 10;  // Conducting a logged counseling session (approved)
    private int confirmCertGraduation = 15;  // Confirming a full certification graduation
    private int maxCommendationPoints = 50;  // Cap per single commendation (manual)

    public OfficerProgressionConfig(Path configDir) {
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
            if (obj.has("reviewPaddSession"))      reviewPaddSession      = obj.get("reviewPaddSession").getAsInt();
            if (obj.has("approveTask"))            approveTask            = obj.get("approveTask").getAsInt();
            if (obj.has("confirmCertTask"))        confirmCertTask        = obj.get("confirmCertTask").getAsInt();
            if (obj.has("conductCounseling"))      conductCounseling      = obj.get("conductCounseling").getAsInt();
            if (obj.has("confirmCertGraduation"))  confirmCertGraduation  = obj.get("confirmCertGraduation").getAsInt();
            if (obj.has("maxCommendationPoints"))  maxCommendationPoints  = obj.get("maxCommendationPoints").getAsInt();
        } catch (IOException e) {
            System.out.println("[SecondDawnRP] Failed to load officer progression config: " + e.getMessage());
        }
    }

    private void save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("reviewPaddSession",      reviewPaddSession);
        obj.addProperty("approveTask",            approveTask);
        obj.addProperty("confirmCertTask",        confirmCertTask);
        obj.addProperty("conductCounseling",      conductCounseling);
        obj.addProperty("confirmCertGraduation",  confirmCertGraduation);
        obj.addProperty("maxCommendationPoints",  maxCommendationPoints);
        try {
            Files.writeString(file, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[SecondDawnRP] Failed to save officer progression config: " + e.getMessage());
        }
    }

    public int getReviewPaddSession()     { return reviewPaddSession; }
    public int getApproveTask()           { return approveTask; }
    public int getConfirmCertTask()       { return confirmCertTask; }
    public int getConductCounseling()     { return conductCounseling; }
    public int getConfirmCertGraduation() { return confirmCertGraduation; }
    public int getMaxCommendationPoints() { return maxCommendationPoints; }
}