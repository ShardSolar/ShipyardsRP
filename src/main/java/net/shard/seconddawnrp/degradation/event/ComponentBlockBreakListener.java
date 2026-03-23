package net.shard.seconddawnrp.degradation.event;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;

import java.util.Optional;

/**
 * Handles block breaking for registered components.
 *
 * <p>Survival/adventure mode: block breaking is cancelled — registered
 * components are indestructible until explicitly unregistered via the
 * Component Registration Tool. The player gets a red warning message.
 *
 * <p>Creative mode: block breaks normally and the component is
 * automatically unregistered so no orphaned data remains.
 */
public class ComponentBlockBreakListener {

    public void register() {
        PlayerBlockBreakEvents.BEFORE.register(
                (world, player, pos, state, blockEntity) -> {
                    if (world.isClient()) return true;

                    String worldKey = world.getRegistryKey().getValue().toString();
                    long posLong = pos.asLong();

                    Optional<ComponentEntry> opt =
                            SecondDawnRP.DEGRADATION_SERVICE.getByPosition(worldKey, posLong);
                    if (opt.isEmpty()) return true; // not a component — allow break

                    if (player instanceof ServerPlayerEntity sp && sp.isCreative()) {
                        // Creative — auto-unregister and allow the break
                        String name = opt.get().getDisplayName();
                        SecondDawnRP.DEGRADATION_SERVICE.unregister(worldKey, posLong);
                        sp.sendMessage(
                                Text.literal("Component '").formatted(Formatting.YELLOW)
                                        .append(Text.literal(name).formatted(Formatting.WHITE))
                                        .append(Text.literal("' auto-unregistered.")
                                                .formatted(Formatting.YELLOW)),
                                false);
                        return true; // allow break
                    }

                    // Survival/adventure — block is protected
                    if (player instanceof ServerPlayerEntity sp) {
                        sp.sendMessage(
                                Text.literal("Cannot break a registered component. ")
                                        .formatted(Formatting.RED)
                                        .append(Text.literal("Unregister it first with the Component Registration Tool.")
                                                .formatted(Formatting.DARK_RED)),
                                false);
                    }
                    return false; // cancel break
                });
    }
}