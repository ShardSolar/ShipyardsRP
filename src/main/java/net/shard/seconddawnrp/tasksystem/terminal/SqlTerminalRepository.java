package net.shard.seconddawnrp.tasksystem.terminal;

import net.shard.seconddawnrp.database.DatabaseManager;
import net.shard.seconddawnrp.division.Division;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SqlTerminalRepository implements TaskTerminalRepository {

    private final DatabaseManager databaseManager;

    public SqlTerminalRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public List<TaskTerminalEntry> loadAll() {
        List<TaskTerminalEntry> result = new ArrayList<>();
        String sql = "SELECT world_key, block_pos_long, terminal_type, allowed_divisions "
                + "FROM task_terminals";

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String worldKey     = rs.getString("world_key");
                long blockPosLong   = rs.getLong("block_pos_long");
                TerminalType type   = parseType(rs.getString("terminal_type"));
                List<Division> divs = parseDivisions(rs.getString("allowed_divisions"));

                TaskTerminalEntry entry = new TaskTerminalEntry(
                        worldKey,
                        net.minecraft.util.math.BlockPos.fromLong(blockPosLong),
                        type,
                        divs
                );
                result.add(entry);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public void saveAll(List<TaskTerminalEntry> entries) {
        String upsert = "INSERT INTO task_terminals "
                + "(world_key, block_pos_long, terminal_type, allowed_divisions) "
                + "VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(world_key, block_pos_long) DO UPDATE SET "
                + "terminal_type=excluded.terminal_type, "
                + "allowed_divisions=excluded.allowed_divisions";

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Clear and rewrite — terminals list is small, full replace is safe
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM task_terminals");
                }

                try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                    for (TaskTerminalEntry entry : entries) {
                        ps.setString(1, entry.getWorldKey());
                        ps.setLong(2,   entry.getBlockPosLong());
                        ps.setString(3, entry.getType().name());
                        ps.setString(4, serialiseDivisions(entry.getAllowedDivisions()));
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

    // ── Helpers ───────────────────────────────────────────────────────────

    private TerminalType parseType(String value) {
        try { return TerminalType.valueOf(value); }
        catch (Exception e) { return TerminalType.PUBLIC_BOARD; }
    }

    private List<Division> parseDivisions(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try { return Division.valueOf(s); }
                    catch (Exception e) { return null; }
                })
                .filter(d -> d != null)
                .collect(Collectors.toList());
    }

    private String serialiseDivisions(List<Division> divisions) {
        if (divisions == null || divisions.isEmpty()) return "";
        return divisions.stream()
                .map(Division::name)
                .collect(Collectors.joining(","));
    }
}