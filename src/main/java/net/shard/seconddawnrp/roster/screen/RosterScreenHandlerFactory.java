package net.shard.seconddawnrp.roster.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.roster.data.RosterOpenData;

public class RosterScreenHandlerFactory implements ExtendedScreenHandlerFactory<RosterOpenData> {

    private final RosterOpenData data;

    public RosterScreenHandlerFactory(RosterOpenData data) {
        this.data = data;
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("Roster");
    }

    @Override
    public RosterOpenData getScreenOpeningData(ServerPlayerEntity player) {
        return data;
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new RosterScreenHandler(syncId, playerInventory, data);
    }
}