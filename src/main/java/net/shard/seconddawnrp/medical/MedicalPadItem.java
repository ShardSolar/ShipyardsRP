package net.shard.seconddawnrp.medical;

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
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.List;

/**
 * Medical PADD — treatment and patient records tool.
 *
 * <p>Right-click opens the Medical PADD screen.
 * Access restricted to Medical division officers (LT+).
 * Any player can hold the item; the authority check fires on open.
 *
 * <p>Admin-issued item — no crafting recipe.
 */
public class MedicalPadItem extends Item {

    public MedicalPadItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) return TypedActionResult.success(stack);
        if (!(user instanceof ServerPlayerEntity player)) return TypedActionResult.pass(stack);

        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());

        if (!SecondDawnRP.MEDICAL_SERVICE.isMedicalOfficer(profile)) {
            player.sendMessage(Text.literal(
                            "[Medical PADD] Access restricted to Medical division officers.")
                    .formatted(Formatting.RED), false);
            return TypedActionResult.fail(stack);
        }

        net.shard.seconddawnrp.medical.network.MedicalPadNetworking.openMedicalPad(player);

        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Medical PADD")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click: open patient roster")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Medical division officers only")
                .formatted(Formatting.DARK_GRAY));
    }
}