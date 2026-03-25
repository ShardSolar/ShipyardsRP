package net.shard.seconddawnrp.degradation.event;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;
import net.shard.seconddawnrp.registry.ModItems;

import java.util.Optional;

/**
 * Handles player interactions with registered components.
 *
 * <ul>
 *   <li>Right-click with Engineering PAD — inspect status
 *   <li>Sneak + right-click with correct repair item — consume item and repair
 *   <li>Sneak + right-click with wrong item — tell player what is needed
 * </ul>
 */
public class ComponentInteractListener {

    public void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            String worldKey = world.getRegistryKey().getValue().toString();
            long posLong = pos.asLong();

            Optional<ComponentEntry> opt =
                    SecondDawnRP.DEGRADATION_SERVICE.getByPosition(worldKey, posLong);
            if (opt.isEmpty()) return ActionResult.PASS;

            ComponentEntry entry = opt.get();
            ItemStack held = serverPlayer.getMainHandStack();

            // OFFLINE — block all normal interactions except repair attempt and PAD inspect
            if (entry.getStatus() == ComponentStatus.OFFLINE) {
                if (serverPlayer.isSneaking()) {
                    // Allow repair attempt even when OFFLINE
                    return handleRepair(serverPlayer, worldKey, posLong, entry, held);
                } else if (held.isOf(ModItems.ENGINEERING_PAD)) {
                    handleInspect(serverPlayer, entry);
                    return ActionResult.SUCCESS;
                } else {
                    // Block the interaction — component is non-functional
                    serverPlayer.sendMessage(
                            Text.literal("⚠ " + entry.getDisplayName()
                                            + " is OFFLINE — component non-functional.")
                                    .formatted(Formatting.DARK_RED), true);
                    return ActionResult.FAIL;
                }
            }

            if (serverPlayer.isSneaking()) {
                // Sneak + right-click — attempt repair with held item
                return handleRepair(serverPlayer, worldKey, posLong, entry, held);
            } else if (held.isOf(ModItems.ENGINEERING_PAD)) {
                // If this block is also a registered warp core controller,
                // defer to WarpCoreControllerBlock.onUse() for focused pad view
                if (net.shard.seconddawnrp.SecondDawnRP.WARP_CORE_SERVICE
                        .getByPosition(worldKey, posLong).isPresent()) {
                    return ActionResult.PASS;
                }
                // Plain right-click with PAD — inspect component
                handleInspect(serverPlayer, entry);
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });
    }

    // ── Inspect ───────────────────────────────────────────────────────────────

    private void handleInspect(ServerPlayerEntity player, ComponentEntry entry) {
        String repairItem = resolveRepairItemId(entry);
        int repairCount   = resolveRepairItemCount(entry);

        player.sendMessage(
                Text.literal("── Component Status ──").formatted(Formatting.GOLD), false);
        player.sendMessage(
                Text.literal("Name: ").formatted(Formatting.GRAY)
                        .append(Text.literal(entry.getDisplayName()).formatted(Formatting.WHITE)),
                false);
        player.sendMessage(
                Text.literal("Health: ").formatted(Formatting.GRAY)
                        .append(Text.literal(entry.getHealth() + "/100")
                                .formatted(healthColor(entry.getStatus()))), false);
        player.sendMessage(
                Text.literal("Status: ").formatted(Formatting.GRAY)
                        .append(Text.literal(entry.getStatus().name())
                                .formatted(healthColor(entry.getStatus()))), false);
        player.sendMessage(
                Text.literal("Repair item: ").formatted(Formatting.GRAY)
                        .append(Text.literal(repairCount + "x " + repairItem)
                                .formatted(Formatting.AQUA)), false);
        player.sendMessage(
                Text.literal("ID: ").formatted(Formatting.GRAY)
                        .append(Text.literal(entry.getComponentId())
                                .formatted(Formatting.DARK_GRAY)), false);
    }

    // ── Repair ────────────────────────────────────────────────────────────────

    private ActionResult handleRepair(ServerPlayerEntity player, String worldKey,
                                      long posLong, ComponentEntry entry, ItemStack held) {
        if (entry.getStatus() == ComponentStatus.NOMINAL) {
            player.sendMessage(
                    Text.literal("This component does not require repair.")
                            .formatted(Formatting.GREEN), false);
            return ActionResult.SUCCESS;
        }

        String requiredItemId = resolveRepairItemId(entry);
        int requiredCount     = resolveRepairItemCount(entry);

        // Check held item matches
        String heldItemId = Registries.ITEM.getId(held.getItem()).toString();
        if (!heldItemId.equals(requiredItemId)) {
            player.sendMessage(
                    Text.literal("Wrong item. This component requires ")
                            .formatted(Formatting.RED)
                            .append(Text.literal(requiredCount + "x " + requiredItemId)
                                    .formatted(Formatting.YELLOW))
                            .append(Text.literal(" to repair.").formatted(Formatting.RED)),
                    false);
            return ActionResult.SUCCESS;
        }

        // Check player has enough
        int available = countItem(player, requiredItemId);
        if (available < requiredCount) {
            player.sendMessage(
                    Text.literal("Not enough items. Need ")
                            .formatted(Formatting.RED)
                            .append(Text.literal(requiredCount + "x " + requiredItemId)
                                    .formatted(Formatting.YELLOW))
                            .append(Text.literal(", have " + available + ".")
                                    .formatted(Formatting.RED)),
                    false);
            return ActionResult.SUCCESS;
        }

        // Consume items and apply repair
        consumeItems(player, requiredItemId, requiredCount);
        Optional<ComponentEntry> updated =
                SecondDawnRP.DEGRADATION_SERVICE.applyRepair(worldKey, posLong);

        updated.ifPresent(e -> {
            player.sendMessage(
                    Text.literal("Repair applied (-" + requiredCount + "x "
                                    + requiredItemId + "). Health: ")
                            .formatted(Formatting.GRAY)
                            .append(Text.literal(e.getHealth() + "/100")
                                    .formatted(healthColor(e.getStatus()))), false);
            if (e.getStatus() == ComponentStatus.NOMINAL) {
                player.sendMessage(
                        Text.literal("Component is now fully operational.")
                                .formatted(Formatting.GREEN), false);
            }
        });

        return ActionResult.SUCCESS;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveRepairItemId(ComponentEntry entry) {
        return entry.getRepairItemId() != null
                ? entry.getRepairItemId()
                : SecondDawnRP.DEGRADATION_SERVICE.getConfig().getDefaultRepairItemId();
    }

    private int resolveRepairItemCount(ComponentEntry entry) {
        return entry.getRepairItemCount() > 0
                ? entry.getRepairItemCount()
                : SecondDawnRP.DEGRADATION_SERVICE.getConfig().getDefaultRepairItemCount();
    }

    private static int countItem(ServerPlayerEntity player, String itemId) {
        var item = Registries.ITEM.get(Identifier.of(itemId));
        return player.getInventory().count(item);
    }

    private static void consumeItems(ServerPlayerEntity player, String itemId, int count) {
        var item = Registries.ITEM.get(Identifier.of(itemId));
        int remaining = count;
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.decrement(toRemove);
                remaining -= toRemove;
            }
        }
    }

    private static Formatting healthColor(ComponentStatus status) {
        return switch (status) {
            case NOMINAL  -> Formatting.GREEN;
            case DEGRADED -> Formatting.YELLOW;
            case CRITICAL -> Formatting.RED;
            case OFFLINE  -> Formatting.DARK_RED;
        };
    }
}