package net.shard.seconddawnrp.transporter.client;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.transporter.TransporterControllerNetworking.*;

import java.util.List;

/**
 * Minimal screen handler for the transporter controller screen.
 * No inventory slots — exists solely to allow TransporterControllerScreen
 * to extend HandledScreen, which suppresses Minecraft's world blur effect.
 */
public class TransporterScreenHandler extends ScreenHandler {

    private final BlockPos controllerPos;
    private final List<ReadyPlayerData> readyPlayers;
    private final List<DestinationData> dimensions;
    private final List<DestinationData> shipLocations;
    private final List<BeamUpData> beamUpRequests;

    // Client-side constructor (called by ExtendedScreenHandlerType)
    public TransporterScreenHandler(int syncId, PlayerInventory inventory) {
        super(net.shard.seconddawnrp.registry.ModScreenHandlers.TRANSPORTER_SCREEN, syncId);
        this.controllerPos  = BlockPos.ORIGIN;
        this.readyPlayers   = List.of();
        this.dimensions     = List.of();
        this.shipLocations  = List.of();
        this.beamUpRequests = List.of();
    }

    // Server-side constructor (called by TransporterControllerNetworking)
    public TransporterScreenHandler(int syncId, PlayerInventory inventory,
                                    BlockPos controllerPos,
                                    List<ReadyPlayerData> readyPlayers,
                                    List<DestinationData> dimensions,
                                    List<DestinationData> shipLocations,
                                    List<BeamUpData> beamUpRequests) {
        super(net.shard.seconddawnrp.registry.ModScreenHandlers.TRANSPORTER_SCREEN, syncId);
        this.controllerPos  = controllerPos;
        this.readyPlayers   = readyPlayers;
        this.dimensions     = dimensions;
        this.shipLocations  = shipLocations;
        this.beamUpRequests = beamUpRequests;
    }

    public BlockPos getControllerPos()        { return controllerPos; }
    public List<ReadyPlayerData> getReady()   { return readyPlayers; }
    public List<DestinationData> getDimensions() { return dimensions; }
    public List<DestinationData> getShipLocations() { return shipLocations; }
    public List<BeamUpData> getBeamUpRequests() { return beamUpRequests; }

    @Override public boolean canUse(PlayerEntity player) { return true; }
    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}