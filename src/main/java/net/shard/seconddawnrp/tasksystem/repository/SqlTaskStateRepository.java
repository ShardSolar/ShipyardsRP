package net.shard.seconddawnrp.tasksystem.repository;

import net.shard.seconddawnrp.database.DatabaseManager;
import net.shard.seconddawnrp.tasksystem.data.ActiveTask;
import net.shard.seconddawnrp.tasksystem.data.CompletedTaskRecord;
import net.shard.seconddawnrp.tasksystem.data.TaskAssignmentSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqlTaskStateRepository implements TaskStateRepository {

    private final DatabaseManager databaseManager;

    public SqlTaskStateRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // ── Active tasks ──────────────────────────────────────────────────────

    @Override
    public List<ActiveTask> loadActiveTasks(UUID playerUuid) {
        List<ActiveTask> result = new ArrayList<>();
        String sql = "SELECT task_id, assigned_by_uuid, assignment_source, "
                + "current_progress, complete, awaiting_officer_approval, reward_claimed "
                + "FROM player_active_tasks WHERE player_uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ActiveTask task = new ActiveTask(
                            rs.getString("task_id"),
                            parseUuid(rs.getString("assigned_by_uuid")),
                            parseSource(rs.getString("assignment_source"))
                    );
                    task.setCurrentProgress(rs.getInt("current_progress"));
                    task.setComplete(rs.getInt("complete") == 1);
                    task.setAwaitingOfficerApproval(rs.getInt("awaiting_officer_approval") == 1);
                    task.setRewardClaimed(rs.getInt("reward_claimed") == 1);
                    result.add(task);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public void saveActiveTasks(UUID playerUuid, List<ActiveTask> activeTasks) {
        String delete = "DELETE FROM player_active_tasks WHERE player_uuid = ?";
        String insert = "INSERT INTO player_active_tasks "
                + "(player_uuid, task_id, assigned_by_uuid, assignment_source, "
                + "current_progress, complete, awaiting_officer_approval, reward_claimed) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(delete)) {
                    ps.setString(1, playerUuid.toString());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    for (ActiveTask task : activeTasks) {
                        ps.setString(1, playerUuid.toString());
                        ps.setString(2, task.getTemplateId());
                        ps.setString(3, task.getAssignedByUuid() != null
                                ? task.getAssignedByUuid().toString() : null);
                        ps.setString(4, task.getAssignmentSource().name());
                        ps.setInt(5, task.getCurrentProgress());
                        ps.setInt(6, task.isComplete() ? 1 : 0);
                        ps.setInt(7, task.isAwaitingOfficerApproval() ? 1 : 0);
                        ps.setInt(8, task.isRewardClaimed() ? 1 : 0);
                        ps.addBatch();
                    }
                    ps.executeBatch();
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

    // ── Completed tasks ───────────────────────────────────────────────────

    @Override
    public List<CompletedTaskRecord> loadCompletedTasks(UUID playerUuid) {
        List<CompletedTaskRecord> result = new ArrayList<>();
        String sql = "SELECT task_id, assigned_by_uuid, assignment_source, "
                + "completed_at, reward_points_granted "
                + "FROM player_completed_tasks WHERE player_uuid = ? "
                + "ORDER BY completed_at ASC";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new CompletedTaskRecord(
                            rs.getString("task_id"),
                            parseUuid(rs.getString("assigned_by_uuid")),
                            parseSource(rs.getString("assignment_source")),
                            rs.getLong("completed_at"),
                            rs.getInt("reward_points_granted")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public void saveCompletedTasks(UUID playerUuid, List<CompletedTaskRecord> completedTasks) {
        // Completed tasks are append-only — we only insert records that don't already exist
        String check = "SELECT COUNT(*) FROM player_completed_tasks "
                + "WHERE player_uuid = ? AND task_id = ? AND completed_at = ?";
        String insert = "INSERT INTO player_completed_tasks "
                + "(player_uuid, task_id, assigned_by_uuid, assignment_source, "
                + "completed_at, reward_points_granted) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (CompletedTaskRecord record : completedTasks) {
                    // Skip if already persisted
                    try (PreparedStatement ps = conn.prepareStatement(check)) {
                        ps.setString(1, playerUuid.toString());
                        ps.setString(2, record.getTemplateId());
                        ps.setLong(3, record.getCompletedAtEpochMillis());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next() && rs.getInt(1) > 0) continue;
                        }
                    }

                    try (PreparedStatement ps = conn.prepareStatement(insert)) {
                        ps.setString(1, playerUuid.toString());
                        ps.setString(2, record.getTemplateId());
                        ps.setString(3, record.getAssignedByUuid() != null
                                ? record.getAssignedByUuid().toString() : null);
                        ps.setString(4, record.getAssignmentSource().name());
                        ps.setLong(5, record.getCompletedAtEpochMillis());
                        ps.setInt(6, record.getRewardPointsGranted());
                        ps.executeUpdate();
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

    @Override
    public void clearPlayerTaskState(UUID playerUuid) {
        String deleteActive    = "DELETE FROM player_active_tasks WHERE player_uuid = ?";
        String deleteCompleted = "DELETE FROM player_completed_tasks WHERE player_uuid = ?";

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(deleteActive)) {
                    ps.setString(1, playerUuid.toString()); ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(deleteCompleted)) {
                    ps.setString(1, playerUuid.toString()); ps.executeUpdate();
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

    private TaskAssignmentSource parseSource(String value) {
        if (value == null) return TaskAssignmentSource.SELF;
        try { return TaskAssignmentSource.valueOf(value); }
        catch (IllegalArgumentException e) { return TaskAssignmentSource.SELF; }
    }
}