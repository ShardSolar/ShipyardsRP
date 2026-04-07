package net.shard.seconddawnrp.tactical.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tactical.data.EncounterState;
import net.shard.seconddawnrp.tactical.data.ShipState;

import java.util.ArrayList;
import java.util.List;

/**
 * All Tactical packet types in one file.
 *
 * S→C: EncounterUpdatePayload — full encounter state delta sent every tick
 * C→S: WeaponFirePayload     — player fires weapon
 *      HelmInputPayload      — helm heading/speed change
 *      PowerReroutePayload   — Engineering power allocation
 *      ShieldDistributePayload — shield facing distribution
 */
public class TacticalNetworking {

    // ── Shared ship snapshot ──────────────────────────────────────────────────

    public record ShipSnapshot(
            String shipId, String registryName, String combatId, String faction,
            double posX, double posZ, float heading, float speed,
            int hullIntegrity, int hullMax,
            int shieldFore, int shieldAft, int shieldPort, int shieldStarboard,
            int powerBudget, int weaponsPower, int shieldsPower, int enginesPower, int sensorsPower,
            int torpedoCount, int warpSpeed, boolean warpCapable,
            String hullState, boolean destroyed, String controlMode
    ) {
        static ShipSnapshot of(ShipState s) {
            return new ShipSnapshot(
                    s.getShipId(), s.getRegistryName(), s.getCombatId(), s.getFaction(),
                    s.getPosX(), s.getPosZ(), s.getHeading(), s.getSpeed(),
                    s.getHullIntegrity(), s.getHullMax(),
                    s.getShield(ShipState.ShieldFacing.FORE),
                    s.getShield(ShipState.ShieldFacing.AFT),
                    s.getShield(ShipState.ShieldFacing.PORT),
                    s.getShield(ShipState.ShieldFacing.STARBOARD),
                    s.getPowerBudget(), s.getWeaponsPower(), s.getShieldsPower(),
                    s.getEnginesPower(), s.getSensorsPower(),
                    s.getTorpedoCount(), s.getWarpSpeed(), s.isWarpCapable(),
                    s.getHullState().name(), s.isDestroyed(),
                    s.getControlMode().name()
            );
        }

        static void write(PacketByteBuf buf, ShipSnapshot s) {
            buf.writeString(s.shipId()); buf.writeString(s.registryName());
            buf.writeString(s.combatId()); buf.writeString(s.faction());
            buf.writeDouble(s.posX()); buf.writeDouble(s.posZ());
            buf.writeFloat(s.heading()); buf.writeFloat(s.speed());
            buf.writeInt(s.hullIntegrity()); buf.writeInt(s.hullMax());
            buf.writeInt(s.shieldFore()); buf.writeInt(s.shieldAft());
            buf.writeInt(s.shieldPort()); buf.writeInt(s.shieldStarboard());
            buf.writeInt(s.powerBudget()); buf.writeInt(s.weaponsPower());
            buf.writeInt(s.shieldsPower()); buf.writeInt(s.enginesPower());
            buf.writeInt(s.sensorsPower());
            buf.writeInt(s.torpedoCount()); buf.writeInt(s.warpSpeed());
            buf.writeBoolean(s.warpCapable());
            buf.writeString(s.hullState()); buf.writeBoolean(s.destroyed());
            buf.writeString(s.controlMode());
        }

        static ShipSnapshot read(PacketByteBuf buf) {
            return new ShipSnapshot(
                    buf.readString(), buf.readString(), buf.readString(), buf.readString(),
                    buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                    buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readBoolean(),
                    buf.readString(), buf.readBoolean(), buf.readString()
            );
        }
    }

    // ── S→C: Encounter update ─────────────────────────────────────────────────

    public record EncounterUpdatePayload(
            String encounterId,
            String status,
            List<ShipSnapshot> ships,
            List<String> recentLog
    ) implements CustomPayload {
        public static final Id<EncounterUpdatePayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_update"));
        public static final PacketCodec<PacketByteBuf, EncounterUpdatePayload> CODEC =
                PacketCodec.of(EncounterUpdatePayload::write, EncounterUpdatePayload::read);

        @Override public Id<EncounterUpdatePayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeString(encounterId);
            buf.writeString(status);
            buf.writeInt(ships.size());
            ships.forEach(s -> ShipSnapshot.write(buf, s));
            buf.writeInt(recentLog.size());
            recentLog.forEach(buf::writeString);
        }

        static EncounterUpdatePayload read(PacketByteBuf buf) {
            String eid    = buf.readString();
            String status = buf.readString();
            int count     = buf.readInt();
            List<ShipSnapshot> ships = new ArrayList<>(count);
            for (int i = 0; i < count; i++) ships.add(ShipSnapshot.read(buf));
            int logCount  = buf.readInt();
            List<String> log = new ArrayList<>(logCount);
            for (int i = 0; i < logCount; i++) log.add(buf.readString());
            return new EncounterUpdatePayload(eid, status, ships, log);
        }
    }

    // ── C→S: Weapon fire ──────────────────────────────────────────────────────

    public record WeaponFirePayload(
            String encounterId,
            String attackerShipId,
            String targetShipId,
            String weaponType    // PHASER or TORPEDO
    ) implements CustomPayload {
        public static final Id<WeaponFirePayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_weapon_fire"));
        public static final PacketCodec<PacketByteBuf, WeaponFirePayload> CODEC =
                PacketCodec.of(WeaponFirePayload::write, WeaponFirePayload::read);

        @Override public Id<WeaponFirePayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeString(encounterId); buf.writeString(attackerShipId);
            buf.writeString(targetShipId); buf.writeString(weaponType);
        }

        static WeaponFirePayload read(PacketByteBuf buf) {
            return new WeaponFirePayload(buf.readString(), buf.readString(),
                    buf.readString(), buf.readString());
        }
    }

    // ── C→S: Helm input ───────────────────────────────────────────────────────

    public record HelmInputPayload(
            String encounterId,
            String shipId,
            float targetHeading,
            float targetSpeed,
            boolean evasive
    ) implements CustomPayload {
        public static final Id<HelmInputPayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_helm"));
        public static final PacketCodec<PacketByteBuf, HelmInputPayload> CODEC =
                PacketCodec.of(HelmInputPayload::write, HelmInputPayload::read);

        @Override public Id<HelmInputPayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeString(encounterId); buf.writeString(shipId);
            buf.writeFloat(targetHeading); buf.writeFloat(targetSpeed);
            buf.writeBoolean(evasive);
        }

        static HelmInputPayload read(PacketByteBuf buf) {
            return new HelmInputPayload(buf.readString(), buf.readString(),
                    buf.readFloat(), buf.readFloat(), buf.readBoolean());
        }
    }

    // ── C→S: Power reroute ────────────────────────────────────────────────────

    public record PowerReroutePayload(
            String encounterId,
            String shipId,
            int weapons, int shields, int engines, int sensors
    ) implements CustomPayload {
        public static final Id<PowerReroutePayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_power"));
        public static final PacketCodec<PacketByteBuf, PowerReroutePayload> CODEC =
                PacketCodec.of(PowerReroutePayload::write, PowerReroutePayload::read);

        @Override public Id<PowerReroutePayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeString(encounterId); buf.writeString(shipId);
            buf.writeInt(weapons); buf.writeInt(shields);
            buf.writeInt(engines); buf.writeInt(sensors);
        }

        static PowerReroutePayload read(PacketByteBuf buf) {
            return new PowerReroutePayload(buf.readString(), buf.readString(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }
    }

    // ── C→S: Shield distribution ──────────────────────────────────────────────

    public record ShieldDistributePayload(
            String encounterId,
            String shipId,
            int fore, int aft, int port, int starboard
    ) implements CustomPayload {
        public static final Id<ShieldDistributePayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_shields"));
        public static final PacketCodec<PacketByteBuf, ShieldDistributePayload> CODEC =
                PacketCodec.of(ShieldDistributePayload::write, ShieldDistributePayload::read);

        @Override public Id<ShieldDistributePayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeString(encounterId); buf.writeString(shipId);
            buf.writeInt(fore); buf.writeInt(aft);
            buf.writeInt(port); buf.writeInt(starboard);
        }

        static ShieldDistributePayload read(PacketByteBuf buf) {
            return new ShieldDistributePayload(buf.readString(), buf.readString(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(EncounterUpdatePayload.ID, EncounterUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WeaponFirePayload.ID, WeaponFirePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HelmInputPayload.ID, HelmInputPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PowerReroutePayload.ID, PowerReroutePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ShieldDistributePayload.ID, ShieldDistributePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenTacticalPayload.ID, OpenTacticalPayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(WeaponFirePayload.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (SecondDawnRP.TACTICAL_SERVICE == null) return;
                    SecondDawnRP.TACTICAL_SERVICE.queueWeaponFire(
                            payload.encounterId(), payload.attackerShipId(),
                            payload.targetShipId(), payload.weaponType());
                }));

        ServerPlayNetworking.registerGlobalReceiver(HelmInputPayload.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (SecondDawnRP.TACTICAL_SERVICE == null) return;
                    if (payload.evasive()) {
                        SecondDawnRP.TACTICAL_SERVICE.applyEvasiveManeuver(
                                payload.encounterId(), payload.shipId());
                    } else {
                        SecondDawnRP.TACTICAL_SERVICE.applyHelmInput(
                                payload.encounterId(), payload.shipId(),
                                payload.targetHeading(), payload.targetSpeed());
                    }
                }));

        ServerPlayNetworking.registerGlobalReceiver(PowerReroutePayload.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (SecondDawnRP.TACTICAL_SERVICE == null) return;
                    SecondDawnRP.TACTICAL_SERVICE.applyPowerReroute(
                            payload.encounterId(), payload.shipId(),
                            payload.weapons(), payload.shields(),
                            payload.engines(), payload.sensors());
                }));

        ServerPlayNetworking.registerGlobalReceiver(ShieldDistributePayload.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (SecondDawnRP.TACTICAL_SERVICE == null) return;
                    SecondDawnRP.TACTICAL_SERVICE.applyShieldDistribution(
                            payload.encounterId(), payload.shipId(),
                            payload.fore(), payload.aft(),
                            payload.port(), payload.starboard());
                }));
    }

    // ── S→C broadcast ────────────────────────────────────────────────────────

    public static void broadcastEncounterUpdate(EncounterState encounter, MinecraftServer server) {
        if (server == null) return;

        List<ShipSnapshot> snapshots = encounter.getAllShips().stream()
                .map(ShipSnapshot::of)
                .toList();

        EncounterUpdatePayload payload = new EncounterUpdatePayload(
                encounter.getEncounterId(),
                encounter.getStatus().name(),
                snapshots,
                encounter.getRecentLog(20));

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** Send full encounter state to a single player (e.g. on screen open). */
    public static void sendEncounterUpdate(ServerPlayerEntity player, EncounterState encounter) {
        List<ShipSnapshot> snapshots = encounter.getAllShips().stream()
                .map(ShipSnapshot::of)
                .toList();

        ServerPlayNetworking.send(player, new EncounterUpdatePayload(
                encounter.getEncounterId(),
                encounter.getStatus().name(),
                snapshots,
                encounter.getRecentLog(50)));
    }

    // ── S→C: Open screen ──────────────────────────────────────────────────────
    // Added separately — used by TacticalConsoleBlock to open screens on client

    public record OpenTacticalPayload(
            String encounterId,
            String status,
            List<ShipSnapshot> ships,
            List<String> combatLog,
            boolean gmMode
    ) implements CustomPayload {
        public static final Id<OpenTacticalPayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_open"));
        public static final PacketCodec<PacketByteBuf, OpenTacticalPayload> CODEC =
                PacketCodec.of(OpenTacticalPayload::write, OpenTacticalPayload::read);

        @Override public Id<OpenTacticalPayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeString(encounterId); buf.writeString(status);
            buf.writeInt(ships.size());
            ships.forEach(s -> ShipSnapshot.write(buf, s));
            buf.writeInt(combatLog.size());
            combatLog.forEach(buf::writeString);
            buf.writeBoolean(gmMode);
        }

        static OpenTacticalPayload read(PacketByteBuf buf) {
            String eid = buf.readString(); String st = buf.readString();
            int count = buf.readInt(); List<ShipSnapshot> ships = new ArrayList<>(count);
            for (int i = 0; i < count; i++) ships.add(ShipSnapshot.read(buf));
            int lc = buf.readInt(); List<String> log = new ArrayList<>(lc);
            for (int i = 0; i < lc; i++) log.add(buf.readString());
            return new OpenTacticalPayload(eid, st, ships, log, buf.readBoolean());
        }
    }

    public static void sendOpenPacket(ServerPlayerEntity player,
                                      net.shard.seconddawnrp.tactical.data.EncounterState encounter) {
        boolean gmMode = player.hasPermissionLevel(2);
        String encId   = encounter != null ? encounter.getEncounterId() : "STANDBY";
        String status  = encounter != null ? encounter.getStatus().name() : "STANDBY";
        List<ShipSnapshot> ships = encounter != null
                ? encounter.getAllShips().stream().map(ShipSnapshot::of).toList()
                : List.of();
        List<String> log = encounter != null ? encounter.getRecentLog(20) : List.of();

        ServerPlayNetworking.send(player,
                new OpenTacticalPayload(encId, status, ships, log, gmMode));
    }
}