package net.shard.seconddawnrp.warpcore.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.shard.seconddawnrp.registry.ModItems;
import net.shard.seconddawnrp.warpcore.data.WarpCoreEntry;
import net.shard.seconddawnrp.warpcore.network.WarpCoreStatusS2CPacket;

import java.util.List;
import java.util.Optional;

/**
 * Warp Core Tool.
 *
 * <ul>
 *   <li>Right-click on registered block — open monitor GUI for that core
 *   <li>Right-click in air — open monitor for nearest/single registered core
 *   <li>Sneak + right-click unregistered block — register as warp core controller
 *   <li>Sneak + right-click registered block — unregister
 * </ul>
 *
 * <p>Fuel rods: if the player is holding fuel rods in their off-hand and
 * right-clicks a registered warp core, the rods are loaded automatically.
 */
public class WarpCoreTool extends Item {

    public WarpCoreTool(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return TypedActionResult.pass(user.getStackInHand(hand));
        if (!(user instanceof ServerPlayerEntity player))
            return TypedActionResult.pass(user.getStackInHand(hand));

        HitResult hit = player.raycast(5.0, 0, false);
        boolean lookingAtBlock = hit.getType() == HitResult.Type.BLOCK;

        if (lookingAtBlock) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            String worldKey = world.getRegistryKey().getValue().toString();
            long posLong = pos.asLong();
            Optional<WarpCoreEntry> existing = SecondDawnRP.WARP_CORE_SERVICE.getByPosition(worldKey, posLong);

            if (player.isSneaking()) {
                // Sneak + right-click: register or unregister
                if (existing.isPresent()) {
                    if (!hasPermission(player)) {
                        player.sendMessage(Text.literal("Requires st.gm.use.").formatted(Formatting.RED), false);
                        return TypedActionResult.fail(user.getStackInHand(hand));
                    }
                    SecondDawnRP.WARP_CORE_SERVICE.unregister(existing.get().getEntryId());
                    player.sendMessage(Text.literal("Warp core unregistered.").formatted(Formatting.YELLOW), false);
                } else {
                    if (!hasPermission(player)) {
                        player.sendMessage(Text.literal("Requires st.gm.use.").formatted(Formatting.RED), false);
                        return TypedActionResult.fail(user.getStackInHand(hand));
                    }
                    try {
                        WarpCoreEntry entry = SecondDawnRP.WARP_CORE_SERVICE.register(
                                worldKey, posLong, player.getUuid());
                        player.sendMessage(Text.literal("Warp core registered! ID: ")
                                .formatted(Formatting.GREEN)
                                .append(Text.literal(entry.getEntryId()).formatted(Formatting.WHITE))
                                .append(Text.literal(". Connect TR/EP cables to this block for power.")
                                        .formatted(Formatting.GRAY)), false);
                    } catch (IllegalStateException e) {
                        player.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), false);
                    }
                }
                return TypedActionResult.success(user.getStackInHand(hand));
            }

            // Plain right-click on registered block: open monitor
            if (existing.isPresent()) {
                WarpCoreEntry entry = existing.get();
                ServerPlayNetworking.send(player,
                        WarpCoreStatusS2CPacket.fromEntry(entry,
                                SecondDawnRP.WARP_CORE_SERVICE.getCoilHealth(entry)));
                return TypedActionResult.success(user.getStackInHand(hand));
            }
        }

        // Right-click in air or non-registered block: open monitor for single/nearest core
        if (!player.isSneaking()) {
            Optional<WarpCoreEntry> single = SecondDawnRP.WARP_CORE_SERVICE.getEntry();
            if (single.isPresent()) {
                WarpCoreEntry entry = single.get();
                ServerPlayNetworking.send(player,
                        WarpCoreStatusS2CPacket.fromEntry(entry,
                                SecondDawnRP.WARP_CORE_SERVICE.getCoilHealth(entry)));
            } else if (SecondDawnRP.WARP_CORE_SERVICE.isRegistered()) {
                player.sendMessage(Text.literal("Multiple cores registered — aim at a controller block.")
                        .formatted(Formatting.GRAY), false);
            } else {
                player.sendMessage(Text.literal("No warp core registered. Sneak + right-click a controller block to register.")
                        .formatted(Formatting.GRAY), false);
            }
        }

        return TypedActionResult.success(user.getStackInHand(hand));
    }

    private static boolean hasPermission(ServerPlayerEntity p) {
        return p.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(p, "st.gm.use");
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Warp Core Tool").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click controller: open monitor / load fuel rods from off-hand").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Sneak + right-click: register / unregister").formatted(Formatting.DARK_GRAY));
    }
}