package net.shard.seconddawnrp.gmevent.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.shard.seconddawnrp.gmevent.data.EncounterTemplate;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JsonEncounterTemplateRepository implements EncounterTemplateRepository {

    private final Path savePath;
    private final Gson gson;

    public JsonEncounterTemplateRepository(Path configDir) {
        this.savePath = configDir
                .resolve("assets")
                .resolve("seconddawnrp")
                .resolve("encounter_templates.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void init() throws IOException {
        Files.createDirectories(savePath.getParent());
    }

    @Override
    public List<EncounterTemplate> loadAll() {
        if (!Files.exists(savePath)) return new ArrayList<>();
        try {
            String json = Files.readString(savePath);
            Type listType = new TypeToken<List<EncounterTemplate>>() {}.getType();
            List<EncounterTemplate> loaded = gson.fromJson(json, listType);
            return loaded != null ? loaded : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public Optional<EncounterTemplate> findById(String id) {
        return loadAll().stream()
                .filter(t -> t.getId().equals(id))
                .findFirst();
    }

    @Override
    public void save(EncounterTemplate template) {
        List<EncounterTemplate> all = loadAll();
        all.removeIf(t -> t.getId().equals(template.getId()));
        all.add(template);
        writeAll(all);
    }

    @Override
    public void delete(String id) {
        List<EncounterTemplate> all = loadAll();
        all.removeIf(t -> t.getId().equals(id));
        writeAll(all);
    }

    private void writeAll(List<EncounterTemplate> templates) {
        try {
            Files.createDirectories(savePath.getParent());
            Files.writeString(savePath, gson.toJson(templates));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}