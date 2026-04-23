package net.shard.seconddawnrp.degradation.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;
import net.shard.seconddawnrp.degradation.service.DegradationService;
import net.shard.seconddawnrp.warpcore.data.WarpCoreEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sent server → client when the player opens the Engineering PAD.
 *
 * Component scoping (V15):
 *   fromServiceForPlayer() resolves which ship the player is on and sends
 *   only that ship's components. Fallback to all if position not in bounds.
 *
 * Warp core scoping (V15):
 *   Warp cores are filtered by ship binding. If the resolved ship has bound
 *   cores, only those cores are sent. If no cores are bound to the ship,
 *   unbound cores (shipId == null) are sent as fallback. If still nothing,
 *   all cores are sent (legacy / unconfigured setup).
 */
public record OpenEngineeringPadS2CPacket(
        List<ComponentSnapshot> components,
        List<WarpCoreSnapshot> warpCores,
        String focusedCoreId,
        String warpCoreState,
        int warpCoreFuel,
        int warpCoreMaxFuel,
        int warpCorePower
) implements CustomPayload {

    public static final Id<OpenEngineeringPadS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "open_engineering_pad"));

    public record ComponentSnapshot(
            String componentId, String displayName, String worldKey,
            long blockPosLong, int health, ComponentStatus status,
            String repairItemId, int repairItemCount, boolean missingBlock
    ) {}

    public record WarpCoreSnapshot(
            String entryId, String state, int fuel, int maxFuel,
            int power, int coilHealth, int coilCount
    ) {}

    // ── Codecs ────────────────────────────────────────────────────────────────

    private static final PacketCodec<RegistryByteBuf, ComponentSnapshot> SNAPSHOT_CODEC =
            PacketCodec.of(
                    (v, buf) -> {
                        buf.writeString(v.componentId()); buf.writeString(v.displayName());
                        buf.writeString(v.worldKey()); buf.writeLong(v.blockPosLong());
                        buf.writeInt(v.health()); buf.writeString(v.status().name());
                        buf.writeString(v.repairItemId() != null ? v.repairItemId() : "");
                        buf.writeInt(v.repairItemCount()); buf.writeBoolean(v.missingBlock());
                    },
                    buf -> new ComponentSnapshot(
                            buf.readString(), buf.readString(), buf.readString(),
                            buf.readLong(), buf.readInt(),
                            ComponentStatus.valueOf(buf.readString()),
                            buf.readString(), buf.readInt(), buf.readBoolean())
            );

    private static final PacketCodec<RegistryByteBuf, WarpCoreSnapshot> WC_CODEC =
            PacketCodec.of(
                    (v, buf) -> {
                        buf.writeString(v.entryId()); buf.writeString(v.state());
                        buf.writeInt(v.fuel()); buf.writeInt(v.maxFuel());
                        buf.writeInt(v.power()); buf.writeInt(v.coilHealth());
                        buf.writeInt(v.coilCount());
                    },
                    buf -> new WarpCoreSnapshot(
                            buf.readString(), buf.readString(), buf.readInt(),
                            buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt())
            );

    public static final PacketCodec<RegistryByteBuf, OpenEngineeringPadS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeInt(value.components().size());
                        for (ComponentSnapshot s : value.components()) SNAPSHOT_CODEC.encode(buf, s);
                        buf.writeInt(value.warpCores().size());
                        for (WarpCoreSnapshot wc : value.warpCores()) WC_CODEC.encode(buf, wc);
                        buf.writeString(value.focusedCoreId() != null ? value.focusedCoreId() : "");
                        buf.writeString(value.warpCoreState());
                        buf.writeInt(value.warpCoreFuel());
                        buf.writeInt(value.warpCoreMaxFuel());
                        buf.writeInt(value.warpCorePower());
                    },
                    buf -> {
                        int size = buf.readInt();
                        List<ComponentSnapshot> list = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) list.add(SNAPSHOT_CODEC.decode(buf));
                        int wcSize = buf.readInt();
                        List<WarpCoreSnapshot> wcList = new ArrayList<>(wcSize);
                        for (int i = 0; i < wcSize; i++) wcList.add(WC_CODEC.decode(buf));
                        String focusedId = buf.readString();
                        return new OpenEngineeringPadS2CPacket(
                                list, wcList, focusedId,
                                buf.readString(), buf.readInt(), buf.readInt(), buf.readInt());
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    // ── Factories ─────────────────────────────────────────────────────────────

    /**
     * Primary factory — scopes both components and warp cores to the player's ship.
     */
    public static OpenEngineeringPadS2CPacket fromServiceForPlayer(
            DegradationService service, ServerPlayerEntity player) {
        String worldKey = player.getWorld().getRegistryKey().getValue().toString();
        List<ComponentEntry> components = service.getComponentsForPlayer(
                worldKey, player.getX(), player.getY(), player.getZ());

        // Resolve the shipId the player is standing on (may be null)
        String resolvedShipId = resolveShipId(worldKey, player.getX(), player.getY(), player.getZ());

        return buildPacket(service, components, null, resolvedShipId);
    }

    /**
     * Focused on a specific warp core (opened via controller block).
     * Components still scoped to player's ship.
     */
    public static OpenEngineeringPadS2CPacket fromServiceWithCoreForPlayer(
            DegradationService service, ServerPlayerEntity player, String focusedCoreId) {
        String worldKey = player.getWorld().getRegistryKey().getValue().toString();
        List<ComponentEntry> components = service.getComponentsForPlayer(
                worldKey, player.getX(), player.getY(), player.getZ());
        String resolvedShipId = resolveShipId(worldKey, player.getX(), player.getY(), player.getZ());
        return buildPacket(service, components, focusedCoreId, resolvedShipId);
    }

    /**
     * Legacy factory — all components, all unbound warp cores.
     */
    public static OpenEngineeringPadS2CPacket fromService(DegradationService service) {
        return buildPacket(service, new ArrayList<>(service.getAllComponents()), null, null);
    }

    public static OpenEngineeringPadS2CPacket fromServiceWithCore(
            DegradationService service, String focusedCoreId) {
        return buildPacket(service, new ArrayList<>(service.getAllComponents()),
                focusedCoreId, null);
    }

    // ── Ship resolution helper ────────────────────────────────────────────────

    private static String resolveShipId(String worldKey, double x, double y, double z) {
        if (SecondDawnRP.TACTICAL_SERVICE == null) return null;
        return SecondDawnRP.TACTICAL_SERVICE
                .getShipAtPosition(worldKey, x, y, z)
                .map(entry -> entry.getShipId())
                .orElse(null);
    }

    // ── Shared packet builder ─────────────────────────────────────────────────

    private static OpenEngineeringPadS2CPacket buildPacket(
            DegradationService service,
            List<ComponentEntry> componentEntries,
            String focusedCoreId,
            String resolvedShipId) {

        // ── Component snapshots ───────────────────────────────────────────────
        List<ComponentSnapshot> snapshots = new ArrayList<>(componentEntries.size());
        for (ComponentEntry entry : componentEntries) {
            String repairId = entry.getRepairItemId() != null && !entry.getRepairItemId().isEmpty()
                    ? entry.getRepairItemId()
                    : service.getConfig().getDefaultRepairItemId();
            int repairCount = entry.getRepairItemCount() > 0
                    ? entry.getRepairItemCount()
                    : service.getConfig().getDefaultRepairItemCount();
            snapshots.add(new ComponentSnapshot(
                    entry.getComponentId(), entry.getDisplayName(), entry.getWorldKey(),
                    entry.getBlockPosLong(), entry.getHealth(), entry.getStatus(),
                    repairId, repairCount, entry.isMissingBlock()));
        }
        snapshots.sort(Comparator
                .<ComponentSnapshot>comparingInt(s -> statusSortKey(s.status()))
                .thenComparingInt(ComponentSnapshot::health));

        // ── Warp core snapshots (ship-scoped) ─────────────────────────────────
        var wcService = SecondDawnRP.WARP_CORE_SERVICE;
        List<WarpCoreSnapshot> wcSnapshots = new ArrayList<>();
        String wcState = ""; int wcFuel = 0, wcMaxFuel = 64, wcPower = 0;

        if (wcService != null && wcService.isRegistered()) {
            List<WarpCoreEntry> allCores = new ArrayList<>(wcService.getAll());

            // Resolve which cores to show for this ship
            List<WarpCoreEntry> visibleCores;
            if (resolvedShipId != null) {
                // Prefer cores explicitly bound to this ship
                List<WarpCoreEntry> shipBound = allCores.stream()
                        .filter(e -> resolvedShipId.equals(e.getShipId()))
                        .toList();
                if (!shipBound.isEmpty()) {
                    visibleCores = shipBound;
                } else {
                    // Fall back to unbound cores (legacy / not yet assigned)
                    List<WarpCoreEntry> unbound = allCores.stream()
                            .filter(e -> !e.hasShipBinding())
                            .toList();
                    visibleCores = unbound.isEmpty() ? allCores : unbound;
                }
            } else {
                // No ship resolved — show all (fallback for unconfigured setup)
                visibleCores = allCores;
            }

            for (WarpCoreEntry wcEntry : visibleCores) {
                int coilHealth = wcService.getCoilHealth(wcEntry);
                int coilCount  = wcEntry.getResonanceCoilIds().size();
                wcSnapshots.add(new WarpCoreSnapshot(
                        wcEntry.getEntryId(), wcEntry.getState().name(),
                        wcEntry.getFuelRods(), wcService.getConfig().getMaxFuelRods(),
                        wcEntry.getCurrentPowerOutput(), coilHealth, coilCount));
            }

            if (!visibleCores.isEmpty()) {
                WarpCoreEntry first = visibleCores.get(0);
                wcState   = first.getState().name();
                wcFuel    = first.getFuelRods();
                wcMaxFuel = wcService.getConfig().getMaxFuelRods();
                wcPower   = first.getCurrentPowerOutput();
            }
        }

        // If focused on a specific core, filter to just that one
        if (focusedCoreId != null && !focusedCoreId.isEmpty()) {
            List<WarpCoreSnapshot> focused = wcSnapshots.stream()
                    .filter(wc -> wc.entryId().equals(focusedCoreId))
                    .toList();
            if (wcService != null) {
                wcService.getById(focusedCoreId).ifPresent(e -> {
                    // wcState/Fuel/Power already set above; no reassignment needed here
                });
                var entry = wcService.getById(focusedCoreId);
                if (entry.isPresent()) {
                    wcState   = entry.get().getState().name();
                    wcFuel    = entry.get().getFuelRods();
                    wcMaxFuel = wcService.getConfig().getMaxFuelRods();
                    wcPower   = entry.get().getCurrentPowerOutput();
                }
            }
            return new OpenEngineeringPadS2CPacket(
                    snapshots, focused, focusedCoreId,
                    wcState, wcFuel, wcMaxFuel, wcPower);
        }

        return new OpenEngineeringPadS2CPacket(
                snapshots, wcSnapshots, null,
                wcState, wcFuel, wcMaxFuel, wcPower);
    }

    private static int statusSortKey(ComponentStatus status) {
        return switch (status) {
            case OFFLINE  -> 0;
            case CRITICAL -> 1;
            case DEGRADED -> 2;
            case NOMINAL  -> 3;
        };
    }
}