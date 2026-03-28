package net.shard.seconddawnrp.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.shard.seconddawnrp.division.Rank;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON-driven slot caps per commissioned rank.
 * File: config/assets/seconddawnrp/officer_slots.json
 *
 * Slot caps prevent officer inflation and preserve the value of commissioned
 * rank. When a rank is full, eligible players are queued — promoted in order
 * of service record points, then time at current rank.
 *
 * Captain has exactly 1 slot and is admin-designated only — that cap is
 * enforced in code regardless of config.
 */
public class OfficerSlotConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "officer_slots.json";

    private final Path file;

    // Default slot counts per rank — admin-adjustable at runtime
    private final Map<Rank, Integer> slots = new HashMap<>();

    public OfficerSlotConfig(Path configDir) {
        this.file = configDir.resolve("assets/seconddawnrp/" + FILE_NAME);
        setDefaults();
    }

    private void setDefaults() {
        slots.put(Rank.ENSIGN,               20);
        slots.put(Rank.LIEUTENANT_JG,        10);
        slots.put(Rank.LIEUTENANT,           10);
        slots.put(Rank.LIEUTENANT_COMMANDER,  5);
        slots.put(Rank.COMMANDER,             2);
        slots.put(Rank.CAPTAIN,               1);
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
            for (Rank rank : Rank.values()) {
                if (obj.has(rank.getId())) {
                    slots.put(rank, obj.get(rank.getId()).getAsInt());
                }
            }
            // Captain is always exactly 1 — enforce regardless of config
            slots.put(Rank.CAPTAIN, 1);
        } catch (IOException e) {
            System.out.println("[SecondDawnRP] Failed to load officer slot config: " + e.getMessage());
        }
    }

    public void save() {
        JsonObject obj = new JsonObject();
        for (Map.Entry<Rank, Integer> entry : slots.entrySet()) {
            obj.addProperty(entry.getKey().getId(), entry.getValue());
        }
        try {
            Files.writeString(file, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[SecondDawnRP] Failed to save officer slot config: " + e.getMessage());
        }
    }

    public int getSlots(Rank rank) {
        return slots.getOrDefault(rank, 0);
    }

    public void setSlots(Rank rank, int count) {
        if (rank == Rank.CAPTAIN) return; // Captain always 1
        slots.put(rank, Math.max(0, count));
        save();
    }
}