package net.shard.seconddawnrp.warpcore.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.warpcore.data.ReactorState;
import net.shard.seconddawnrp.warpcore.data.WarpCoreEntry;
import net.shard.seconddawnrp.warpcore.service.WarpCoreService;

/**
 * Sent server → client to populate the Warp Core Monitor screen.
 * Also broadcast to all Engineering players when state changes.
 */
public record WarpCoreStatusS2CPacket(
        boolean registered,
        String state,
        int fuelRods,
        int maxFuelRods,
        int powerOutput,
        int fuelPercent,
        int stability,
        int coilHealth,
        String fuelLabel
) implements CustomPayload {

    public static final Id<WarpCoreStatusS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "warpcore_status"));

    public static final PacketCodec<RegistryByteBuf, WarpCoreStatusS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeBoolean(value.registered());
                        buf.writeString(value.state());
                        buf.writeInt(value.fuelRods());
                        buf.writeInt(value.maxFuelRods());
                        buf.writeInt(value.powerOutput());
                        buf.writeInt(value.fuelPercent());
                        buf.writeInt(value.stability());
                        buf.writeInt(value.coilHealth());
                        buf.writeString(value.fuelLabel());
                    },
                    buf -> new WarpCoreStatusS2CPacket(
                            buf.readBoolean(),
                            buf.readString(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readString()
                    )
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    public static WarpCoreStatusS2CPacket fromService(WarpCoreService service) {
        if (!service.isRegistered()) {
            return new WarpCoreStatusS2CPacket(
                    false, ReactorState.OFFLINE.name(), 0,
                    service.getConfig().getMaxFuelRods(), 0, 0, 0, 100, "Fuel Rods");
        }
        WarpCoreEntry entry = service.getEntry().get();
        var adapter = service.getAdapter();
        int coilHealth = entry.getResonanceCoilComponentId() != null
                ? SecondDawnRP.DEGRADATION_SERVICE
                .getById(entry.getResonanceCoilComponentId())
                .map(c -> c.getHealth()).orElse(100) : 100;

        return new WarpCoreStatusS2CPacket(
                true,
                entry.getState().name(),
                entry.getFuelRods(),
                service.getConfig().getMaxFuelRods(),
                entry.getCurrentPowerOutput(),
                adapter != null ? adapter.getFuelLevelPercent() : 0,
                adapter != null ? adapter.getStability() : 0,
                coilHealth,
                adapter != null ? adapter.getPrimaryFuelLabel() : "Fuel Rods"
        );
    }
}