package net.shard.seconddawnrp.gmevent.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class SpawnItemScreenHandlerFactory
        implements ExtendedScreenHandlerFactory<SpawnItemScreenOpenData> {

    private final SpawnItemScreenOpenData data;

    public SpawnItemScreenHandlerFactory(SpawnItemScreenOpenData data) {
        this.data = data;
    }

    @Override public Text getDisplayName() { return Text.literal("Spawn Tool"); }

    @Override
    public SpawnItemScreenOpenData getScreenOpeningData(ServerPlayerEntity player) { return data; }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new SpawnItemScreenHandler(syncId, inv, data);
    }
}