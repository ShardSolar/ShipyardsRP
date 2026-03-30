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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gurney item.
 *
 * Entity right-click (attach) — handled by MedicalInteractListener via UseEntityCallback.
 * Air right-click — releases current patient.
 *
 * Problem: in Fabric 1.21.1, UseEntityCallback and Item.use() fire independently.
 * When right-clicking a player entity, UseEntityCallback fires (attaches), then
 * use() also fires (immediately releases). Fix: track attach timestamp per carrier,
 * suppress use()-triggered release for 500ms after an attach.
 */
public class GurneyItem extends Item {

    /** carrierUuid -> timestamp of last attach. Suppresses use() release briefly. */
    static final Map<UUID, Long> LAST_ATTACH_MS = new ConcurrentHashMap<>();
    private static final long ATTACH_SUPPRESS_MS = 500L;

    public GurneyItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) return TypedActionResult.pass(stack);
        if (hand != Hand.MAIN_HAND) return TypedActionResult.pass(stack);
        if (!(user instanceof ServerPlayerEntity player)) return TypedActionResult.pass(stack);
        if (SecondDawnRP.GURNEY_SERVICE == null) return TypedActionResult.pass(stack);

        if (!SecondDawnRP.GURNEY_SERVICE.isCarrier(player.getUuid())) {
            player.sendMessage(Text.literal(
                            "[Gurney] Right-click a downed patient to load them.")
                    .formatted(Formatting.GRAY), false);
            return TypedActionResult.pass(stack);
        }

        // Suppress release if we just attached (UseEntityCallback fired first)
        Long lastAttach = LAST_ATTACH_MS.get(player.getUuid());
        if (lastAttach != null
                && System.currentTimeMillis() - lastAttach < ATTACH_SUPPRESS_MS) {
            return TypedActionResult.success(stack); // consume but don't release
        }

        SecondDawnRP.GURNEY_SERVICE.detachByCarrier(player);
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Medical Gurney").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click downed player: load").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Right-click air: release patient").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Or: /gurney release").formatted(Formatting.DARK_GRAY));
    }
}