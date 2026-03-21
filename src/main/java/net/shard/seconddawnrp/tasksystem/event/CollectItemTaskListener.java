package net.shard.seconddawnrp.tasksystem.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;
import net.shard.seconddawnrp.tasksystem.data.ActiveTask;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;
import net.shard.seconddawnrp.tasksystem.data.TaskTemplate;
import net.shard.seconddawnrp.tasksystem.service.TaskService;
import net.shard.seconddawnrp.tasksystem.util.TaskTargetMatcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CollectItemTaskListener {

    private final PlayerProfileManager profileManager;
    private final TaskService taskService;

    // Stores item counts from the previous tick per player
    // Key: playerUuid, Value: map of (item registry id → count)
    private final Map<UUID, Map<String, Integer>> previousInventory = new HashMap<>();

    public CollectItemTaskListener(PlayerProfileManager profileManager, TaskService taskService) {
        this.profileManager = profileManager;
        this.taskService = taskService;
    }

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerProfile profile = profileManager.getLoadedProfile(player.getUuid());
            if (profile == null) continue;

            List<ActiveTask> activeTasks = profile.getActiveTasks();
            if (activeTasks.isEmpty()) {
                previousInventory.remove(player.getUuid());
                continue;
            }

            // Only process if player has at least one COLLECT_ITEM task
            boolean hasCollectTask = activeTasks.stream().anyMatch(task -> {
                TaskTemplate t = taskService.resolveTaskTemplate(task.getTemplateId());
                return t != null && t.getObjectiveType() == TaskObjectiveType.COLLECT_ITEM;
            });

            if (!hasCollectTask) {
                previousInventory.remove(player.getUuid());
                continue;
            }

            Map<String, Integer> currentCounts = snapshotInventory(player);
            Map<String, Integer> previousCounts = previousInventory.getOrDefault(
                    player.getUuid(), new HashMap<>());

            // Find items that increased in count since last tick
            for (Map.Entry<String, Integer> entry : currentCounts.entrySet()) {
                String itemId = entry.getKey();
                int current = entry.getValue();
                int previous = previousCounts.getOrDefault(itemId, 0);

                if (current <= previous) continue;

                int gained = current - previous;

                // Check each active collect task against this item
                for (ActiveTask activeTask : List.copyOf(activeTasks)) {
                    TaskTemplate template = taskService.resolveTaskTemplate(activeTask.getTemplateId());
                    if (template == null) continue;
                    if (template.getObjectiveType() != TaskObjectiveType.COLLECT_ITEM) continue;
                    if (activeTask.isComplete()) continue;

                    // Build a dummy stack to use itemMatches — just need the item id comparison
                    if (itemId.equals(normalizeId(template.getTargetId()))) {
                        taskService.incrementProgress(profile, template.getId(), gained);
                    }
                }
            }

            previousInventory.put(player.getUuid(), currentCounts);
        }
    }

    /**
     * Builds a map of item registry id -> total count across all inventory slots.
     */
    private Map<String, Integer> snapshotInventory(ServerPlayerEntity player) {
        Map<String, Integer> counts = new HashMap<>();

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            String id = net.minecraft.registry.Registries.ITEM
                    .getId(stack.getItem()).toString();

            counts.merge(id, stack.getCount(), Integer::sum);
        }

        return counts;
    }

    private String normalizeId(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.trim().toLowerCase();
        if (!value.contains(":")) value = "minecraft:" + value;
        return value;
    }
}