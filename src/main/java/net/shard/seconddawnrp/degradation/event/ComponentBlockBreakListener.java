package net.shard.seconddawnrp.degradation.event;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;

import java.util.Optional;

/**
 * Handles block breaking for registered components.
 *
 * Behavior:
 * - Health > 0  -> protected, cannot break
 * - Health <= 0 -> break is forced, then component is marked missing
 * - Creative    -> unregister and allow/admin-remove
 */
public class ComponentBlockBreakListener {

    public void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient()) return true;

            String worldKey = world.getRegistryKey().getValue().toString();
            long posLong = pos.asLong();

            Optional<ComponentEntry> opt =
                    SecondDawnRP.DEGRADATION_SERVICE.getByPosition(worldKey, posLong);

            if (opt.isEmpty()) {
                return true;
            }

            ComponentEntry entry = opt.get();

            // Creative: remove registration and let admin break normally
            if (player instanceof ServerPlayerEntity sp && sp.isCreative()) {
                SecondDawnRP.DEGRADATION_SERVICE.unregister(worldKey, posLong);
                sp.sendMessage(
                        Text.literal("Component removed: ")
                                .formatted(Formatting.YELLOW)
                                .append(Text.literal(entry.getDisplayName()).formatted(Formatting.WHITE)),
                        false
                );
                return true;
            }

            // Still has integrity left: do not allow break
            if (entry.getHealth() > 0) {
                if (player instanceof ServerPlayerEntity sp) {
                    sp.sendMessage(
                            Text.literal("Component integrity holding (" + entry.getHealth() + "/100). ")
                                    .formatted(Formatting.RED)
                                    .append(Text.literal("Repair or destroy it to 0 before removal.")
                                            .formatted(Formatting.DARK_RED)),
                            true
                    );
                }
                return false;
            }

            // Health is 0: force the break now, then mark missing
            boolean broken = world.breakBlock(pos, false, player);

            if (broken) {
                SecondDawnRP.DEGRADATION_SERVICE.markBlockMissing(worldKey, posLong);

                if (player instanceof ServerPlayerEntity sp) {
                    sp.sendMessage(
                            Text.literal("[Engineering] ")
                                    .formatted(Formatting.RED)
                                    .append(Text.literal("Component destroyed: ").formatted(Formatting.DARK_RED))
                                    .append(Text.literal(entry.getDisplayName()).formatted(Formatting.WHITE)),
                            false
                    );
                }
            } else if (player instanceof ServerPlayerEntity sp) {
                sp.sendMessage(
                        Text.literal("Component reached 0 health, but the block could not be removed.")
                                .formatted(Formatting.RED),
                        false
                );
            }

            // Cancel vanilla handling because we already handled it ourselves
            return false;
        });
    }
}