package net.shard.seconddawnrp.warpcore.repository;

import com.google.gson.*;
import net.shard.seconddawnrp.warpcore.data.ReactorState;
import net.shard.seconddawnrp.warpcore.data.WarpCoreEntry;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * JSON-backed warp core repository supporting multiple warp cores.
 * Stored as a JSON array at {@code config/assets/seconddawnrp/warpcore.json}.
 * Backward compatible with old single-object saves.
 */
public class JsonWarpCoreRepository implements WarpCoreRepository {

    private static final String FILE_NAME = "warpcore.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path filePath;

    public JsonWarpCoreRepository(Path configDir) {
        this.filePath = configDir.resolve("assets/seconddawnrp/" + FILE_NAME);
    }

    public void init() throws IOException {
        if (!Files.exists(filePath)) {
            Files.createDirectories(filePath.getParent());
        }
    }

    @Override
    public void saveAll(Collection<WarpCoreEntry> entries) {
        JsonArray arr = new JsonArray();
        for (WarpCoreEntry e : entries) arr.add(toJson(e));
        Path tmp = filePath.resolveSibling(FILE_NAME + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp)) { GSON.toJson(arr, w); }
        catch (IOException e) { throw new RuntimeException("Failed to write warpcore.tmp", e); }
        try { Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException e) { throw new RuntimeException("Failed to replace warpcore.json", e); }
    }

    @Override
    public Collection<WarpCoreEntry> loadAll() {
        if (!Files.exists(filePath)) return new ArrayList<>();
        try (Reader r = Files.newBufferedReader(filePath)) {
            JsonElement el = GSON.fromJson(r, JsonElement.class);
            if (el == null) return new ArrayList<>();

            // Backward compat: old saves were a single JsonObject
            if (el.isJsonObject()) {
                WarpCoreEntry e = fromJson(el.getAsJsonObject());
                return e != null ? List.of(e) : new ArrayList<>();
            }

            List<WarpCoreEntry> result = new ArrayList<>();
            for (JsonElement item : el.getAsJsonArray()) {
                WarpCoreEntry e = fromJson(item.getAsJsonObject());
                if (e != null) result.add(e);
            }
            return result;
        } catch (IOException e) { throw new RuntimeException("Failed to load warpcore.json", e); }
    }

    @Override
    public void delete(String entryId) {
        List<WarpCoreEntry> entries = new ArrayList<>(loadAll());
        entries.removeIf(e -> e.getEntryId().equals(entryId));
        saveAll(entries);
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private static JsonObject toJson(WarpCoreEntry e) {
        JsonObject o = new JsonObject();
        o.addProperty("entryId", e.getEntryId());
        o.addProperty("worldKey", e.getWorldKey());
        o.addProperty("blockPosLong", e.getBlockPosLong());
        o.addProperty("state", e.getState().name());
        o.addProperty("fuelRods", e.getFuelRods());
        o.addProperty("lastFuelDrainMs", e.getLastFuelDrainMs());
        o.addProperty("lastFaultTaskMs", e.getLastFaultTaskMs());
        o.addProperty("currentPowerOutput", e.getCurrentPowerOutput());
        com.google.gson.JsonArray coils = new com.google.gson.JsonArray();
        e.getResonanceCoilIds().forEach(coils::add);
        o.add("resonanceCoilIds", coils);
        o.addProperty("registeredByUuid",
                e.getRegisteredByUuid() != null ? e.getRegisteredByUuid().toString() : null);
        return o;
    }

    private static WarpCoreEntry fromJson(JsonObject o) {
        if (o == null) return null;
        String uuidStr = o.has("registeredByUuid") && !o.get("registeredByUuid").isJsonNull()
                ? o.get("registeredByUuid").getAsString() : null;
        java.util.List<String> coilIds = new java.util.ArrayList<>();
        if (o.has("resonanceCoilIds") && o.get("resonanceCoilIds").isJsonArray()) {
            o.get("resonanceCoilIds").getAsJsonArray()
                    .forEach(el -> coilIds.add(el.getAsString()));
        } else if (o.has("resonanceCoilComponentId") && !o.get("resonanceCoilComponentId").isJsonNull()) {
            // backward compat with old single-coil saves
            coilIds.add(o.get("resonanceCoilComponentId").getAsString());
        }
        // Generate entryId from blockPosLong for old single-core saves
        String entryId = o.has("entryId") ? o.get("entryId").getAsString()
                : "wc_" + Long.toHexString(o.get("blockPosLong").getAsLong() & 0xFFFFFFL);

        return new WarpCoreEntry(
                entryId,
                o.get("worldKey").getAsString(),
                o.get("blockPosLong").getAsLong(),
                ReactorState.valueOf(o.get("state").getAsString()),
                o.get("fuelRods").getAsInt(),
                o.get("lastFuelDrainMs").getAsLong(),
                o.get("lastFaultTaskMs").getAsLong(),
                o.get("currentPowerOutput").getAsInt(),
                coilIds,
                uuidStr != null ? UUID.fromString(uuidStr) : null
        );
    }
}