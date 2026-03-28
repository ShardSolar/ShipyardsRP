package net.shard.seconddawnrp.progression;

import net.shard.seconddawnrp.database.DatabaseManager;
import net.shard.seconddawnrp.division.Rank;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite repository for officer slot queue entries.
 * Table created by DatabaseMigrations V7.
 */
public class SqlSlotQueueRepository {

    private final DatabaseManager db;

    public SqlSlotQueueRepository(DatabaseManager db) {
        this.db = db;
    }

    public void add(SlotQueueEntry entry) {
        String sql = """
            INSERT OR REPLACE INTO officer_slot_queue
              (player_uuid, target_rank, service_record, queued_at, time_at_rank)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.getPlayerUuid().toString());
            ps.setString(2, entry.getTargetRank().name());
            ps.setLong(3, entry.getServiceRecord());
            ps.setLong(4, entry.getQueuedAt());
            ps.setLong(5, entry.getTimeAtRank());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[SecondDawnRP] SlotQueue add failed: " + e.getMessage());
        }
    }

    public void remove(UUID playerUuid, Rank targetRank) {
        String sql = "DELETE FROM officer_slot_queue WHERE player_uuid = ? AND target_rank = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, targetRank.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[SecondDawnRP] SlotQueue remove failed: " + e.getMessage());
        }
    }

    /**
     * Returns the queue for a target rank, ordered by serviceRecord DESC then timeAtRank DESC.
     * First entry is the next player to be promoted when a slot opens.
     */
    public List<SlotQueueEntry> getQueue(Rank targetRank) {
        String sql = """
            SELECT player_uuid, target_rank, service_record, queued_at, time_at_rank
            FROM officer_slot_queue
            WHERE target_rank = ?
            ORDER BY service_record DESC, time_at_rank DESC
            """;
        List<SlotQueueEntry> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, targetRank.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new SlotQueueEntry(
                            UUID.fromString(rs.getString("player_uuid")),
                            Rank.valueOf(rs.getString("target_rank")),
                            rs.getLong("service_record"),
                            rs.getLong("queued_at"),
                            rs.getLong("time_at_rank")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("[SecondDawnRP] SlotQueue getQueue failed: " + e.getMessage());
        }
        return result;
    }

    public boolean isQueued(UUID playerUuid, Rank targetRank) {
        String sql = "SELECT 1 FROM officer_slot_queue WHERE player_uuid = ? AND target_rank = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, targetRank.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }
}