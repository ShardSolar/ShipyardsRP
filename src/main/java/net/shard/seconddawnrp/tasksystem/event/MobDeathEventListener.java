package net.shard.seconddawnrp.gmevent.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.shard.seconddawnrp.gmevent.service.GmEventService;

public class MobDeathEventListener {

    private final GmEventService gmEventService;

    public MobDeathEventListener(GmEventService gmEventService) {
        this.gmEventService = gmEventService;
    }

    public void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(this::onDeath);
    }

    private void onDeath(LivingEntity entity, DamageSource damageSource) {
        gmEventService.onMobDeath(entity.getUuid());
    }
}