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
 * Carries entryId so the screen knows which core to target for button actions.
 */
public record WarpCoreStatusS2CPacket(
        String entryId,
        boolean registered,
        String state,
        int fuelRods,
        int maxFuelRods,
        int powerOutput,
        int fuelPercent,
        int stability,
        int coilHealth,
        int coilCount,
        String fuelLabel
) implements CustomPayload {

    public static final Id<WarpCoreStatusS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "warpcore_status"));

    public static final PacketCodec<RegistryByteBuf, WarpCoreStatusS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeString(value.entryId() != null ? value.entryId() : "");
                        buf.writeBoolean(value.registered());
                        buf.writeString(value.state());
                        buf.writeInt(value.fuelRods());
                        buf.writeInt(value.maxFuelRods());
                        buf.writeInt(value.powerOutput());
                        buf.writeInt(value.fuelPercent());
                        buf.writeInt(value.stability());
                        buf.writeInt(value.coilHealth());
                        buf.writeInt(value.coilCount());
                        buf.writeString(value.fuelLabel());
                    },
                    buf -> new WarpCoreStatusS2CPacket(
                            buf.readString(),  // entryId
                            buf.readBoolean(),
                            buf.readString(),
                            buf.readInt(),
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

    public static WarpCoreStatusS2CPacket fromEntry(WarpCoreEntry entry, int coilHealth) {
        var service = SecondDawnRP.WARP_CORE_SERVICE;
        // Force an adapter refresh so cable state is current when player opens screen
        service.refreshAdapterForEntry(entry);
        var adapter = service.getAdapter(entry.getEntryId());
        return new WarpCoreStatusS2CPacket(
                entry.getEntryId(),
                true,
                entry.getState().name(),
                entry.getFuelRods(),
                service.getConfig().getMaxFuelRods(),
                entry.getCurrentPowerOutput(),
                adapter != null ? adapter.getFuelLevelPercent() : 0,
                adapter != null ? adapter.getStability() : 0,
                coilHealth,
                entry.getResonanceCoilIds().size(),
                adapter != null ? adapter.getPrimaryFuelLabel() : "Fuel Rods"
        );
    }

    public static WarpCoreStatusS2CPacket fromService(WarpCoreService service) {
        if (!service.isRegistered()) {
            return new WarpCoreStatusS2CPacket("", false,
                    ReactorState.OFFLINE.name(), 0,
                    service.getConfig().getMaxFuelRods(), 0, 0, 0, -1, 0, "Fuel Rods");
        }
        var entry = service.getEntry();
        if (entry.isEmpty()) {
            return new WarpCoreStatusS2CPacket("", true,
                    ReactorState.OFFLINE.name(), 0,
                    service.getConfig().getMaxFuelRods(), 0, 0, 0, -1, 0, "Fuel Rods");
        }
        return fromEntry(entry.get(), service.getCoilHealth(entry.get()));
    }
}