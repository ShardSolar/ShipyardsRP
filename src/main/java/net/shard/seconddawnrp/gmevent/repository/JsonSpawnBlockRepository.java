package net.shard.seconddawnrp.gmevent.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.shard.seconddawnrp.gmevent.data.SpawnBlockEntry;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JsonSpawnBlockRepository implements SpawnBlockRepository {

    private final Path savePath;
    private final Gson gson;

    public JsonSpawnBlockRepository(Path configDir) {
        this.savePath = configDir
                .resolve("assets")
                .resolve("seconddawnrp")
                .resolve("spawn_blocks.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void init() throws IOException {
        Files.createDirectories(savePath.getParent());
    }

    @Override
    public List<SpawnBlockEntry> loadAll() {
        if (!Files.exists(savePath)) return new ArrayList<>();
        try {
            String json = Files.readString(savePath);
            Type listType = new TypeToken<List<SpawnBlockEntry>>() {}.getType();
            List<SpawnBlockEntry> loaded = gson.fromJson(json, listType);
            return loaded != null ? loaded : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public void saveAll(List<SpawnBlockEntry> entries) {
        try {
            Files.createDirectories(savePath.getParent());
            Files.writeString(savePath, gson.toJson(entries));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}