package net.shard.seconddawnrp.tactical.damage;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Admin tool for mapping model blocks to damage zone IDs.
 *
 * Right-click air:   cycle mode (MODEL / REAL)
 * Right-click block: register block to current zone context
 *
 * Set context with: /admin hardpoint zone set <shipId> <zoneId> <mode>
 */
public class DamageZoneToolItem extends Item {

    public DamageZoneToolItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null || player.getWorld().isClient()) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        if (!sp.hasPermissionLevel(4)) {
            sp.sendMessage(Text.literal("[DamageZone] Admin access required.").formatted(Formatting.RED), false);
            return ActionResult.FAIL;
        }

        ItemStack stack = context.getStack();
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            sp.sendMessage(Text.literal("[DamageZone] Set context first with a command.").formatted(Formatting.YELLOW), false);
            return ActionResult.FAIL;
        }

        NbtCompound compound = customData.copyNbt();
        String shipId = compound.getString("shipId");
        String zoneId = compound.getString("zoneId");
        String mode   = compound.getString("mode");

        if (shipId.isEmpty() || zoneId.isEmpty()) {
            sp.sendMessage(Text.literal("[DamageZone] No shipId/zoneId set.").formatted(Formatting.YELLOW), false);
            return ActionResult.FAIL;
        }

        BlockPos pos = context.getBlockPos();
        if ("MODEL".equals(mode)) {
            DamageModelMapper.registerModelBlock(shipId, zoneId, pos);
            sp.sendMessage(Text.literal("[DamageZone] Model block " + formatPos(pos) + " → " + zoneId)
                    .formatted(Formatting.GREEN), false);
        } else {
            DamageModelMapper.registerRealBlock(shipId, zoneId, pos);
            sp.sendMessage(Text.literal("[DamageZone] Real block " + formatPos(pos) + " → " + zoneId)
                    .formatted(Formatting.AQUA), false);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return TypedActionResult.pass(user.getStackInHand(hand));
        if (!(user instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(user.getStackInHand(hand));

        ItemStack stack = user.getStackInHand(hand);
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);

        if (customData == null) {
            sp.sendMessage(Text.literal("[DamageZone] No context set.").formatted(Formatting.YELLOW), false);
            return TypedActionResult.success(stack);
        }

        // Cycle mode MODEL ↔ REAL
        NbtCompound compound = customData.copyNbt();
        String newMode = "MODEL".equals(compound.getString("mode")) ? "REAL" : "MODEL";
        compound.putString("mode", newMode);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(compound));
        sp.sendMessage(Text.literal("[DamageZone] Mode: " + newMode).formatted(Formatting.AQUA), true);
        return TypedActionResult.success(stack);
    }

    public static void setContext(ServerPlayerEntity player, String shipId,
                                  String zoneId, String mode) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof DamageZoneToolItem)) {
            player.sendMessage(Text.literal("[DamageZone] Hold the Damage Zone Tool.").formatted(Formatting.RED), false);
            return;
        }
        NbtCompound compound = new NbtCompound();
        compound.putString("shipId", shipId);
        compound.putString("zoneId", zoneId);
        compound.putString("mode", mode.toUpperCase());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(compound));
        player.sendMessage(Text.literal("[DamageZone] Context: " + shipId + " / " + zoneId + " / " + mode.toUpperCase())
                .formatted(Formatting.GREEN), false);
    }

    private String formatPos(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }
}