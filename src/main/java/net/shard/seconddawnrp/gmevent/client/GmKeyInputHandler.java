package net.shard.seconddawnrp.gmevent.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.shard.seconddawnrp.gmevent.item.SpawnItemTool;
import net.shard.seconddawnrp.gmevent.network.DespawnAllC2SPacket;
import net.shard.seconddawnrp.gmevent.network.FireSpawnC2SPacket;

public class GmKeyInputHandler {

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(GmKeyInputHandler::onTick);
    }

    private static void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null) return;

        ClientPlayerEntity player = client.player;

        // ONLY fire if Spawn Item Tool is in main hand
        ItemStack main = player.getMainHandStack();
        if (!(main.getItem() instanceof SpawnItemTool)) return;

        if (GmKeybindings.GM_SPAWN.wasPressed()) {
            String templateId = readSelectedTemplate(main);
            if (templateId == null || templateId.isBlank()) {
                player.sendMessage(
                        net.minecraft.text.Text.literal("[GM] No template selected. Open GUI first."),
                        true);
                return;
            }
            var hitResult = player.raycast(50, 0, false);
            var pos = net.minecraft.util.math.BlockPos.ofFloored(hitResult.getPos());
            String worldKey = client.world.getRegistryKey().getValue().toString();
            ClientPlayNetworking.send(new FireSpawnC2SPacket(
                    templateId, pos.getX(), pos.getY(), pos.getZ(), worldKey));
            player.sendMessage(
                    net.minecraft.text.Text.literal("[GM] Spawning: " + templateId
                            + " at " + pos.toShortString()), true);
        }

        if (GmKeybindings.GM_DESPAWN.wasPressed()) {
            ClientPlayNetworking.send(new DespawnAllC2SPacket());
            player.sendMessage(
                    net.minecraft.text.Text.literal("[GM] Despawning all event mobs..."), true);
        }
    }

    private static ItemStack findSpawnItemTool(ClientPlayerEntity player) {
        // Check main hand first, then hotbar
        ItemStack main = player.getMainHandStack();
        if (main.getItem() instanceof SpawnItemTool) return main;
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.getItem() instanceof SpawnItemTool) return s;
        }
        return null;
    }

    private static String readSelectedTemplate(ItemStack stack) {
        var component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return null;
        var nbt = component.copyNbt();
        return nbt.contains("SelectedTemplate") ? nbt.getString("SelectedTemplate") : null;
    }
}