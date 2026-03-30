package net.shard.seconddawnrp.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MilkBucketItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MilkBucketItem.class)
public class MilkBucketItemMixin {

    @Inject(method = "finishUsing", at = @At("TAIL"))
    private void seconddawnrp$handleMedicalMilkSuppression(
            ItemStack stack,
            World world,
            LivingEntity user,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (world.isClient()) return;
        if (!(user instanceof ServerPlayerEntity player)) return;

        SecondDawnRP.LONG_TERM_INJURY_SERVICE.handleMilkUse(player, stack);
    }
}