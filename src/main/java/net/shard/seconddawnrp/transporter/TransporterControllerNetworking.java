package net.shard.seconddawnrp.transporter;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.dimension.LocationDefinition;
import net.shard.seconddawnrp.dimension.LocationService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-side networking for the Transporter Controller screen.
 * S→C: OpenPayload — sends all controller data to client
 * C→S: ActionPayload — transport/approve actions from client
 */
public class TransporterControllerNetworking {

    // ── Payload types ─────────────────────────────────────────────────────────

    public record ReadyPlayerData(String playerName, String playerUuid) {}
    public record DestinationData(String id, String displayName, boolean available) {}
    public record BeamUpData(String requestId, String playerName,
                             String sourceDimension, long requestedAt) {}

    public record OpenPayload(
            BlockPos controllerPos,
            List<ReadyPlayerData> readyPlayers,
            List<DestinationData> dimensions,
            List<DestinationData> shipLocations,
            List<BeamUpData> beamUpRequests
    ) implements CustomPayload {
        public static final Id<OpenPayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "transporter_open"));
        public static final PacketCodec<PacketByteBuf, OpenPayload> CODEC =
                PacketCodec.of(OpenPayload::write, OpenPayload::read);

        @Override public Id<OpenPayload> getId() { return ID; }

        static OpenPayload read(PacketByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            List<ReadyPlayerData> ready = buf.readList(b ->
                    new ReadyPlayerData(b.readString(), b.readString()));
            List<DestinationData> dims = buf.readList(b ->
                    new DestinationData(b.readString(), b.readString(), b.readBoolean()));
            List<DestinationData> locs = buf.readList(b ->
                    new DestinationData(b.readString(), b.readString(), b.readBoolean()));
            List<BeamUpData> reqs = buf.readList(b ->
                    new BeamUpData(b.readString(), b.readString(), b.readString(), b.readLong()));
            return new OpenPayload(pos, ready, dims, locs, reqs);
        }

        void write(PacketByteBuf buf) {
            buf.writeBlockPos(controllerPos);
            buf.writeCollection(readyPlayers, (b, p) -> {
                b.writeString(p.playerName());
                b.writeString(p.playerUuid());
            });
            buf.writeCollection(dimensions, (b, d) -> {
                b.writeString(d.id());
                b.writeString(d.displayName());
                b.writeBoolean(d.available());
            });
            buf.writeCollection(shipLocations, (b, d) -> {
                b.writeString(d.id());
                b.writeString(d.displayName());
                b.writeBoolean(d.available());
            });
            buf.writeCollection(beamUpRequests, (b, r) -> {
                b.writeString(r.requestId());
                b.writeString(r.playerName());
                b.writeString(r.sourceDimension());
                b.writeLong(r.requestedAt());
            });
        }
    }

    public enum ActionType { TRANSPORT_DIMENSION, TRANSPORT_LOCATION, APPROVE_BEAMUP }

    public record ActionPayload(
            ActionType action,
            String targetId,
            List<String> playerUuids
    ) implements CustomPayload {
        public static final Id<ActionPayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "transporter_action"));
        public static final PacketCodec<PacketByteBuf, ActionPayload> CODEC =
                PacketCodec.of(ActionPayload::write, ActionPayload::read);

        @Override public Id<ActionPayload> getId() { return ID; }

        static ActionPayload read(PacketByteBuf buf) {
            ActionType action = ActionType.values()[buf.readVarInt()];
            String target = buf.readString();
            List<String> players = buf.readList(PacketByteBuf::readString);
            return new ActionPayload(action, target, players);
        }

        void write(PacketByteBuf buf) {
            buf.writeVarInt(action.ordinal());
            buf.writeString(targetId);
            buf.writeCollection(playerUuids, PacketByteBuf::writeString);
        }
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(OpenPayload.ID, OpenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
    }

    public static void registerServerReceivers(TransporterService transporterService,
                                               LocationService locationService) {
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) -> {
            ServerPlayerEntity operator = context.player();
            context.server().execute(() ->
                    handleAction(payload, operator, transporterService, locationService));
        });
    }

    private static void handleAction(ActionPayload payload,
                                     ServerPlayerEntity operator,
                                     TransporterService transporterService,
                                     LocationService locationService) {
        switch (payload.action()) {
            case TRANSPORT_DIMENSION -> {
                List<ServerPlayerEntity> targets = resolveTargets(payload.playerUuids(), operator);
                int count = transporterService.transportToDimension(targets, payload.targetId());
                operator.sendMessage(Text.literal(
                                "[Transporter] Transported " + count + " player(s) to " + payload.targetId())
                        .formatted(Formatting.GREEN), false);
                targets.forEach(p -> transporterService.clearReady(p.getUuid()));
            }
            case TRANSPORT_LOCATION -> {
                List<ServerPlayerEntity> targets = resolveTargets(payload.playerUuids(), operator);
                int count = transporterService.transportToShipLocation(targets, payload.targetId());
                operator.sendMessage(Text.literal(
                                "[Transporter] Transported " + count + " player(s) to " + payload.targetId())
                        .formatted(Formatting.GREEN), false);
                targets.forEach(p -> transporterService.clearReady(p.getUuid()));
            }
            case APPROVE_BEAMUP -> {
                boolean ok = transporterService.approveBeamUpRequest(payload.targetId(), operator);
                if (!ok) {
                    operator.sendMessage(Text.literal(
                                    "[Transporter] Request not found or already handled.")
                            .formatted(Formatting.YELLOW), false);
                }
            }
        }
    }

    private static List<ServerPlayerEntity> resolveTargets(List<String> uuids,
                                                           ServerPlayerEntity operator) {
        if (operator.getServer() == null) return List.of();
        List<ServerPlayerEntity> result = new ArrayList<>();
        for (String uuidStr : uuids) {
            try {
                ServerPlayerEntity player = operator.getServer()
                        .getPlayerManager().getPlayer(UUID.fromString(uuidStr));
                if (player != null) result.add(player);
            } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    // ── S→C: send controller data ─────────────────────────────────────────────

    public static void sendOpenPacket(ServerPlayerEntity operator, BlockPos controllerPos) {
        TransporterService ts = net.shard.seconddawnrp.SecondDawnRP.TRANSPORTER_SERVICE;
        LocationService ls    = net.shard.seconddawnrp.SecondDawnRP.LOCATION_SERVICE;
        if (ts == null || ls == null) return;

        List<ReadyPlayerData> ready = ts.getReadyPlayers().stream()
                .map(p -> new ReadyPlayerData(p.getName().getString(), p.getUuid().toString()))
                .toList();

        List<DestinationData> dims = ls.getAllDimensions().stream()
                .map(def -> new DestinationData(
                        def.dimensionId(), def.displayName(), ls.isReachable(def.dimensionId())))
                .toList();

        List<DestinationData> locs = ts.getShipLocations().values().stream()
                .map(loc -> new DestinationData(loc.name(), loc.name(), true))
                .toList();

        List<BeamUpData> reqs = ts.getPendingRequests().stream()
                .map(r -> new BeamUpData(r.getRequestId(), r.getPlayerName(),
                        r.getSourceDimension(), r.getRequestedAt()))
                .toList();

        ServerPlayNetworking.send(operator,
                new OpenPayload(controllerPos, ready, dims, locs, reqs));
    }
}