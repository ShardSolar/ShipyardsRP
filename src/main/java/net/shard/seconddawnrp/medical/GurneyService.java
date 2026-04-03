package net.shard.seconddawnrp.medical;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gurney transport system.
 *
 * Behavior:
 * - Downed players can be picked up immediately without confirmation.
 * - Players with medical conditions can be picked up, but must confirm first.
 * - Healthy players cannot be placed on a gurney.
 * - Carrier leads the stand physically.
 * - Detach by item reuse or /gurney release.
 * - Cleanup on detach and server stop.
 */
public class GurneyService {

    private static final long PICKUP_REQUEST_TIMEOUT_MS = 20_000L;

    private final PlayerProfileManager profileManager;

    /** carrierUuid -> active transport session */
    private final Map<UUID, GurneySession> byCarrier = new ConcurrentHashMap<>();

    /** patientUuid -> carrierUuid */
    private final Map<UUID, UUID> patientToCarrier = new ConcurrentHashMap<>();

    /** patientUuid -> pending request */
    private final Map<UUID, PendingPickupRequest> pendingRequests = new ConcurrentHashMap<>();

    public GurneyService(PlayerProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    // ── Attach / request ─────────────────────────────────────────────────────

    /**
     * Main entrypoint:
     * - downed patient => immediate attach
     * - patient with condition => request confirmation
     * - otherwise reject
     */
    public boolean attach(ServerPlayerEntity carrier, ServerPlayerEntity patient) {
        if (carrier.getUuid().equals(patient.getUuid())) {
            carrier.sendMessage(Text.literal("[Medical] You cannot place yourself on a gurney.")
                    .formatted(Formatting.RED), false);
            return false;
        }

        if (carrier.getServerWorld() != patient.getServerWorld()) {
            carrier.sendMessage(Text.literal("[Medical] Patient must be in the same world.")
                    .formatted(Formatting.RED), false);
            return false;
        }

        if (byCarrier.containsKey(carrier.getUuid())) {
            carrier.sendMessage(Text.literal("[Medical] You are already transporting a patient.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        if (patientToCarrier.containsKey(patient.getUuid())) {
            carrier.sendMessage(Text.literal("[Medical] That patient is already on a gurney.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        boolean downed = isDowned(patient.getUuid());
        boolean hasCondition = hasAnyMedicalCondition(patient.getUuid());

        if (!downed && !hasCondition) {
            carrier.sendMessage(Text.literal("[Medical] Gurney can only pick up downed patients or patients with a medical condition.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        if (downed) {
            return attachImmediate(carrier, patient);
        }

        return requestAttach(carrier, patient);
    }

    /**
     * Sends a confirmation request to a conscious patient with a medical condition.
     */
    public boolean requestAttach(ServerPlayerEntity carrier, ServerPlayerEntity patient) {
        if (carrier.getUuid().equals(patient.getUuid())) {
            carrier.sendMessage(Text.literal("[Medical] You cannot place yourself on a gurney.")
                    .formatted(Formatting.RED), false);
            return false;
        }

        if (carrier.getServerWorld() != patient.getServerWorld()) {
            carrier.sendMessage(Text.literal("[Medical] Patient must be in the same world.")
                    .formatted(Formatting.RED), false);
            return false;
        }

        if (isDowned(patient.getUuid())) {
            return attachImmediate(carrier, patient);
        }

        if (!hasAnyMedicalCondition(patient.getUuid())) {
            carrier.sendMessage(Text.literal("[Medical] That player has no medical condition and does not need a gurney.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        if (byCarrier.containsKey(carrier.getUuid())) {
            carrier.sendMessage(Text.literal("[Medical] You are already transporting a patient.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        if (patientToCarrier.containsKey(patient.getUuid())) {
            carrier.sendMessage(Text.literal("[Medical] That patient is already on a gurney.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        PendingPickupRequest existing = pendingRequests.get(patient.getUuid());
        if (existing != null && !isExpired(existing)) {
            carrier.sendMessage(Text.literal("[Medical] That patient already has a pending gurney request.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        PendingPickupRequest request = new PendingPickupRequest(
                carrier.getUuid(),
                patient.getUuid(),
                carrier.getServerWorld().getRegistryKey().getValue().toString(),
                System.currentTimeMillis()
        );
        pendingRequests.put(patient.getUuid(), request);

        carrier.sendMessage(Text.literal("[Medical] Gurney pickup request sent to "
                        + patient.getName().getString() + ".")
                .formatted(Formatting.AQUA), false);

        patient.sendMessage(
                Text.literal("[Medical] ")
                        .formatted(Formatting.AQUA)
                        .append(Text.literal(carrier.getName().getString()).formatted(Formatting.WHITE))
                        .append(Text.literal(" wants to place you on a gurney. Use ")
                                .formatted(Formatting.AQUA))
                        .append(Text.literal("/gurney accept").formatted(Formatting.GREEN))
                        .append(Text.literal(" or ").formatted(Formatting.GRAY))
                        .append(Text.literal("/gurney deny").formatted(Formatting.RED)),
                false
        );

        return true;
    }

    /**
     * Patient accepts a pending pickup request.
     */
    public boolean acceptPendingPickup(ServerPlayerEntity patient) {
        PendingPickupRequest request = pendingRequests.get(patient.getUuid());
        if (request == null) {
            patient.sendMessage(Text.literal("[Medical] You have no pending gurney request.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        if (isExpired(request)) {
            pendingRequests.remove(patient.getUuid());
            patient.sendMessage(Text.literal("[Medical] That gurney request expired.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        MinecraftServer server = patient.getServer();
        if (server == null) {
            pendingRequests.remove(patient.getUuid());
            return false;
        }

        ServerPlayerEntity carrier = server.getPlayerManager().getPlayer(request.carrierUuid());
        if (carrier == null || !carrier.isAlive()) {
            pendingRequests.remove(patient.getUuid());
            patient.sendMessage(Text.literal("[Medical] The carrier is no longer available.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        pendingRequests.remove(patient.getUuid());
        return attachImmediate(carrier, patient);
    }

    /**
     * Patient denies a pending pickup request.
     */
    public boolean denyPendingPickup(ServerPlayerEntity patient) {
        PendingPickupRequest request = pendingRequests.remove(patient.getUuid());
        if (request == null) {
            patient.sendMessage(Text.literal("[Medical] You have no pending gurney request.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        MinecraftServer server = patient.getServer();
        if (server != null) {
            ServerPlayerEntity carrier = server.getPlayerManager().getPlayer(request.carrierUuid());
            if (carrier != null) {
                carrier.sendMessage(Text.literal("[Medical] "
                                + patient.getName().getString()
                                + " declined the gurney request.")
                        .formatted(Formatting.YELLOW), false);
            }
        }

        patient.sendMessage(Text.literal("[Medical] Gurney request denied.")
                .formatted(Formatting.YELLOW), false);
        return true;
    }

    /**
     * Actual attach implementation. Used for downed patients immediately and
     * for conscious patients after acceptance.
     */
    private boolean attachImmediate(ServerPlayerEntity carrier, ServerPlayerEntity patient) {
        if (carrier.getUuid().equals(patient.getUuid())) {
            carrier.sendMessage(Text.literal("[Medical] You cannot place yourself on a gurney.")
                    .formatted(Formatting.RED), false);
            return false;
        }

        if (carrier.getServerWorld() != patient.getServerWorld()) {
            carrier.sendMessage(Text.literal("[Medical] Patient must be in the same world.")
                    .formatted(Formatting.RED), false);
            return false;
        }

        if (byCarrier.containsKey(carrier.getUuid())) {
            carrier.sendMessage(Text.literal("[Medical] You are already transporting a patient.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        if (patientToCarrier.containsKey(patient.getUuid())) {
            carrier.sendMessage(Text.literal("[Medical] That patient is already on a gurney.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        if (!isDowned(patient.getUuid()) && !hasAnyMedicalCondition(patient.getUuid())) {
            carrier.sendMessage(Text.literal("[Medical] That patient is not eligible for gurney transport.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        World world = carrier.getWorld();

        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        stand.setPosition(patient.getX(), patient.getY() - 1.6, patient.getZ());
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setNoGravity(true);
        stand.setSilent(true);

        if (isDowned(patient.getUuid())) {
            stand.setCustomName(Text.literal("⬇ DOWN ⬇").formatted(Formatting.RED));
        } else {
            stand.setCustomName(Text.literal("✚ PATIENT ✚").formatted(Formatting.AQUA));
        }
        stand.setCustomNameVisible(true);

        world.spawnEntity(stand);

        if (!patient.startRiding(stand, true)) {
            stand.discard();
            carrier.sendMessage(Text.literal("[Medical] Failed to attach patient to gurney.")
                    .formatted(Formatting.RED), false);
            return false;
        }

        GurneySession session = new GurneySession(
                carrier.getUuid(),
                patient.getUuid(),
                stand.getUuid()
        );

        byCarrier.put(carrier.getUuid(), session);
        patientToCarrier.put(patient.getUuid(), carrier.getUuid());
        pendingRequests.remove(patient.getUuid());

        carrier.sendMessage(Text.literal("[Medical] Patient loaded onto gurney.")
                .formatted(Formatting.GREEN), false);
        patient.sendMessage(Text.literal("[Medical] You have been placed on a gurney.")
                .formatted(Formatting.AQUA), false);

        return true;
    }

    // ── Detach ────────────────────────────────────────────────────────────────

    public boolean detachByCarrier(ServerPlayerEntity carrier) {
        GurneySession session = byCarrier.get(carrier.getUuid());
        if (session == null) {
            carrier.sendMessage(Text.literal("[Medical] You are not transporting anyone.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        detachInternal(carrier.getServer(), session, true);
        carrier.sendMessage(Text.literal("[Medical] Patient released from gurney.")
                .formatted(Formatting.GREEN), false);
        return true;
    }

    public boolean detachByPatient(ServerPlayerEntity patient) {
        UUID carrierUuid = patientToCarrier.get(patient.getUuid());
        if (carrierUuid == null) {
            patient.sendMessage(Text.literal("[Medical] You are not on a gurney.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        GurneySession session = byCarrier.get(carrierUuid);
        if (session == null) {
            patientToCarrier.remove(patient.getUuid());
            patient.sendMessage(Text.literal("[Medical] Gurney state was already cleared.")
                    .formatted(Formatting.YELLOW), false);
            return false;
        }

        detachInternal(patient.getServer(), session, true);
        patient.sendMessage(Text.literal("[Medical] You have been released from the gurney.")
                .formatted(Formatting.GREEN), false);
        return true;
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        if (!pendingRequests.isEmpty()) {
            for (PendingPickupRequest request : new ArrayList<>(pendingRequests.values())) {
                if (isExpired(request)) {
                    pendingRequests.remove(request.patientUuid());

                    ServerPlayerEntity carrier = server.getPlayerManager().getPlayer(request.carrierUuid());
                    ServerPlayerEntity patient = server.getPlayerManager().getPlayer(request.patientUuid());

                    if (carrier != null) {
                        carrier.sendMessage(Text.literal("[Medical] Gurney request expired.")
                                .formatted(Formatting.YELLOW), false);
                    }
                    if (patient != null) {
                        patient.sendMessage(Text.literal("[Medical] Gurney request expired.")
                                .formatted(Formatting.YELLOW), false);
                    }
                }
            }
        }

        if (byCarrier.isEmpty()) return;

        List<GurneySession> toDetach = new ArrayList<>();

        for (GurneySession session : byCarrier.values()) {
            ServerPlayerEntity carrier = server.getPlayerManager().getPlayer(session.carrierUuid());
            ServerPlayerEntity patient = server.getPlayerManager().getPlayer(session.patientUuid());

            if (carrier == null || patient == null) {
                toDetach.add(session);
                continue;
            }

            if (!carrier.isAlive() || !patient.isAlive()) {
                toDetach.add(session);
                continue;
            }

            if (!isDowned(patient.getUuid()) && !hasAnyMedicalCondition(patient.getUuid())) {
                toDetach.add(session);
                continue;
            }

            if (carrier.getServerWorld() != patient.getServerWorld()) {
                toDetach.add(session);
                continue;
            }

            ArmorStandEntity stand = findStand(carrier.getServerWorld(), session.standUuid());
            if (stand == null || !stand.isAlive()) {
                toDetach.add(session);
                continue;
            }

            Vec3d forward = Vec3d.fromPolar(0.0F, carrier.getYaw()).normalize();
            Vec3d targetPos = carrier.getPos().add(forward.multiply(0.9));
            stand.setPosition(targetPos.x, carrier.getY() - 1.6, targetPos.z);

            if (patient.getVehicle() != stand) {
                patient.startRiding(stand, true);
            }
        }

        for (GurneySession session : toDetach) {
            detachInternal(server, session, false);
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void cleanupAll(MinecraftServer server) {
        for (GurneySession session : new ArrayList<>(byCarrier.values())) {
            detachInternal(server, session, false);
        }
        byCarrier.clear();
        patientToCarrier.clear();
        pendingRequests.clear();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean isCarrier(UUID uuid) {
        return byCarrier.containsKey(uuid);
    }

    public boolean isPatientOnGurney(UUID uuid) {
        return patientToCarrier.containsKey(uuid);
    }

    public boolean hasPendingPickupRequest(UUID patientUuid) {
        PendingPickupRequest request = pendingRequests.get(patientUuid);
        return request != null && !isExpired(request);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void detachInternal(MinecraftServer server, GurneySession session,
                                boolean placePatientSafely) {
        byCarrier.remove(session.carrierUuid());
        patientToCarrier.remove(session.patientUuid());

        ServerPlayerEntity patient = server.getPlayerManager().getPlayer(session.patientUuid());
        ServerPlayerEntity carrier = server.getPlayerManager().getPlayer(session.carrierUuid());

        ArmorStandEntity stand = null;
        if (carrier != null) {
            stand = findStand(carrier.getServerWorld(), session.standUuid());
        } else if (patient != null) {
            stand = findStand(patient.getServerWorld(), session.standUuid());
        }

        if (patient != null) {
            patient.stopRiding();

            if (placePatientSafely && carrier != null) {
                Vec3d forward = Vec3d.fromPolar(0.0F, carrier.getYaw()).normalize();
                Vec3d drop = carrier.getPos().add(forward.multiply(1.2));
                patient.teleport(drop.x, carrier.getY(), drop.z, true);
            }
        }

        if (stand != null) {
            stand.discard();
        }
    }

    private ArmorStandEntity findStand(World world, UUID uuid) {
        if (world == null || uuid == null) return null;
        return (ArmorStandEntity) ((net.minecraft.server.world.ServerWorld) world).getEntity(uuid);
    }

    private boolean isDowned(UUID playerUuid) {
        return net.shard.seconddawnrp.SecondDawnRP.DOWNED_SERVICE != null
                && net.shard.seconddawnrp.SecondDawnRP.DOWNED_SERVICE.isDowned(playerUuid);
    }

    private boolean hasAnyMedicalCondition(UUID playerUuid) {
        PlayerProfile profile = profileManager.getLoadedProfile(playerUuid);
        if (profile == null) return false;

        return !profile.getActiveMedicalConditionIds().isEmpty();
    }

    private boolean isExpired(PendingPickupRequest request) {
        return System.currentTimeMillis() - request.createdAtEpochMs() > PICKUP_REQUEST_TIMEOUT_MS;
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record GurneySession(
            UUID carrierUuid,
            UUID patientUuid,
            UUID standUuid
    ) {}

    public record PendingPickupRequest(
            UUID carrierUuid,
            UUID patientUuid,
            String worldKey,
            long createdAtEpochMs
    ) {}
}