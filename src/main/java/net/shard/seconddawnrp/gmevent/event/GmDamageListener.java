package net.shard.seconddawnrp.gmevent.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.shard.seconddawnrp.gmevent.service.GmEventService;

public class GmDamageListener {

    private final GmEventService gmEventService;

    public GmDamageListener(GmEventService gmEventService) {
        this.gmEventService = gmEventService;
    }

    public void register() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE
                .register(this::onDamage);
    }

    private boolean onDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof MobEntity)) return true;
        // Return false to cancel the damage
        if (gmEventService.shouldCancelDamage(entity.getUuid(), source)) {
            return false;
        }
        return true;
    }
}