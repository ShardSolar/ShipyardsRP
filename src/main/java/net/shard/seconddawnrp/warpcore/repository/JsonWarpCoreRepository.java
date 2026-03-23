package net.shard.seconddawnrp.warpcore.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.shard.seconddawnrp.warpcore.data.ReactorState;
import net.shard.seconddawnrp.warpcore.data.WarpCoreEntry;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * JSON-backed warp core repository.
 * Stored at {@code config/assets/seconddawnrp/warpcore.json}.
 * Writes are atomic via .tmp swap.
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
    public void save(WarpCoreEntry entry) {
        JsonObject obj = new JsonObject();
        obj.addProperty("worldKey", entry.getWorldKey());
        obj.addProperty("blockPosLong", entry.getBlockPosLong());
        obj.addProperty("state", entry.getState().name());
        obj.addProperty("fuelRods", entry.getFuelRods());
        obj.addProperty("lastFuelDrainMs", entry.getLastFuelDrainMs());
        obj.addProperty("lastFaultTaskMs", entry.getLastFaultTaskMs());
        obj.addProperty("currentPowerOutput", entry.getCurrentPowerOutput());
        obj.addProperty("resonanceCoilComponentId", entry.getResonanceCoilComponentId());
        obj.addProperty("registeredByUuid",
                entry.getRegisteredByUuid() != null
                        ? entry.getRegisteredByUuid().toString() : null);

        Path tmp = filePath.resolveSibling(FILE_NAME + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp)) {
            GSON.toJson(obj, w);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write warpcore.tmp", e);
        }
        try {
            Files.move(tmp, filePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to replace warpcore.json", e);
        }
    }

    @Override
    public Optional<WarpCoreEntry> load() {
        if (!Files.exists(filePath)) return Optional.empty();
        try (Reader r = Files.newBufferedReader(filePath)) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);
            if (obj == null) return Optional.empty();

            String uuidStr = obj.has("registeredByUuid")
                    && !obj.get("registeredByUuid").isJsonNull()
                    ? obj.get("registeredByUuid").getAsString() : null;
            String coilId = obj.has("resonanceCoilComponentId")
                    && !obj.get("resonanceCoilComponentId").isJsonNull()
                    ? obj.get("resonanceCoilComponentId").getAsString() : null;

            WarpCoreEntry entry = new WarpCoreEntry(
                    obj.get("worldKey").getAsString(),
                    obj.get("blockPosLong").getAsLong(),
                    ReactorState.valueOf(obj.get("state").getAsString()),
                    obj.get("fuelRods").getAsInt(),
                    obj.get("lastFuelDrainMs").getAsLong(),
                    obj.get("lastFaultTaskMs").getAsLong(),
                    obj.get("currentPowerOutput").getAsInt(),
                    coilId,
                    uuidStr != null ? UUID.fromString(uuidStr) : null
            );
            return Optional.of(entry);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load warpcore.json", e);
        }
    }

    @Override
    public void delete() {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete warpcore.json", e);
        }
    }
}