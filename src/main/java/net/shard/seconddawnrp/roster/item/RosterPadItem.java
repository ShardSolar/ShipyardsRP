package net.shard.seconddawnrp.roster.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.shard.seconddawnrp.roster.network.RosterNetworking;

/**
 * The Roster PAD item — right-click in air to open the division roster screen.
 * Available to all players; the screen enforces authority levels for actions.
 */
public class RosterPadItem extends Item {

    public RosterPadItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) return TypedActionResult.success(stack);
        if (!(user instanceof ServerPlayerEntity player)) return TypedActionResult.pass(stack);

        RosterNetworking.openRoster(player);
        return TypedActionResult.success(stack);
    }
}