package net.shard.seconddawnrp.medical;

import net.shard.seconddawnrp.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists medical terminal visitor records in the {@code medical_terminal_visitors} table
 * created in V8.
 */
public final class SqlMedicalTerminalRepository {

    private final DatabaseManager db;

    public SqlMedicalTerminalRepository(DatabaseManager db) {
        this.db = db;
    }

    /** Upsert a visitor record — inserts on first visit, updates last_visit_at on return. */
    public void upsertVisitor(UUID playerUuid, long now) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO medical_terminal_visitors (player_uuid, first_visit_at, last_visit_at) "
                            + "VALUES (?, ?, ?) "
                            + "ON CONFLICT(player_uuid) DO UPDATE SET last_visit_at = excluded.last_visit_at")) {
                ps.setString(1, playerUuid.toString());
                ps.setLong(2, now);
                ps.setLong(3, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert medical terminal visitor " + playerUuid, e);
        }
    }

    public boolean isVisitor(UUID playerUuid) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM medical_terminal_visitors WHERE player_uuid = ? LIMIT 1")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check medical visitor " + playerUuid, e);
        }
    }

    public List<UUID> loadAllVisitors() {
        List<UUID> result = new ArrayList<>();
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT player_uuid FROM medical_terminal_visitors "
                            + "ORDER BY first_visit_at ASC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(UUID.fromString(rs.getString("player_uuid")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load all medical terminal visitors", e);
        }
        return result;
    }
}