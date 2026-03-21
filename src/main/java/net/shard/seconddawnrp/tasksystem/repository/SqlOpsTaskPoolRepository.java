package net.shard.seconddawnrp.tasksystem.repository;

import net.shard.seconddawnrp.database.DatabaseManager;
import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.tasksystem.data.OpsTaskPoolEntry;
import net.shard.seconddawnrp.tasksystem.data.OpsTaskStatus;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqlOpsTaskPoolRepository implements OpsTaskPoolRepository {

    private final DatabaseManager databaseManager;

    public SqlOpsTaskPoolRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public List<OpsTaskPoolEntry> loadAll() {
        List<OpsTaskPoolEntry> result = new ArrayList<>();
        String sql = "SELECT task_id, display_name, description, division, objective_type, "
                + "target_id, required_amount, reward_points, officer_confirmation_required, "
                + "created_by_uuid, created_at, status, assigned_player_uuid, "
                + "assigned_by_uuid, pooled_division, review_note "
                + "FROM ops_task_pool ORDER BY created_at ASC";

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                OpsTaskPoolEntry entry = new OpsTaskPoolEntry(
                        rs.getString("task_id"),
                        rs.getString("display_name"),
                        rs.getString("description"),
                        parseDivision(rs.getString("division")),
                        parseObjectiveType(rs.getString("objective_type")),
                        rs.getString("target_id"),
                        rs.getInt("required_amount"),
                        rs.getInt("reward_points"),
                        rs.getInt("officer_confirmation_required") == 1,
                        parseUuid(rs.getString("created_by_uuid")),
                        rs.getLong("created_at"),
                        parseStatus(rs.getString("status"))
                );

                entry.setAssignedPlayerUuid(parseUuid(rs.getString("assigned_player_uuid")));
                entry.setAssignedByUuid(parseUuid(rs.getString("assigned_by_uuid")));
                entry.setPooledDivision(parseDivisionNullable(rs.getString("pooled_division")));
                entry.setReviewNote(rs.getString("review_note"));

                result.add(entry);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public void saveAll(List<OpsTaskPoolEntry> entries) {
        String upsert = "INSERT INTO ops_task_pool "
                + "(task_id, display_name, description, division, objective_type, target_id, "
                + "required_amount, reward_points, officer_confirmation_required, "
                + "created_by_uuid, created_at, status, assigned_player_uuid, "
                + "assigned_by_uuid, pooled_division, review_note) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT(task_id) DO UPDATE SET "
                + "display_name=excluded.display_name, "
                + "description=excluded.description, "
                + "division=excluded.division, "
                + "objective_type=excluded.objective_type, "
                + "target_id=excluded.target_id, "
                + "required_amount=excluded.required_amount, "
                + "reward_points=excluded.reward_points, "
                + "officer_confirmation_required=excluded.officer_confirmation_required, "
                + "status=excluded.status, "
                + "assigned_player_uuid=excluded.assigned_player_uuid, "
                + "assigned_by_uuid=excluded.assigned_by_uuid, "
                + "pooled_division=excluded.pooled_division, "
                + "review_note=excluded.review_note";

        // Also delete rows that are no longer in the list
        String deleteGone = "DELETE FROM ops_task_pool WHERE task_id NOT IN ";

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Upsert all current entries
                try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                    for (OpsTaskPoolEntry e : entries) {
                        ps.setString(1,  e.getTaskId());
                        ps.setString(2,  e.getDisplayName());
                        ps.setString(3,  e.getDescription());
                        ps.setString(4,  e.getDivision().name());
                        ps.setString(5,  e.getObjectiveType().name());
                        ps.setString(6,  e.getTargetId());
                        ps.setInt(7,     e.getRequiredAmount());
                        ps.setInt(8,     e.getRewardPoints());
                        ps.setInt(9,     e.isOfficerConfirmationRequired() ? 1 : 0);
                        ps.setString(10, e.getCreatedBy() != null
                                ? e.getCreatedBy().toString() : null);ps.setLong(11,   e.getCreatedAtEpochMillis());
                        ps.setString(12, e.getStatus().name());
                        ps.setString(13, e.getAssignedPlayerUuid() != null
                                ? e.getAssignedPlayerUuid().toString() : null);
                        ps.setString(14, e.getAssignedByUuid() != null
                                ? e.getAssignedByUuid().toString() : null);
                        ps.setString(15, e.getPooledDivision() != null
                                ? e.getPooledDivision().name() : null);
                        ps.setString(16, e.getReviewNote());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // Delete any rows whose task_id is no longer in the list
                if (!entries.isEmpty()) {
                    StringBuilder sb = new StringBuilder("DELETE FROM ops_task_pool WHERE task_id NOT IN (");
                    for (int i = 0; i < entries.size(); i++) {
                        sb.append(i == 0 ? "?" : ",?");
                    }
                    sb.append(")");
                    try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                        for (int i = 0; i < entries.size(); i++) {
                            ps.setString(i + 1, entries.get(i).getTaskId());
                        }
                        ps.executeUpdate();
                    }
                } else {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate("DELETE FROM ops_task_pool");
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException e) { return null; }
    }

    private Division parseDivision(String value) {
        try { return Division.valueOf(value); }
        catch (Exception e) { return Division.OPERATIONS; }
    }

    private Division parseDivisionNullable(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Division.valueOf(value); }
        catch (Exception e) { return null; }
    }

    private TaskObjectiveType parseObjectiveType(String value) {
        try { return TaskObjectiveType.valueOf(value); }
        catch (Exception e) { return TaskObjectiveType.MANUAL_CONFIRM; }
    }

    private OpsTaskStatus parseStatus(String value) {
        try { return OpsTaskStatus.valueOf(value); }
        catch (Exception e) { return OpsTaskStatus.UNASSIGNED; }
    }
}