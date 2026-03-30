package net.shard.seconddawnrp.character;

import net.shard.seconddawnrp.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqlLongTermInjuryRepository implements LongTermInjuryRepository {

    private final DatabaseManager databaseManager;

    public SqlLongTermInjuryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public Optional<LongTermInjury> loadActive(UUID playerUuid) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM long_term_injuries "
                            + "WHERE player_uuid = ? AND active = 1 "
                            + "ORDER BY applied_at_ms DESC LIMIT 1")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load active LTI for " + playerUuid, e);
        }
    }

    public List<LongTermInjury> loadAllActiveForPlayer(UUID playerUuid) {
        List<LongTermInjury> result = new ArrayList<>();
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM long_term_injuries "
                            + "WHERE player_uuid = ? AND active = 1 "
                            + "ORDER BY applied_at_ms ASC")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load all active conditions for " + playerUuid, e);
        }
        return result;
    }

    public List<LongTermInjury> loadHistory(UUID playerUuid) {
        List<LongTermInjury> result = new ArrayList<>();
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM long_term_injuries "
                            + "WHERE player_uuid = ? "
                            + "ORDER BY applied_at_ms DESC")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load condition history for " + playerUuid, e);
        }
        return result;
    }

    @Override
    public void save(LongTermInjury injury) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO long_term_injuries ("
                            + "injury_id, player_uuid, tier, applied_at_ms, expires_at_ms, "
                            + "sessions_completed, last_treatment_ms, active, "
                            + "condition_key, display_name_override, description_override, "
                            + "requires_surgery, treatment_steps_completed, "
                            + "resolved_by, resolution_note, is_death_cause, applied_by, notes, "
                            + "effects_suppressed_until_ms, last_milk_use_ms"
                            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                            + "ON CONFLICT(injury_id) DO UPDATE SET "
                            + "expires_at_ms                = excluded.expires_at_ms, "
                            + "sessions_completed           = excluded.sessions_completed, "
                            + "last_treatment_ms            = excluded.last_treatment_ms, "
                            + "active                       = excluded.active, "
                            + "display_name_override        = excluded.display_name_override, "
                            + "description_override         = excluded.description_override, "
                            + "treatment_steps_completed    = excluded.treatment_steps_completed, "
                            + "resolved_by                  = excluded.resolved_by, "
                            + "resolution_note              = excluded.resolution_note, "
                            + "notes                        = excluded.notes, "
                            + "effects_suppressed_until_ms  = excluded.effects_suppressed_until_ms, "
                            + "last_milk_use_ms             = excluded.last_milk_use_ms")) {

                ps.setString(1, injury.getInjuryId());
                ps.setString(2, injury.getPlayerUuid().toString());
                ps.setString(3, injury.getTier().name());
                ps.setLong(4, injury.getAppliedAtMs());
                ps.setLong(5, injury.getExpiresAtMs());
                ps.setInt(6, injury.getSessionsCompleted());
                ps.setLong(7, injury.getLastTreatmentMs());
                ps.setInt(8, injury.isActive() ? 1 : 0);
                setNullableString(ps, 9, injury.getConditionKey());
                setNullableString(ps, 10, injury.getDisplayNameOverride());
                setNullableString(ps, 11, injury.getDescriptionOverride());
                ps.setInt(12, injury.isRequiresSurgery() ? 1 : 0);
                ps.setString(13, injury.getTreatmentStepsCompleted());
                setNullableString(ps, 14, injury.getResolvedBy());
                setNullableString(ps, 15, injury.getResolutionNote());
                ps.setInt(16, injury.isDeathCause() ? 1 : 0);
                setNullableString(ps, 17, injury.getAppliedBy());
                setNullableString(ps, 18, injury.getNotes());
                ps.setLong(19, injury.getEffectsSuppressedUntilMs());
                ps.setLong(20, injury.getLastMilkUseMs());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save condition " + injury.getInjuryId(), e);
        }
    }

    @Override
    public void deactivate(String injuryId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE long_term_injuries SET active = 0 WHERE injury_id = ?")) {
                ps.setString(1, injuryId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate condition " + injuryId, e);
        }
    }

    @Override
    public List<LongTermInjury> loadAllActive() {
        List<LongTermInjury> result = new ArrayList<>();
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM long_term_injuries WHERE active = 1");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load all active conditions", e);
        }
        return result;
    }

    private LongTermInjury mapRow(ResultSet rs) throws SQLException {
        String conditionKey = safeString(rs, "condition_key");
        String displayNameOverride = safeString(rs, "display_name_override");
        String descriptionOverride = safeString(rs, "description_override");
        boolean requiresSurgery = safeInt(rs, "requires_surgery") == 1;
        String treatmentStepsCompleted = safeStringDefault(rs, "treatment_steps_completed", "[]");
        String resolvedBy = safeString(rs, "resolved_by");
        String resolutionNote = safeString(rs, "resolution_note");
        boolean isDeathCause = safeInt(rs, "is_death_cause") == 1;
        String appliedBy = safeString(rs, "applied_by");
        String notes = safeString(rs, "notes");
        long effectsSuppressedUntilMs = safeLong(rs, "effects_suppressed_until_ms");
        long lastMilkUseMs = safeLong(rs, "last_milk_use_ms");

        return new LongTermInjury(
                rs.getString("injury_id"),
                UUID.fromString(rs.getString("player_uuid")),
                LongTermInjuryTier.valueOf(rs.getString("tier")),
                rs.getLong("applied_at_ms"),
                rs.getLong("expires_at_ms"),
                rs.getInt("sessions_completed"),
                rs.getLong("last_treatment_ms"),
                rs.getInt("active") == 1,
                conditionKey,
                displayNameOverride,
                descriptionOverride,
                requiresSurgery,
                treatmentStepsCompleted,
                resolvedBy,
                resolutionNote,
                isDeathCause,
                appliedBy,
                notes,
                effectsSuppressedUntilMs,
                lastMilkUseMs
        );
    }

    private static String safeString(ResultSet rs, String col) {
        try { return rs.getString(col); }
        catch (SQLException ignored) { return null; }
    }

    private static String safeStringDefault(ResultSet rs, String col, String def) {
        try {
            String v = rs.getString(col);
            return v != null ? v : def;
        } catch (SQLException ignored) {
            return def;
        }
    }

    private static int safeInt(ResultSet rs, String col) {
        try { return rs.getInt(col); }
        catch (SQLException ignored) { return 0; }
    }

    private static long safeLong(ResultSet rs, String col) {
        try { return rs.getLong(col); }
        catch (SQLException ignored) { return 0L; }
    }

    private static void setNullableString(PreparedStatement ps, int idx, String value)
            throws SQLException {
        if (value != null) ps.setString(idx, value);
        else ps.setNull(idx, Types.VARCHAR);
    }
}