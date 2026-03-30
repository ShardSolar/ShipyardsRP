package net.shard.seconddawnrp.medical;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

/**
 * Hooks lethal damage to implement the downed state.
 *
 * Critical fix: we must set health AND mark downed synchronously inside
 * the ALLOW_DEATH callback — scheduling via server.execute() is too late
 * and allows the death screen to show before the task runs.
 */
public class DownedHooks {

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {

            if (!(entity instanceof ServerPlayerEntity player)) return true;

            PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
            if (profile == null) return true;

            // Already downed — let death proceed (void while downed, /kill, etc.)
            if (profile.isDowned()) return true;

            // Void damage always kills
            if (damageSource.isOf(DamageTypes.OUT_OF_WORLD)) return true;

            // Cancel death — apply downed state synchronously right here
            // so the engine never processes the death at all.
            player.setHealth(1.0f);
            SecondDawnRP.DOWNED_SERVICE.downPlayer(player, false);

            return false;
        });
    }
}