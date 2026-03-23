package net.shard.seconddawnrp.degradation.event;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.item.ComponentRegistrationTool;
import net.shard.seconddawnrp.degradation.item.ComponentRegistrationTool.PendingRegistration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-step chat flow for component registration:
 *
 * <p>Step 1 — player types a display name. "cancel" aborts.
 * <p>Step 2 — player types a repair item ID (e.g. minecraft:iron_ingot)
 *             or "default" to use the global default. "cancel" aborts.
 *
 * <p>All messages in the flow are suppressed from public chat.
 */
public class ComponentNamingChatListener {

    /** Players awaiting step 2 (repair item input): UUID → display name they chose. */
    private static final Map<UUID, String> PENDING_REPAIR_ITEM = new ConcurrentHashMap<>();

    public void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            UUID uuid = sender.getUuid();
            String input = message.getContent().getString().trim();

            // ── Step 2: awaiting repair item ──────────────────────────────────
            if (PENDING_REPAIR_ITEM.containsKey(uuid)) {
                String displayName = PENDING_REPAIR_ITEM.remove(uuid);
                PendingRegistration pending = ComponentRegistrationTool.PENDING.remove(uuid);

                if (input.equalsIgnoreCase("cancel") || input.isBlank()) {
                    sender.sendMessage(
                            Text.literal("Component registration cancelled.").formatted(Formatting.YELLOW),
                            false);
                    return false;
                }

                // Resolve item — "default" means no override
                String repairItemId = null;
                int repairItemCount = 0;

                if (!input.equalsIgnoreCase("default")) {
                    // Parse optional count suffix: "minecraft:iron_ingot 3"
                    String[] parts = input.split("\\s+", 2);
                    repairItemId = parts[0].toLowerCase();
                    if (parts.length > 1) {
                        try {
                            repairItemCount = Math.max(1, Integer.parseInt(parts[1]));
                        } catch (NumberFormatException ignored) {
                            repairItemCount = 1;
                        }
                    } else {
                        repairItemCount = 1;
                    }
                }

                try {
                    ComponentEntry entry = SecondDawnRP.DEGRADATION_SERVICE.register(
                            pending.worldKey(),
                            pending.blockPosLong(),
                            pending.blockTypeId(),
                            displayName,
                            sender.getUuid()
                    );

                    // Apply repair item override if not default
                    if (repairItemId != null) {
                        entry.setRepairItemId(repairItemId);
                        entry.setRepairItemCount(repairItemCount);
                        SecondDawnRP.DEGRADATION_SERVICE.forceSave(entry);
                    }

                    String itemDisplay = repairItemId != null
                            ? repairItemCount + "x " + repairItemId
                            : "global default (" + SecondDawnRP.DEGRADATION_SERVICE
                            .getConfig().getDefaultRepairItemCount() + "x "
                            + SecondDawnRP.DEGRADATION_SERVICE
                            .getConfig().getDefaultRepairItemId() + ")";

                    sender.sendMessage(
                            Text.literal("Registered '").formatted(Formatting.GREEN)
                                    .append(Text.literal(entry.getDisplayName())
                                            .formatted(Formatting.WHITE))
                                    .append(Text.literal("' (id: " + entry.getComponentId() + ")")
                                            .formatted(Formatting.GRAY)),
                            false);
                    sender.sendMessage(
                            Text.literal("Repair item: ").formatted(Formatting.GRAY)
                                    .append(Text.literal(itemDisplay).formatted(Formatting.AQUA)),
                            false);

                } catch (IllegalStateException e) {
                    sender.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), false);
                }
                return false;
            }

            // ── Step 1: awaiting display name ─────────────────────────────────
            PendingRegistration pending = ComponentRegistrationTool.PENDING.get(uuid);
            if (pending == null) return true; // not in flow

            ComponentRegistrationTool.PENDING.remove(uuid);

            if (input.equalsIgnoreCase("cancel") || input.isBlank()) {
                sender.sendMessage(
                        Text.literal("Component registration cancelled.").formatted(Formatting.YELLOW),
                        false);
                return false;
            }

            // Store display name and move to step 2
            // Re-add pending so step 2 can read it
            ComponentRegistrationTool.PENDING.put(uuid, pending);
            PENDING_REPAIR_ITEM.put(uuid, input);

            String defaultItem = SecondDawnRP.DEGRADATION_SERVICE.getConfig().getDefaultRepairItemId();
            int defaultCount   = SecondDawnRP.DEGRADATION_SERVICE.getConfig().getDefaultRepairItemCount();

            sender.sendMessage(
                    Text.literal("Name set to '").formatted(Formatting.GRAY)
                            .append(Text.literal(input).formatted(Formatting.WHITE))
                            .append(Text.literal("'.").formatted(Formatting.GRAY)),
                    false);
            sender.sendMessage(
                    Text.literal("Now type a repair item ID (e.g. ").formatted(Formatting.GRAY)
                            .append(Text.literal("minecraft:iron_ingot 2").formatted(Formatting.YELLOW))
                            .append(Text.literal("), or ").formatted(Formatting.GRAY))
                            .append(Text.literal("default").formatted(Formatting.AQUA))
                            .append(Text.literal(" to use the global default (")
                                    .formatted(Formatting.GRAY))
                            .append(Text.literal(defaultCount + "x " + defaultItem)
                                    .formatted(Formatting.AQUA))
                            .append(Text.literal("), or ").formatted(Formatting.GRAY))
                            .append(Text.literal("cancel").formatted(Formatting.RED))
                            .append(Text.literal(".").formatted(Formatting.GRAY)),
                    false);

            return false;
        });
    }
}