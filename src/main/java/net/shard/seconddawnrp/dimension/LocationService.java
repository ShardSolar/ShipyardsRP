package net.shard.seconddawnrp.dimension;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import net.shard.seconddawnrp.database.DatabaseManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dimension activation state and session-level entry point overrides.
 *
 * Activation state is persisted in the dimension_registry DB table.
 * Entry point overrides are session-only — reset to JSON defaults on restart.
 *
 * Phase 12 hook: proximityCheck() currently always returns true.
 * When the Tactical map is built, it will call TacticalService.isInRange(dimensionId).
 */
public class LocationService {

    private final LocationRegistry registry;
    private final DatabaseManager databaseManager;
    private MinecraftServer server;

    /** Persisted activation state — loaded from DB on startup. */
    private final Set<String> activeDimensions = ConcurrentHashMap.newKeySet();

    /** Session-only entry point overrides — cleared on restart. */
    private final Map<String, double[]> entryOverrides = new ConcurrentHashMap<>();

    public LocationService(LocationRegistry registry, DatabaseManager databaseManager) {
        this.registry = registry;
        this.databaseManager = databaseManager;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── Startup ───────────────────────────────────────────────────────────────

    public void loadFromDatabase() {
        activeDimensions.clear();
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT dimension_id FROM dimension_registry WHERE active = 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("dimension_id");
                        if (registry.exists(id)) {
                            activeDimensions.add(id);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[SecondDawnRP] Failed to load dimension registry: "
                    + e.getMessage());
        }
        System.out.println("[SecondDawnRP] LocationService: "
                + activeDimensions.size() + " active dimension(s).");
    }

    // ── Activation ────────────────────────────────────────────────────────────

    public boolean activate(String dimensionId) {
        if (!registry.exists(dimensionId)) return false;
        activeDimensions.add(dimensionId);
        persistActivation(dimensionId, true);
        return true;
    }

    public boolean deactivate(String dimensionId) {
        if (!registry.exists(dimensionId)) return false;
        activeDimensions.remove(dimensionId);
        persistActivation(dimensionId, false);
        return true;
    }

    public boolean isActive(String dimensionId) {
        return activeDimensions.contains(dimensionId);
    }

    /**
     * Returns true if this dimension can currently be reached via transporter.
     * Phase 12: add && TACTICAL_SERVICE.isInRange(dimensionId) when available.
     */
    public boolean isReachable(String dimensionId) {
        if (!isActive(dimensionId)) return false;
        Optional<LocationDefinition> def = registry.get(dimensionId);
        if (def.isEmpty()) return false;
        return proximityCheck(dimensionId, def.get());
    }

    /**
     * Phase 12 hook — always returns true until Tactical map is built.
     * When Phase 12 ships: check def.proximityRequired() and query tactical service.
     */
    @SuppressWarnings("unused")
    private boolean proximityCheck(String dimensionId, LocationDefinition def) {
        // TODO Phase 12: if (def.proximityRequired()) return TACTICAL_SERVICE.isInRange(dimensionId);
        return true;
    }

    // ── Entry point overrides ─────────────────────────────────────────────────

    /** Session-only override — resets to JSON default on restart. */
    public void setEntryPoint(String dimensionId, double x, double y, double z) {
        entryOverrides.put(dimensionId, new double[]{x, y, z});
    }

    /** Returns the effective entry point: override if set, else JSON default. */
    public double[] getEntryPoint(String dimensionId) {
        if (entryOverrides.containsKey(dimensionId)) {
            return entryOverrides.get(dimensionId);
        }
        return registry.get(dimensionId)
                .map(def -> new double[]{def.defaultEntryX(), def.defaultEntryY(), def.defaultEntryZ()})
                .orElse(new double[]{0.5, 64.0, 0.5});
    }

    // ── Teleportation ─────────────────────────────────────────────────────────

    /**
     * Teleports a player to a dimension at the given coordinates.
     * Works regardless of activation state — used by GM /tp command.
     */
    public boolean teleportPlayer(ServerPlayerEntity player,
                                  String dimensionId,
                                  double x, double y, double z) {
        if (server == null) return false;

        RegistryKey<World> worldKey = RegistryKey.of(
                RegistryKeys.WORLD, Identifier.of("seconddawnrp", dimensionId));
        ServerWorld targetWorld = server.getWorld(worldKey);

        if (targetWorld == null) {
            System.err.println("[SecondDawnRP] Dimension not found: seconddawnrp:" + dimensionId);
            return false;
        }

        player.teleport(targetWorld, x, y, z,
                player.getYaw(), player.getPitch());
        return true;
    }

    /**
     * Teleports a player to a dimension using the registered entry point.
     */
    public boolean teleportToEntryPoint(ServerPlayerEntity player, String dimensionId) {
        double[] entry = getEntryPoint(dimensionId);
        return teleportPlayer(player, dimensionId, entry[0], entry[1], entry[2]);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<LocationDefinition> getAllDimensions() {
        return registry.getAll();
    }

    public List<LocationDefinition> getReachableDimensions() {
        return registry.getAll().stream()
                .filter(def -> isReachable(def.dimensionId()))
                .toList();
    }

    public Optional<LocationDefinition> get(String dimensionId) {
        return registry.get(dimensionId);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void persistActivation(String dimensionId, boolean active) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO dimension_registry (dimension_id, active) VALUES (?, ?) "
                            + "ON CONFLICT(dimension_id) DO UPDATE SET active = excluded.active")) {
                ps.setString(1, dimensionId);
                ps.setInt(2, active ? 1 : 0);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[SecondDawnRP] Failed to persist dimension activation: "
                    + e.getMessage());
        }
    }
}