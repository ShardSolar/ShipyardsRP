package net.shard.seconddawnrp.terminal;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;

/**
 * Registers a UseBlockCallback that intercepts right-clicks on designated terminal blocks.
 */
public class TerminalDesignatorInteractListener {

    public void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;

            if (player.isSneaking()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            boolean consumed = SecondDawnRP.TERMINAL_DESIGNATOR_SERVICE.handleInteract(sp, sw, pos);

            return consumed ? ActionResult.SUCCESS : ActionResult.PASS;
        });
    }
}