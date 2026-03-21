package net.shard.seconddawnrp.gmevent.repository;

import net.shard.seconddawnrp.gmevent.data.EncounterTemplate;
import java.util.List;
import java.util.Optional;

public interface EncounterTemplateRepository {
    List<EncounterTemplate> loadAll();
    Optional<EncounterTemplate> findById(String id);
    void save(EncounterTemplate template);
    void delete(String id);
}