package net.shard.seconddawnrp.warpcore.repository;

import net.shard.seconddawnrp.warpcore.data.WarpCoreEntry;

import java.util.Optional;

/**
 * Persistence contract for the warp core entry.
 *
 * <p>Only one warp core exists per server. The repository stores and
 * retrieves that single entry. JSON is the primary backend for Phase 5.
 */
public interface WarpCoreRepository {

    /** Persist the current warp core state. */
    void save(WarpCoreEntry entry);

    /** Load the persisted warp core entry, if one exists. */
    Optional<WarpCoreEntry> load();

    /** Remove the persisted entry — used when the controller block is broken. */
    void delete();
}