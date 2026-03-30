package net.shard.seconddawnrp.medical;

import net.shard.seconddawnrp.character.LongTermInjury;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence contract for medical conditions.
 *
 * <p>Backed by the {@code long_term_injuries} table (extended in V8).
 * All reads and writes go through this interface — MedicalService never
 * touches the DB directly.
 */
public interface MedicalRepository {

    /** Save or update a condition record. */
    void save(LongTermInjury condition);

    /** Load a single condition by its primary key. */
    Optional<LongTermInjury> loadById(String conditionId);

    /** All active conditions for a player, ordered oldest-first. */
    List<LongTermInjury> loadActiveForPlayer(UUID playerUuid);

    /** Full history (active + resolved) for a player, most-recent-first. */
    List<LongTermInjury> loadHistoryForPlayer(UUID playerUuid);

    /** Mark a condition inactive without touching other fields. */
    void deactivate(String conditionId);

    /**
     * Update only the treatment_steps_completed JSON column.
     * Called after each item administration to avoid a full upsert.
     */
    void updateSteps(String conditionId, String stepsJson);

    /**
     * Resolve a condition — sets active=0, resolved_by, resolution_note,
     * and writes the record. One atomic call so partial state is impossible.
     */
    void resolve(String conditionId, String resolvedByUuid, String resolutionNote);
}