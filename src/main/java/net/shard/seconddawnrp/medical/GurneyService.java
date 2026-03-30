package net.shard.seconddawnrp.medical;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V1 gurney transport system.
 *
 * Scope-aligned behavior:
 * - Attach only to downed players
 * - Spawn invisible armor stand with downed indicator name tag
 * - Carrier leads the stand physically
 * - Detach by item reuse or /gurney release
 * - Cleanup on detach and server stop
 */
public class GurneyService {

    private final PlayerProfileManager profileManager;

    /** carrierUuid -> active transport session */
    private final Map<UUID, GurneySession> byCarrier = new ConcurrentHashMap<>();

    /** patientUuid -> carrierUuid */
    private final Map<UUID, UUID> patientToCarrier = new ConcurrentHashMap<>();

    public GurneyService(PlayerProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    // ── Attach ────────────────────────────────────────────────────────────────

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

        if (!isDowned(patient.getUuid())) {
            carrier.sendMessage(Text.literal("[Medical] Gurney can only attach to a downed patient.")
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

        World world = carrier.getWorld();

        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        // Offset stand below ground so patient sits at correct height.
        // ArmorStandEntity mounts passengers ~1.8 blocks above its feet,
        // so we need to sink it ~1.6 blocks to get the patient near ground level.
        stand.setPosition(patient.getX(), patient.getY() - 1.6, patient.getZ());
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setNoGravity(true);
        stand.setSilent(true);

        // Downed indicator — visible to all nearby players
        stand.setCustomName(Text.literal("⬇ DOWN ⬇").formatted(Formatting.RED));
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

            if (!isDowned(patient.getUuid())) {
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

            // Keep stand just in front of carrier, sunk below ground for correct patient height
            Vec3d forward = Vec3d.fromPolar(0.0F, carrier.getYaw()).normalize();
            Vec3d targetPos = carrier.getPos().add(forward.multiply(0.9));
            stand.setPosition(targetPos.x, carrier.getY() - 1.6, targetPos.z);

            // Keep patient mounted
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
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean isCarrier(UUID uuid) {
        return byCarrier.containsKey(uuid);
    }

    public boolean isPatientOnGurney(UUID uuid) {
        return patientToCarrier.containsKey(uuid);
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

    // ── Session record ────────────────────────────────────────────────────────

    public record GurneySession(
            UUID carrierUuid,
            UUID patientUuid,
            UUID standUuid
    ) {}
}