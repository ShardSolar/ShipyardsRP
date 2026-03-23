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
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.network.OpenEngineeringPadS2CPacket;

import java.util.List;

/*
 * The Engineering division's handheld PADD.
 *
 * <p>Right-click in air: opens the Engineering PADD screen showing all
 * registered components, their health bars, and status at a glance.
 *
 * <p>Right-click on a registered block: handled by
 * {@link net.shard.seconddawnrp.degradation.event.ComponentInteractListener}
 * (inspect / sneak-repair).
 *
 * <p>The item does not consume durability and has no crafting recipe —
 * it is admin-issued, matching the pattern of {@code task_pad} and
 * {@code operations_pad}.
 */
public class EngineeringPadItem extends Item {

    public EngineeringPadItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
            // Send all component data to the client so the screen can render it
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    serverPlayer,
                    OpenEngineeringPadS2CPacket.fromService(SecondDawnRP.DEGRADATION_SERVICE)
            );
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Engineering Systems PADD")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click: component overview")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Right-click block: inspect component")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Sneak + right-click block: apply repair")
                .formatted(Formatting.DARK_GRAY));
    }
}