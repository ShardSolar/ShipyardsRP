package net.shard.seconddawnrp.progression;

import net.shard.seconddawnrp.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ServiceRecordRepository {

    private final DatabaseManager databaseManager;

    public ServiceRecordRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void save(ServiceRecordEntry entry) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO service_record_entries "
                            + "(entry_id, player_uuid, timestamp, type, points_delta, "
                            + " actor_uuid, actor_name, reason, division_context) "
                            + "VALUES (?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, entry.getEntryId());
                ps.setString(2, entry.getPlayerUuid().toString());
                ps.setLong(3, entry.getTimestamp());
                ps.setString(4, entry.getType().name());
                ps.setInt(5, entry.getPointsDelta());
                setNullable(ps, 6, entry.getActorUuid());
                setNullable(ps, 7, entry.getActorName());
                ps.setString(8, entry.getReason());
                ps.setString(9, entry.getDivisionContext());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[SecondDawnRP] Failed to save service record entry: "
                    + e.getMessage());
        }
    }

    /** Load all entries for a player, newest first. */
    public List<ServiceRecordEntry> loadForPlayer(UUID playerUuid) {
        List<ServiceRecordEntry> result = new ArrayList<>();
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM service_record_entries "
                            + "WHERE player_uuid = ? ORDER BY timestamp DESC")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[SecondDawnRP] Failed to load service record: "
                    + e.getMessage());
        }
        return result;
    }

    /** Load only commendations (points_delta > 0). */
    public List<ServiceRecordEntry> loadCommendations(UUID playerUuid) {
        return loadFiltered(playerUuid,
                "WHERE player_uuid = ? AND points_delta > 0 ORDER BY timestamp DESC");
    }

    /** Load only demerits (points_delta < 0). */
    public List<ServiceRecordEntry> loadDemerits(UUID playerUuid) {
        return loadFiltered(playerUuid,
                "WHERE player_uuid = ? AND points_delta < 0 ORDER BY timestamp DESC");
    }

    /** Load career events (promotions, transfers, etc.) — not commendations/demerits. */
    public List<ServiceRecordEntry> loadCareerEvents(UUID playerUuid) {
        return loadFiltered(playerUuid,
                "WHERE player_uuid = ? AND type NOT IN ('COMMENDATION','DEMERIT') "
                        + "ORDER BY timestamp DESC");
    }

    private List<ServiceRecordEntry> loadFiltered(UUID playerUuid, String whereClause) {
        List<ServiceRecordEntry> result = new ArrayList<>();
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM service_record_entries " + whereClause)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[SecondDawnRP] Failed to load filtered service record: "
                    + e.getMessage());
        }
        return result;
    }

    private ServiceRecordEntry mapRow(ResultSet rs) throws SQLException {
        return new ServiceRecordEntry(
                rs.getString("entry_id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getLong("timestamp"),
                ServiceRecordEntry.Type.valueOf(rs.getString("type")),
                rs.getInt("points_delta"),
                rs.getString("actor_uuid"),
                rs.getString("actor_name"),
                rs.getString("reason"),
                rs.getString("division_context")
        );
    }

    private void setNullable(PreparedStatement ps, int idx, String value)
            throws SQLException {
        if (value != null) ps.setString(idx, value);
        else ps.setNull(idx, Types.VARCHAR);
    }
}