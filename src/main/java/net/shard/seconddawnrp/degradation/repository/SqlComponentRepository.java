package net.shard.seconddawnrp.degradation.repository;

import net.shard.seconddawnrp.database.DatabaseManager;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL-backed component repository targeting the {@code components} table
 * added in schema version 3.
 *
 * <p>This is the migration-path implementation. It is wired in once
 * the server has been running schema v3 without issues. Until then,
 * {@link JsonComponentRepository} is the primary backend.
 */
public class SqlComponentRepository implements ComponentRepository {

    private final DatabaseManager db;

    public SqlComponentRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(ComponentEntry entry) {
        String sql = "INSERT OR REPLACE INTO components ("
                + "component_id, world_key, block_pos_long, block_type_id, display_name, "
                + "health, status, last_drain_tick_ms, last_task_generated_ms, registered_by_uuid, "
                + "repair_item_id, repair_item_count"
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.getComponentId());
            ps.setString(2, entry.getWorldKey());
            ps.setLong(3, entry.getBlockPosLong());
            ps.setString(4, entry.getBlockTypeId());
            ps.setString(5, entry.getDisplayName());
            ps.setInt(6, entry.getHealth());
            ps.setString(7, entry.getStatus().name());
            ps.setLong(8, entry.getLastDrainTickMs());
            ps.setLong(9, entry.getLastTaskGeneratedMs());
            ps.setString(10, entry.getRegisteredByUuid() != null
                    ? entry.getRegisteredByUuid().toString() : null);
            ps.setString(11, entry.getRepairItemId());
            ps.setInt(12, entry.getRepairItemCount());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save component " + entry.getComponentId(), e);
        }
    }

    @Override
    public void saveAll(Collection<ComponentEntry> entries) {
        String sql = "INSERT OR REPLACE INTO components ("
                + "component_id, world_key, block_pos_long, block_type_id, display_name, "
                + "health, status, last_drain_tick_ms, last_task_generated_ms, registered_by_uuid, "
                + "repair_item_id, repair_item_count"
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (ComponentEntry entry : entries) {
                ps.setString(1, entry.getComponentId());
                ps.setString(2, entry.getWorldKey());
                ps.setLong(3, entry.getBlockPosLong());
                ps.setString(4, entry.getBlockTypeId());
                ps.setString(5, entry.getDisplayName());
                ps.setInt(6, entry.getHealth());
                ps.setString(7, entry.getStatus().name());
                ps.setLong(8, entry.getLastDrainTickMs());
                ps.setLong(9, entry.getLastTaskGeneratedMs());
                ps.setString(10, entry.getRegisteredByUuid() != null
                        ? entry.getRegisteredByUuid().toString() : null);
                ps.setString(11, entry.getRepairItemId());
                ps.setInt(12, entry.getRepairItemCount());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to bulk-save components", e);
        }
    }

    @Override
    public Collection<ComponentEntry> loadAll() {
        List<ComponentEntry> result = new ArrayList<>();
        String sql = "SELECT * FROM components";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(fromResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load all components", e);
        }
        return result;
    }

    @Override
    public Optional<ComponentEntry> findById(String componentId) {
        String sql = "SELECT * FROM components WHERE component_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, componentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find component by id: " + componentId, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ComponentEntry> findByPosition(String worldKey, long blockPosLong) {
        String sql = "SELECT * FROM components WHERE world_key = ? AND block_pos_long = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, worldKey);
            ps.setLong(2, blockPosLong);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find component by position", e);
        }
        return Optional.empty();
    }

    @Override
    public void delete(String componentId) {
        String sql = "DELETE FROM components WHERE component_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, componentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete component: " + componentId, e);
        }
    }

    @Override
    public void deleteAll() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM components")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete all components", e);
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private static ComponentEntry fromResultSet(ResultSet rs) throws SQLException {
        String uuidStr = rs.getString("registered_by_uuid");
        return new ComponentEntry(
                rs.getString("component_id"),
                rs.getString("world_key"),
                rs.getLong("block_pos_long"),
                rs.getString("block_type_id"),
                rs.getString("display_name"),
                rs.getInt("health"),
                ComponentStatus.valueOf(rs.getString("status")),
                rs.getLong("last_drain_tick_ms"),
                rs.getLong("last_task_generated_ms"),
                uuidStr != null ? UUID.fromString(uuidStr) : null,
                rs.getString("repair_item_id"),
                rs.getInt("repair_item_count")
        );
    }
}