package net.shard.seconddawnrp.degradation.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * GM tool for registering and removing maintainable components in-world.
 *
 * <p>Interaction model (mirrors TaskTerminalToolItem pattern):
 * <ul>
 *   <li>Right-click a block — if unregistered: enters naming mode, prompts GM
 *       to type a display name in chat. The next chat message from that player
 *       is intercepted by {@link net.shard.seconddawnrp.degradation.event.ComponentNamingChatListener}
 *       and used to complete registration.
 *   <li>Right-click a registered block — shows component status in chat.
 *   <li>Sneak + right-click a registered block — removes the component.
 * </ul>
 *
 * <p>Requires {@code st.engineering.admin} or {@code st.gm.use} permission.
 */
public class ComponentRegistrationTool extends Item {

    /**
     * Players currently in naming mode: UUID → packed BlockPos they clicked.
     * Populated here, consumed by {@link net.shard.seconddawnrp.degradation.event.ComponentNamingChatListener}.
     */
    public static final Map<UUID, PendingRegistration> PENDING =
            new ConcurrentHashMap<>();

    public record PendingRegistration(
            String worldKey,
            long blockPosLong,
            String blockTypeId
    ) {}

    public ComponentRegistrationTool(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return TypedActionResult.pass(user.getStackInHand(hand));
        if (!(user instanceof ServerPlayerEntity player)) return TypedActionResult.pass(user.getStackInHand(hand));

        if (!hasPermission(player)) {
            player.sendMessage(Text.literal("You don't have permission to use this tool.")
                    .formatted(Formatting.RED), false);
            return TypedActionResult.fail(user.getStackInHand(hand));
        }

        // Ray-cast to find the targeted block
        HitResult hit = player.raycast(5.0, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.literal("Aim at a block to register it as a component.")
                    .formatted(Formatting.GRAY), false);
            return TypedActionResult.pass(user.getStackInHand(hand));
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        String worldKey = world.getRegistryKey().getValue().toString();
        long posLong = pos.asLong();
        String blockTypeId = world.getBlockState(pos).getBlock()
                .getTranslationKey();

        Optional<ComponentEntry> existing =
                SecondDawnRP.DEGRADATION_SERVICE.getByPosition(worldKey, posLong);

        if (player.isSneaking() && existing.isPresent()) {
            // Sneak + right-click registered block — remove
            handleRemove(player, worldKey, posLong, existing);
        } else if (player.isSneaking()) {
            // Sneak + right-click unregistered block — begin naming flow
            // Sneaking bypasses block onUse, works on furnaces/chests/modded blocks
            handleBeginNaming(player, worldKey, posLong, blockTypeId, pos);
        } else if (existing.isPresent()) {
            // Plain right-click registered block — inspect
            handleInspect(player, existing.get());
        } else {
            player.sendMessage(Text.literal("Sneak + right-click to register this block as a component.")
                    .formatted(Formatting.DARK_GRAY), false);
        }

        return TypedActionResult.success(user.getStackInHand(hand));
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleBeginNaming(ServerPlayerEntity player, String worldKey,
                                   long posLong, String blockTypeId, BlockPos pos) {
        // Cancel any previous pending registration for this player
        PENDING.put(player.getUuid(), new PendingRegistration(worldKey, posLong, blockTypeId));

        player.sendMessage(
                Text.literal("── Component Registration ──").formatted(Formatting.GOLD), false);
        player.sendMessage(
                Text.literal("Block: ").formatted(Formatting.GRAY)
                        .append(Text.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                                .formatted(Formatting.WHITE)), false);
        player.sendMessage(
                Text.literal("Type a display name in chat to register, or ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal("cancel").formatted(Formatting.RED))
                        .append(Text.literal(" to abort.").formatted(Formatting.GRAY)), false);
    }

    private void handleInspect(ServerPlayerEntity player, ComponentEntry entry) {
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
                Text.literal("ID: ").formatted(Formatting.GRAY)
                        .append(Text.literal(entry.getComponentId())
                                .formatted(Formatting.DARK_GRAY)), false);
        player.sendMessage(
                Text.literal("Sneak + right-click to remove.").formatted(Formatting.DARK_GRAY),
                false);
    }

    private void handleRemove(ServerPlayerEntity player, String worldKey,
                              long posLong, Optional<ComponentEntry> existing) {
        if (existing.isEmpty()) {
            player.sendMessage(
                    Text.literal("No component registered at this block.")
                            .formatted(Formatting.RED), false);
            return;
        }
        String name = existing.get().getDisplayName();
        SecondDawnRP.DEGRADATION_SERVICE.unregister(worldKey, posLong);
        player.sendMessage(
                Text.literal("Component '").formatted(Formatting.YELLOW)
                        .append(Text.literal(name).formatted(Formatting.WHITE))
                        .append(Text.literal("' removed.").formatted(Formatting.YELLOW)), false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean hasPermission(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, "st.gm.use")
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, "st.engineering.admin");
    }

    private static Formatting healthColor(
            net.shard.seconddawnrp.degradation.data.ComponentStatus status) {
        return switch (status) {
            case NOMINAL  -> Formatting.GREEN;
            case DEGRADED -> Formatting.YELLOW;
            case CRITICAL -> Formatting.RED;
            case OFFLINE  -> Formatting.DARK_RED;
        };
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Component Registration Tool").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click registered block: inspect")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Sneak + right-click block: register")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Sneak + right-click registered: remove")
                .formatted(Formatting.DARK_GRAY));
    }
}