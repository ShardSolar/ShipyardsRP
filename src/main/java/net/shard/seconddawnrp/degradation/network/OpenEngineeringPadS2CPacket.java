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

/*
 * Sent server → client when the player right-clicks with the Engineering PAD
 * in air. Carries a snapshot of all registered components so the client
 * screen can render them without round-tripping.
 *
 * <p>Each component is encoded as a lightweight {@link ComponentSnapshot}
 * record containing only what the screen needs — no UUIDs or timestamps.
 */
public record OpenEngineeringPadS2CPacket(
        List<ComponentSnapshot> components
) implements CustomPayload {

    public static final Id<OpenEngineeringPadS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "open_engineering_pad"));

    /*
     * Lightweight view of a component for screen rendering.
     *
     * @param componentId unique component ID
     * @param displayName human-readable name
     * @param worldKey    registry key of the world (e.g. {@code minecraft:overworld})
     * @param health      current health 0–100
     * @param status      current {@link ComponentStatus}
     */
    public record ComponentSnapshot(
            String componentId,
            String displayName,
            String worldKey,
            int health,
            ComponentStatus status
    ) {}

    // ── Codec ─────────────────────────────────────────────────────────────────

    private static final PacketCodec<RegistryByteBuf, ComponentSnapshot> SNAPSHOT_CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, ComponentSnapshot::componentId,
                    PacketCodecs.STRING, ComponentSnapshot::displayName,
                    PacketCodecs.STRING, ComponentSnapshot::worldKey,
                    PacketCodecs.INTEGER, ComponentSnapshot::health,
                    PacketCodecs.STRING.xmap(ComponentStatus::valueOf, ComponentStatus::name),
                    ComponentSnapshot::status,
                    ComponentSnapshot::new
            );

    public static final PacketCodec<RegistryByteBuf, OpenEngineeringPadS2CPacket> CODEC =
            PacketCodec.tuple(
                    SNAPSHOT_CODEC.collect(PacketCodecs.toList()),
                    OpenEngineeringPadS2CPacket::components,
                    OpenEngineeringPadS2CPacket::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /*
     * Build a packet from the live service, sorted worst-first so the most
     * critical components appear at the top of the screen.
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
        // Sort: OFFLINE first, then CRITICAL, DEGRADED, NOMINAL; within status by health asc
        snapshots.sort(Comparator
                .<ComponentSnapshot>comparingInt(s -> statusSortKey(s.status()))
                .thenComparingInt(ComponentSnapshot::health));
        return new OpenEngineeringPadS2CPacket(snapshots);
    }

    private static int statusSortKey(ComponentStatus status) {
        return switch (status) {
            case OFFLINE -> 0;
            case CRITICAL -> 1;
            case DEGRADED -> 2;
            case NOMINAL -> 3;
        };
    }
}