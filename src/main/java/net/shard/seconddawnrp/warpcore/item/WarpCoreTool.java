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
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.warpcore.network.WarpCoreStatusS2CPacket;

import java.util.List;

/**
 * Warp Core Tool — opens the Warp Core Monitor screen.
 *
 * <p>Right-click in air: sends current warp core status to client,
 * opening the monitor screen.
 *
 * <p>Available to any player for status viewing. Fault injection and
 * configuration commands require appropriate permissions.
 */
public class WarpCoreTool extends Item {

    public WarpCoreTool(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            ServerPlayNetworking.send(player,
                    WarpCoreStatusS2CPacket.fromService(SecondDawnRP.WARP_CORE_SERVICE));
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Warp Core Monitoring Tool").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click: open reactor status")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("/warpcore startup | shutdown | fuel add <n>")
                .formatted(Formatting.DARK_GRAY));
    }
}