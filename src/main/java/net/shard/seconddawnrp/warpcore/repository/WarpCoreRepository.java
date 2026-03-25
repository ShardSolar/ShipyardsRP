package net.shard.seconddawnrp.warpcore.repository;

import net.shard.seconddawnrp.warpcore.data.WarpCoreEntry;

import java.util.Collection;

/** Persistence contract for warp core entries. Multiple cores are supported. */
public interface WarpCoreRepository {
    void saveAll(Collection<WarpCoreEntry> entries);
    Collection<WarpCoreEntry> loadAll();
    void delete(String entryId);
}