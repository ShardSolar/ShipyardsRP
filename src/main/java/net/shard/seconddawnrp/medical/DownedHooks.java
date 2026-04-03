package net.shard.seconddawnrp.medical;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

/**
 * Hooks lethal damage to implement the downed state.
 *
 * Behavior:
 * - First lethal hit: player is put into downed state instead of dying.
 * - Lethal hit while already downed: vanilla death is still cancelled, and the
 *   player is resolved through a controlled medical failure flow instead.
 */
public class DownedHooks {

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;

            PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
            if (profile == null) return true;

            // Already downed — do NOT allow vanilla death.
            // Resolve through controlled medical failure instead.
            if (profile.isDowned()) {
                player.setHealth(1.0f);
                if (SecondDawnRP.DOWNED_SERVICE != null) {
                    SecondDawnRP.DOWNED_SERVICE.resolveDownedDeath(player);
                }
                return false;
            }

            // First lethal hit — cancel death and enter downed state
            player.setHealth(1.0f);
            SecondDawnRP.DOWNED_SERVICE.downPlayer(player, false);
            return false;
        });
    }
}