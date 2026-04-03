package net.shard.seconddawnrp.gmevent.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.gmevent.item.SpawnItemTool;
import net.shard.seconddawnrp.gmevent.network.DespawnToolSpawnedC2SPacket;
import net.shard.seconddawnrp.gmevent.network.FireSpawnC2SPacket;

public class GmKeyInputHandler {

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(GmKeyInputHandler::onTick);
    }

    private static void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        ClientPlayerEntity player = client.player;

        ItemStack main = player.getMainHandStack();
        if (!(main.getItem() instanceof SpawnItemTool)) return;

        if (GmKeybindings.GM_SPAWN.wasPressed()) {
            String templateId = readSelectedTemplate(main);
            if (templateId == null || templateId.isBlank()) {
                player.sendMessage(Text.literal("[GM] No template selected. Open GUI first."), true);
                return;
            }

            var hitResult = player.raycast(50, 0, false);
            var pos = net.minecraft.util.math.BlockPos.ofFloored(hitResult.getPos());
            String worldKey = client.world.getRegistryKey().getValue().toString();

            ClientPlayNetworking.send(new FireSpawnC2SPacket(
                    templateId,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    worldKey
            ));

            player.sendMessage(
                    Text.literal("[GM] Spawning: " + templateId + " at " + pos.toShortString()),
                    true
            );
        }

        if (GmKeybindings.GM_DESPAWN.wasPressed()) {
            ClientPlayNetworking.send(new DespawnToolSpawnedC2SPacket());
            player.sendMessage(
                    Text.literal("[GM] Despawning tool-spawned mobs..."),
                    true
            );
        }
    }

    private static String readSelectedTemplate(ItemStack stack) {
        var component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return null;
        var nbt = component.copyNbt();
        return nbt.contains("SelectedTemplate") ? nbt.getString("SelectedTemplate") : null;
    }
}