package net.shard.seconddawnrp.degradation.event;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;

import java.util.Optional;

/**
 * Listens for block attacks (left-click) on registered components.
 *
 * Behavior:
 * - If component health is above 0, left-click damages the component and cancels normal breaking.
 * - If component health is already 0, this listener allows the click through so the
 *   block-break listener can remove the block and mark it missing.
 */
public class ComponentDamageListener {

    /** Health drained per direct hit. */
    private static final int DAMAGE_PER_HIT = 10;

    public void register() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient()) return ActionResult.PASS;

            String worldKey = world.getRegistryKey().getValue().toString();
            long posLong = pos.asLong();

            Optional<ComponentEntry> opt =
                    SecondDawnRP.DEGRADATION_SERVICE.getByPosition(worldKey, posLong);
            if (opt.isEmpty()) return ActionResult.PASS;

            ComponentEntry entry = opt.get();

            // Missing block shouldn't be attack-processed here
            if (entry.isMissingBlock()) {
                return ActionResult.PASS;
            }

            // IMPORTANT:
            // If already at 0 health, allow normal block-breaking flow to continue.
            // This lets ComponentBlockBreakListener handle actual destruction.
            if (entry.getHealth() <= 0) {
                return ActionResult.PASS;
            }

            var updated = SecondDawnRP.DEGRADATION_SERVICE.applyDamage(worldKey, posLong, DAMAGE_PER_HIT);

            if (player instanceof ServerPlayerEntity sp) {
                updated.ifPresent(e -> {
                    sp.sendMessage(
                            Text.literal("Hit: ").formatted(Formatting.GRAY)
                                    .append(Text.literal(e.getDisplayName()).formatted(Formatting.WHITE))
                                    .append(Text.literal(" — " + e.getHealth() + "/100")
                                            .formatted(healthColor(e.getStatus()))),
                            true
                    );

                    if (e.getHealth() <= 0) {
                        sp.sendMessage(
                                Text.literal(e.getDisplayName() + " is now non-functional. Break it to remove the dead component.")
                                        .formatted(Formatting.DARK_RED),
                                false
                        );
                    }
                });
            }

            // Cancel normal breaking only while component still has health to lose.
            return ActionResult.FAIL;
        });
    }

    private static Formatting healthColor(ComponentStatus status) {
        return switch (status) {
            case NOMINAL  -> Formatting.GREEN;
            case DEGRADED -> Formatting.YELLOW;
            case CRITICAL -> Formatting.RED;
            case OFFLINE  -> Formatting.DARK_RED;
        };
    }
}