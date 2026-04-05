package net.shard.seconddawnrp.transporter;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.database.DatabaseManager;
import net.shard.seconddawnrp.dimension.LocationService;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core transporter service.
 * Manages ready state, beam-up queue, ship locations, and teleport execution.
 */
public class TransporterService {

    private static final long BEAMUP_EXPIRY_MS = 5 * 60 * 1000L;

    private final DatabaseManager databaseManager;
    private final LocationService locationService;
    private MinecraftServer server;

    private final Set<UUID> readyPlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, BeamUpRequest> beamUpRequests = new ConcurrentHashMap<>();
    private final Map<String, ShipLocation> shipLocations = new ConcurrentHashMap<>();

    public TransporterService(DatabaseManager databaseManager, LocationService locationService) {
        this.databaseManager = databaseManager;
        this.locationService = locationService;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── Startup ───────────────────────────────────────────────────────────────

    public void loadFromDatabase() {
        loadShipLocations();
        loadPendingBeamUpRequests();
        System.out.println("[SecondDawnRP] TransporterService: loaded "
                + shipLocations.size() + " ship location(s), "
                + beamUpRequests.size() + " pending beam-up request(s).");
    }

    private void loadShipLocations() {
        shipLocations.clear();
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name, x, y, z, world_key FROM ship_locations")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        shipLocations.put(rs.getString("name"), new ShipLocation(
                                rs.getString("name"),
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                rs.getString("world_key")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[SecondDawnRP] Failed to load ship locations: " + e.getMessage());
        }
    }

    private void loadPendingBeamUpRequests() {
        beamUpRequests.clear();
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM beam_up_requests WHERE status = 'PENDING'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        BeamUpRequest req = new BeamUpRequest(
                                rs.getString("request_id"),
                                UUID.fromString(rs.getString("player_uuid")),
                                rs.getString("player_name"),
                                rs.getString("source_dimension"),
                                rs.getLong("requested_at")
                        );
                        beamUpRequests.put(req.getRequestId(), req);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[SecondDawnRP] Failed to load beam-up requests: " + e.getMessage());
        }
    }

    // ── Ready state ───────────────────────────────────────────────────────────

    public boolean toggleReady(UUID playerUuid) {
        if (readyPlayers.contains(playerUuid)) {
            readyPlayers.remove(playerUuid);
            return false;
        } else {
            readyPlayers.add(playerUuid);
            return true;
        }
    }

    public boolean isReady(UUID playerUuid) { return readyPlayers.contains(playerUuid); }

    public void clearReady(UUID playerUuid) { readyPlayers.remove(playerUuid); }

    public List<ServerPlayerEntity> getReadyPlayers() {
        if (server == null) return List.of();
        List<ServerPlayerEntity> result = new ArrayList<>();
        for (UUID uuid : readyPlayers) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) result.add(player);
        }
        return result;
    }

    // ── Beam-up requests ──────────────────────────────────────────────────────

    public BeamUpRequest submitBeamUpRequest(ServerPlayerEntity player) {
        for (BeamUpRequest existing : beamUpRequests.values()) {
            if (existing.getPlayerUuid().equals(player.getUuid())
                    && existing.getStatus() == BeamUpRequest.Status.PENDING) {
                return existing;
            }
        }

        String dimensionId = player.getWorld().getRegistryKey().getValue().getPath();
        String requestId = UUID.randomUUID().toString();
        BeamUpRequest req = new BeamUpRequest(
                requestId, player.getUuid(), player.getName().getString(),
                dimensionId, System.currentTimeMillis()
        );

        beamUpRequests.put(requestId, req);
        persistBeamUpRequest(req);
        return req;
    }

    public boolean approveBeamUpRequest(String requestId, ServerPlayerEntity operator) {
        BeamUpRequest req = beamUpRequests.get(requestId);
        if (req == null || req.getStatus() != BeamUpRequest.Status.PENDING) return false;
        if (server == null) return false;

        ServerPlayerEntity target = server.getPlayerManager().getPlayer(req.getPlayerUuid());
        if (target == null) { expireRequest(requestId); return false; }

        target.teleport(operator.getServerWorld(),
                operator.getX(), operator.getY(), operator.getZ(),
                operator.getYaw(), operator.getPitch());

        target.sendMessage(Text.literal(
                        "[Transporter] Beam-up approved. Welcome back aboard.")
                .formatted(Formatting.GREEN), false);
        operator.sendMessage(Text.literal(
                        "[Transporter] " + req.getPlayerName() + " has been beamed aboard.")
                .formatted(Formatting.GREEN), false);

        req.approve(operator.getUuid().toString());
        updateRequestStatus(requestId, BeamUpRequest.Status.APPROVED,
                operator.getUuid().toString());
        beamUpRequests.remove(requestId);
        return true;
    }

    public List<BeamUpRequest> getPendingRequests() {
        long now = System.currentTimeMillis();
        new ArrayList<>(beamUpRequests.values()).stream()
                .filter(r -> r.isExpired(BEAMUP_EXPIRY_MS))
                .forEach(r -> expireRequest(r.getRequestId()));

        return beamUpRequests.values().stream()
                .filter(r -> r.getStatus() == BeamUpRequest.Status.PENDING)
                .sorted(Comparator.comparingLong(BeamUpRequest::getRequestedAt))
                .toList();
    }

    private void expireRequest(String requestId) {
        BeamUpRequest req = beamUpRequests.remove(requestId);
        if (req != null) {
            req.expire();
            updateRequestStatus(requestId, BeamUpRequest.Status.EXPIRED, null);
            if (server != null) {
                ServerPlayerEntity player =
                        server.getPlayerManager().getPlayer(req.getPlayerUuid());
                if (player != null) {
                    player.sendMessage(Text.literal(
                                    "[Transporter] Your beam-up request has expired. Try again.")
                            .formatted(Formatting.YELLOW), false);
                }
            }
        }
    }

    // ── Ship locations ────────────────────────────────────────────────────────

    public void addShipLocation(String name, double x, double y, double z,
                                String worldKey, String registeredBy) {
        ShipLocation loc = new ShipLocation(name, x, y, z, worldKey);
        shipLocations.put(name, loc);
        persistShipLocation(loc, registeredBy);
    }

    public void removeShipLocation(String name) {
        shipLocations.remove(name);
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM ship_locations WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[SecondDawnRP] Failed to remove ship location: " + e.getMessage());
        }
    }

    public Map<String, ShipLocation> getShipLocations() {
        return Collections.unmodifiableMap(shipLocations);
    }

    public Optional<ShipLocation> getShipLocation(String name) {
        return Optional.ofNullable(shipLocations.get(name));
    }

    // ── Teleport ──────────────────────────────────────────────────────────────

    public int transportToShipLocation(List<ServerPlayerEntity> players, String locationName) {
        if (server == null) return 0;
        ShipLocation loc = shipLocations.get(locationName);
        if (loc == null) return 0;

        var worldKey = net.minecraft.registry.RegistryKey.of(
                net.minecraft.registry.RegistryKeys.WORLD,
                net.minecraft.util.Identifier.of(loc.worldKey()));
        var world = server.getWorld(worldKey);
        if (world == null) return 0;

        int count = 0;
        for (ServerPlayerEntity player : players) {
            player.teleport(world, loc.x(), loc.y(), loc.z(),
                    player.getYaw(), player.getPitch());
            player.sendMessage(Text.literal(
                            "[Transporter] Energizing... transported to " + loc.name() + ".")
                    .formatted(Formatting.AQUA), false);
            count++;
        }
        return count;
    }

    public int transportToDimension(List<ServerPlayerEntity> players, String dimensionId) {
        int count = 0;
        for (ServerPlayerEntity player : players) {
            if (locationService.teleportToEntryPoint(player, dimensionId)) {
                player.sendMessage(Text.literal("[Transporter] Energizing... stand by.")
                        .formatted(Formatting.AQUA), false);
                count++;
            }
        }
        return count;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void persistBeamUpRequest(BeamUpRequest req) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO beam_up_requests "
                            + "(request_id, player_uuid, player_name, source_dimension, requested_at, status) "
                            + "VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, req.getRequestId());
                ps.setString(2, req.getPlayerUuid().toString());
                ps.setString(3, req.getPlayerName());
                ps.setString(4, req.getSourceDimension());
                ps.setLong(5, req.getRequestedAt());
                ps.setString(6, req.getStatus().name());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[SecondDawnRP] Failed to persist beam-up request: " + e.getMessage());
        }
    }

    private void updateRequestStatus(String requestId,
                                     BeamUpRequest.Status status, String handledBy) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE beam_up_requests SET status=?, handled_by=?, handled_at=? "
                            + "WHERE request_id=?")) {
                ps.setString(1, status.name());
                if (handledBy != null) ps.setString(2, handledBy);
                else ps.setNull(2, Types.VARCHAR);
                ps.setLong(3, System.currentTimeMillis());
                ps.setString(4, requestId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[SecondDawnRP] Failed to update beam-up status: " + e.getMessage());
        }
    }

    private void persistShipLocation(ShipLocation loc, String registeredBy) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ship_locations (name, x, y, z, world_key, registered_by, registered_at) "
                            + "VALUES (?,?,?,?,?,?,?) "
                            + "ON CONFLICT(name) DO UPDATE SET x=excluded.x, y=excluded.y, "
                            + "z=excluded.z, world_key=excluded.world_key")) {
                ps.setString(1, loc.name());
                ps.setDouble(2, loc.x());
                ps.setDouble(3, loc.y());
                ps.setDouble(4, loc.z());
                ps.setString(5, loc.worldKey());
                ps.setString(6, registeredBy);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[SecondDawnRP] Failed to persist ship location: " + e.getMessage());
        }
    }

    // ── Data records ──────────────────────────────────────────────────────────

    public record ShipLocation(String name, double x, double y, double z, String worldKey) {}
}