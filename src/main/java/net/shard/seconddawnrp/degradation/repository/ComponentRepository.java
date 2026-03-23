package net.shard.seconddawnrp.degradation.repository;

import net.shard.seconddawnrp.degradation.data.ComponentEntry;

import java.util.Collection;
import java.util.Optional;

/*
 * Persistence contract for registered maintainable components.
 *
 * <p>All implementations must be thread-safe with respect to the server
 * main thread. JSON is the primary backend for Phase 4; a SQL migration
 * path is provided by {@link SqlComponentRepository} for Phase 5+.
 */
public interface ComponentRepository {

    /**
     * Persist a newly registered component. Overwrites any existing entry
     * with the same {@code worldKey + blockPosLong} pair.
     */
    void save(ComponentEntry entry);

    /**
     * Persist all dirty component entries in bulk. Called on server stop
     * and on periodic auto-save. Implementations may batch writes.
     */
    void saveAll(Collection<ComponentEntry> entries);

    /** Load all persisted components. Called once on server start. */
    Collection<ComponentEntry> loadAll();

    /** Find a component by its auto-generated ID. */
    Optional<ComponentEntry> findById(String componentId);

    /** Find a component by world key and packed block position. */
    Optional<ComponentEntry> findByPosition(String worldKey, long blockPosLong);

    /** Permanently remove a component by ID. */
    void delete(String componentId);

    /** Remove all components (used by /engineering reset — admin only). */
    void deleteAll();
}