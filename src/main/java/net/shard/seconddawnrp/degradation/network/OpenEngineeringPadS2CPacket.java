package net.shard.seconddawnrp.degradation.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;
import net.shard.seconddawnrp.degradation.service.DegradationService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sent server -> client when the player right-clicks with the Engineering PAD
 * in air. Carries a snapshot of all registered components so the client
 * screen can render them without a round-trip.
 */
public record OpenEngineeringPadS2CPacket(
        List<ComponentSnapshot> components,
        String warpCoreState,
        int warpCoreFuel,
        int warpCoreMaxFuel,
        int warpCorePower
) implements CustomPayload {

    public static final Id<OpenEngineeringPadS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "open_engineering_pad"));

    /**
     * Lightweight view of a component for screen rendering.
     */
    public record ComponentSnapshot(
            String componentId,
            String displayName,
            String worldKey,
            int health,
            ComponentStatus status
    ) {}

    // ── Codecs ────────────────────────────────────────────────────────────────

    private static final PacketCodec<RegistryByteBuf, ComponentSnapshot> SNAPSHOT_CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeString(value.componentId());
                        buf.writeString(value.displayName());
                        buf.writeString(value.worldKey());
                        buf.writeInt(value.health());
                        buf.writeString(value.status().name());
                    },
                    buf -> new ComponentSnapshot(
                            buf.readString(),
                            buf.readString(),
                            buf.readString(),
                            buf.readInt(),
                            ComponentStatus.valueOf(buf.readString())
                    )
            );

    public static final PacketCodec<RegistryByteBuf, OpenEngineeringPadS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeInt(value.components().size());
                        for (ComponentSnapshot snap : value.components()) {
                            SNAPSHOT_CODEC.encode(buf, snap);
                        }
                        buf.writeString(value.warpCoreState());
                        buf.writeInt(value.warpCoreFuel());
                        buf.writeInt(value.warpCoreMaxFuel());
                        buf.writeInt(value.warpCorePower());
                    },
                    buf -> {
                        int size = buf.readInt();
                        List<ComponentSnapshot> list = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            list.add(SNAPSHOT_CODEC.decode(buf));
                        }
                        return new OpenEngineeringPadS2CPacket(
                                list,
                                buf.readString(),
                                buf.readInt(),
                                buf.readInt(),
                                buf.readInt()
                        );
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Build a packet from the live service, sorted worst-first.
     */
    public static OpenEngineeringPadS2CPacket fromService(DegradationService service) {
        List<ComponentSnapshot> snapshots = new ArrayList<>();
        for (ComponentEntry entry : service.getAllComponents()) {
            snapshots.add(new ComponentSnapshot(
                    entry.getComponentId(),
                    entry.getDisplayName(),
                    entry.getWorldKey(),
                    entry.getHealth(),
                    entry.getStatus()
            ));
        }
        snapshots.sort(Comparator
                .<ComponentSnapshot>comparingInt(s -> statusSortKey(s.status()))
                .thenComparingInt(ComponentSnapshot::health));
        // Warp core snapshot
        String wcState = "OFFLINE";
        int wcFuel = 0, wcMaxFuel = 64, wcPower = 0;
        if (net.shard.seconddawnrp.SecondDawnRP.WARP_CORE_SERVICE != null
                && net.shard.seconddawnrp.SecondDawnRP.WARP_CORE_SERVICE.isRegistered()) {
            var wc = net.shard.seconddawnrp.SecondDawnRP.WARP_CORE_SERVICE.getEntry().get();
            wcState   = wc.getState().name();
            wcFuel    = wc.getFuelRods();
            wcMaxFuel = net.shard.seconddawnrp.SecondDawnRP.WARP_CORE_SERVICE.getConfig().getMaxFuelRods();
            wcPower   = wc.getCurrentPowerOutput();
        }
        return new OpenEngineeringPadS2CPacket(snapshots, wcState, wcFuel, wcMaxFuel, wcPower);
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