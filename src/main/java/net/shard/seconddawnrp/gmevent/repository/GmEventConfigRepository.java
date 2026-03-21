package net.shard.seconddawnrp.gmevent.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.shard.seconddawnrp.gmevent.data.GmEventConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GmEventConfigRepository {

    private final Path savePath;
    private final Gson gson;

    public GmEventConfigRepository(Path configDir) {
        this.savePath = configDir
                .resolve("assets")
                .resolve("seconddawnrp")
                .resolve("gmevent_config.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void init() throws IOException {
        Files.createDirectories(savePath.getParent());
        // Write defaults if file doesn't exist
        if (!Files.exists(savePath)) {
            save(new GmEventConfig());
        }
    }

    public GmEventConfig load() {
        try {
            String json = Files.readString(savePath);
            GmEventConfig config = gson.fromJson(json, GmEventConfig.class);
            return config != null ? config : new GmEventConfig();
        } catch (Exception e) {
            e.printStackTrace();
            return new GmEventConfig();
        }
    }

    public void save(GmEventConfig config) {
        try {
            Files.createDirectories(savePath.getParent());
            Files.writeString(savePath, gson.toJson(config));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}