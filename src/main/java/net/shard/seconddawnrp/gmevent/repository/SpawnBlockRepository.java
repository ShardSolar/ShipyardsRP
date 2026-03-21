package net.shard.seconddawnrp.gmevent.repository;

import net.shard.seconddawnrp.gmevent.data.SpawnBlockEntry;
import java.util.List;

public interface SpawnBlockRepository {
    List<SpawnBlockEntry> loadAll();
    void saveAll(List<SpawnBlockEntry> entries);
}