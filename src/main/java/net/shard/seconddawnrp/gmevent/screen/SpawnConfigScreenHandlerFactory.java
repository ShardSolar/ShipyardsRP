package net.shard.seconddawnrp.gmevent.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class SpawnConfigScreenHandlerFactory
        implements ExtendedScreenHandlerFactory<SpawnConfigScreenOpenData> {

    private final SpawnConfigScreenOpenData data;

    public SpawnConfigScreenHandlerFactory(SpawnConfigScreenOpenData data) {
        this.data = data;
    }

    @Override public Text getDisplayName() { return Text.literal("Spawn Configurator"); }

    @Override
    public SpawnConfigScreenOpenData getScreenOpeningData(ServerPlayerEntity player) { return data; }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new SpawnConfigScreenHandler(syncId, inv, data);
    }
}