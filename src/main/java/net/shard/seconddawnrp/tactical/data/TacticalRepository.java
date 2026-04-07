package net.shard.seconddawnrp.tactical.data;

import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.database.DatabaseManager;

import java.sql.*;
import java.util.*;

/**
 * Persistence for ship registry, hardpoints, damage zones, and encounter state.
 */
public class TacticalRepository {

    private final DatabaseManager db;

    public TacticalRepository(DatabaseManager db) {
        this.db = db;
    }

    // ── Ship registry ─────────────────────────────────────────────────────────

    public void saveShipRegistryEntry(ShipRegistryEntry entry) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ship_registry (ship_id, registry_name, ship_class, faction, " +
                            "model_world_key, model_origin_long, real_ship_world_key, real_ship_origin_long, " +
                            "default_spawn_long, default_spawn_world_key, default_pos_x, default_pos_z, default_heading) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                            "ON CONFLICT(ship_id) DO UPDATE SET " +
                            "registry_name=excluded.registry_name, ship_class=excluded.ship_class, " +
                            "faction=excluded.faction, model_world_key=excluded.model_world_key, " +
                            "model_origin_long=excluded.model_origin_long, " +
                            "real_ship_world_key=excluded.real_ship_world_key, " +
                            "real_ship_origin_long=excluded.real_ship_origin_long, " +
                            "default_spawn_long=excluded.default_spawn_long, " +
                            "default_spawn_world_key=excluded.default_spawn_world_key, " +
                            "default_pos_x=excluded.default_pos_x, " +
                            "default_pos_z=excluded.default_pos_z, " +
                            "default_heading=excluded.default_heading")) {
                ps.setString(1, entry.getShipId());
                ps.setString(2, entry.getRegistryName());
                ps.setString(3, entry.getShipClass());
                ps.setString(4, entry.getFaction());
                setNullable(ps, 5, entry.getModelWorldKey());
                ps.setLong(6, entry.getModelOrigin().asLong());
                setNullable(ps, 7, entry.getRealShipWorldKey());
                ps.setLong(8, entry.getRealShipOrigin().asLong());
                ps.setLong(9, entry.getDefaultSpawn().asLong());
                setNullable(ps, 10, entry.getDefaultSpawnWorldKey());
                ps.setDouble(11, entry.getDefaultPosX());
                ps.setDouble(12, entry.getDefaultPosZ());
                ps.setFloat(13, entry.getDefaultHeading());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to save ship registry entry: " + e.getMessage());
        }
    }

    public List<ShipRegistryEntry> loadAllShipRegistry() {
        List<ShipRegistryEntry> result = new ArrayList<>();
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM ship_registry");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ShipRegistryEntry e = new ShipRegistryEntry(
                            rs.getString("ship_id"),
                            rs.getString("registry_name"),
                            rs.getString("ship_class"),
                            rs.getString("faction"));
                    String mwk = rs.getString("model_world_key");
                    if (mwk != null) { e.setModelWorldKey(mwk); e.setModelOrigin(BlockPos.fromLong(rs.getLong("model_origin_long"))); }
                    String rwk = rs.getString("real_ship_world_key");
                    if (rwk != null) { e.setRealShipWorldKey(rwk); e.setRealShipOrigin(BlockPos.fromLong(rs.getLong("real_ship_origin_long"))); }
                    String swk = rs.getString("default_spawn_world_key");
                    if (swk != null) e.setDefaultSpawn(BlockPos.fromLong(rs.getLong("default_spawn_long")), swk);
                    e.setDefaultPosition(rs.getDouble("default_pos_x"), rs.getDouble("default_pos_z"), rs.getFloat("default_heading"));
                    result.add(e);
                }
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to load ship registry: " + e.getMessage());
        }
        return result;
    }

    public boolean deleteShipRegistryEntry(String shipId) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM ship_registry WHERE ship_id = ?")) {
                ps.setString(1, shipId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to delete ship: " + e.getMessage());
            return false;
        }
    }

    // ── Hardpoints ────────────────────────────────────────────────────────────

    public void saveHardpoint(HardpointEntry h) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO hardpoint_registry " +
                            "(hardpoint_id, ship_id, block_pos_long, weapon_type, arc, power_draw, reload_ticks, health) " +
                            "VALUES (?,?,?,?,?,?,?,?) " +
                            "ON CONFLICT(hardpoint_id) DO UPDATE SET health=excluded.health")) {
                ps.setString(1, h.getHardpointId());
                ps.setString(2, h.getShipId());
                ps.setLong(3, h.getBlockPosLong());
                ps.setString(4, h.getWeaponType().name());
                ps.setString(5, h.getArc().name());
                ps.setInt(6, h.getPowerDraw());
                ps.setInt(7, h.getReloadTicks());
                ps.setInt(8, h.getHealth());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to save hardpoint: " + e.getMessage());
        }
    }

    public List<HardpointEntry> loadHardpointsForShip(String shipId) {
        List<HardpointEntry> result = new ArrayList<>();
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM hardpoint_registry WHERE ship_id = ?")) {
                ps.setString(1, shipId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new HardpointEntry(
                                rs.getString("hardpoint_id"),
                                rs.getString("ship_id"),
                                BlockPos.fromLong(rs.getLong("block_pos_long")),
                                HardpointEntry.WeaponType.valueOf(rs.getString("weapon_type")),
                                HardpointEntry.Arc.valueOf(rs.getString("arc")),
                                rs.getInt("power_draw"),
                                rs.getInt("reload_ticks"),
                                rs.getInt("health")));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to load hardpoints: " + e.getMessage());
        }
        return result;
    }

    public boolean deleteHardpoint(String hardpointId) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM hardpoint_registry WHERE hardpoint_id = ?")) {
                ps.setString(1, hardpointId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to delete hardpoint: " + e.getMessage());
            return false;
        }
    }

    // ── Shipyard ──────────────────────────────────────────────────────────────

    public void saveShipyard(String worldKey, double x, double y, double z) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO shipyard_config (id, world_key, spawn_x, spawn_y, spawn_z) " +
                            "VALUES (1, ?, ?, ?, ?) " +
                            "ON CONFLICT(id) DO UPDATE SET world_key=excluded.world_key, " +
                            "spawn_x=excluded.spawn_x, spawn_y=excluded.spawn_y, spawn_z=excluded.spawn_z")) {
                ps.setString(1, worldKey);
                ps.setDouble(2, x);
                ps.setDouble(3, y);
                ps.setDouble(4, z);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to save shipyard: " + e.getMessage());
        }
    }

    public Optional<double[]> loadShipyard() {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM shipyard_config WHERE id = 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new double[]{
                            rs.getDouble("spawn_x"),
                            rs.getDouble("spawn_y"),
                            rs.getDouble("spawn_z")});
                }
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to load shipyard: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<String> loadShipyardWorldKey() {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT world_key FROM shipyard_config WHERE id = 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getString("world_key"));
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to load shipyard world key: " + e.getMessage());
        }
        return Optional.empty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setNullable(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value != null) ps.setString(idx, value);
        else ps.setNull(idx, Types.VARCHAR);
    }
}