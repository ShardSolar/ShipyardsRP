package net.shard.seconddawnrp.medical;

import net.shard.seconddawnrp.character.LongTermInjury;
import net.shard.seconddawnrp.character.SqlLongTermInjuryRepository;
import net.shard.seconddawnrp.database.DatabaseManager;

import java.sql.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite implementation of {@link MedicalRepository}.
 *
 * <p>All condition data lives in {@code long_term_injuries} — the table
 * extended in V8. Bulk reads delegate to {@link SqlLongTermInjuryRepository}
 * so there is no duplicated mapping logic. Targeted writes (updateSteps,
 * resolve) use narrow UPDATE statements for efficiency.
 */
public final class SqlMedicalRepository implements MedicalRepository {

    private final DatabaseManager db;
    private final SqlLongTermInjuryRepository ltiRepo;

    public SqlMedicalRepository(DatabaseManager db,
                                SqlLongTermInjuryRepository ltiRepo) {
        this.db      = db;
        this.ltiRepo = ltiRepo;
    }

    // ── MedicalRepository ─────────────────────────────────────────────────────

    @Override
    public void save(LongTermInjury condition) {
        ltiRepo.save(condition);
    }

    @Override
    public Optional<LongTermInjury> loadById(String conditionId) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM long_term_injuries WHERE injury_id = ?")) {
                ps.setString(1, conditionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load condition " + conditionId, e);
        }
    }

    @Override
    public List<LongTermInjury> loadActiveForPlayer(UUID playerUuid) {
        return ltiRepo.loadAllActiveForPlayer(playerUuid);
    }

    @Override
    public List<LongTermInjury> loadHistoryForPlayer(UUID playerUuid) {
        return ltiRepo.loadHistory(playerUuid);
    }

    @Override
    public void deactivate(String conditionId) {
        ltiRepo.deactivate(conditionId);
    }

    @Override
    public void updateSteps(String conditionId, String stepsJson) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE long_term_injuries "
                            + "SET treatment_steps_completed = ? "
                            + "WHERE injury_id = ?")) {
                ps.setString(1, stepsJson);
                ps.setString(2, conditionId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update steps for condition " + conditionId, e);
        }
    }

    @Override
    public void resolve(String conditionId, String resolvedByUuid, String resolutionNote) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE long_term_injuries "
                            + "SET active = 0, "
                            + "    resolved_by = ?, "
                            + "    resolution_note = ? "
                            + "WHERE injury_id = ?")) {
                ps.setString(1, resolvedByUuid);
                ps.setString(2, resolutionNote);
                ps.setString(3, conditionId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to resolve condition " + conditionId, e);
        }
    }

    // ── Row mapping ───────────────────────────────────────────────────────────
    // Duplicated from SqlLongTermInjuryRepository because loadById needs its
    // own ResultSet. Kept in sync — both call the same LongTermInjury constructor.

    private LongTermInjury mapRow(ResultSet rs) throws SQLException {
        String  conditionKey            = safeString(rs, "condition_key");
        String  displayNameOverride     = safeString(rs, "display_name_override");
        String  descriptionOverride     = safeString(rs, "description_override");
        boolean requiresSurgery         = safeInt(rs, "requires_surgery") == 1;
        String  treatmentStepsCompleted = safeStringDefault(rs, "treatment_steps_completed", "[]");
        String  resolvedBy              = safeString(rs, "resolved_by");
        String  resolutionNote          = safeString(rs, "resolution_note");
        boolean isDeathCause            = safeInt(rs, "is_death_cause") == 1;
        String  appliedBy               = safeString(rs, "applied_by");
        String  notes                   = safeString(rs, "notes");

        return new LongTermInjury(
                rs.getString("injury_id"),
                UUID.fromString(rs.getString("player_uuid")),
                net.shard.seconddawnrp.character.LongTermInjuryTier.valueOf(rs.getString("tier")),
                rs.getLong("applied_at_ms"),
                rs.getLong("expires_at_ms"),
                rs.getInt("sessions_completed"),
                rs.getLong("last_treatment_ms"),
                rs.getInt("active") == 1,
                conditionKey, displayNameOverride, descriptionOverride,
                requiresSurgery, treatmentStepsCompleted,
                resolvedBy, resolutionNote, isDeathCause, appliedBy, notes
        );
    }

    private static String safeString(ResultSet rs, String col) {
        try { return rs.getString(col); } catch (SQLException ignored) { return null; }
    }

    private static String safeStringDefault(ResultSet rs, String col, String def) {
        try { String v = rs.getString(col); return v != null ? v : def; }
        catch (SQLException ignored) { return def; }
    }

    private static int safeInt(ResultSet rs, String col) {
        try { return rs.getInt(col); } catch (SQLException ignored) { return 0; }
    }
}