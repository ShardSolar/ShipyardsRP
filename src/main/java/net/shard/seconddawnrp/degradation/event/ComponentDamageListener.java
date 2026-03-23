package net.shard.seconddawnrp.degradation.event;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;

import java.util.Optional;

/**
 * Listens for block attacks (left-click) on registered components.
 *
 * <p>This is the foundation for Phase 5 weapons/combat damage. When a player
 * left-clicks a registered component, health is drained by
 * {@code combatDamagePerHit} from the degradation config. The block itself
 * is not broken — the event is cancelled after applying damage.
 *
 * <p>In Phase 5 this listener will be extended to respond to explosion events
 * and projectile impacts, driving component damage from ship combat.
 *
 * <p>Only players with {@code st.gm.use} can trigger combat damage directly
 * via left-click (for GM testing). In Phase 5, damage will come from
 * server-side explosion/projectile events instead and bypass this permission.
 */
public class ComponentDamageListener {

    /** Health drained per direct hit. Tunable — expose in DegradationConfig Phase 5. */
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

            // Apply combat damage — bypasses the time-based drain interval
            var updated = SecondDawnRP.DEGRADATION_SERVICE.applyDamage(worldKey, posLong, DAMAGE_PER_HIT);

            // Notify the player who hit it — read health from updated entry
            if (player instanceof ServerPlayerEntity sp) {
                updated.ifPresent(e -> sp.sendMessage(
                        Text.literal("Hit: ").formatted(Formatting.GRAY)
                                .append(Text.literal(e.getDisplayName())
                                        .formatted(Formatting.WHITE))
                                .append(Text.literal(" — " + e.getHealth()
                                        + "/100").formatted(healthColor(e.getStatus()))),
                        true)); // action bar — not chat spam
            }

            // Cancel to prevent block breaking
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