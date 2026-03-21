package net.shard.seconddawnrp.gmevent.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.EncounterTemplate;
import net.shard.seconddawnrp.gmevent.service.GmSkillHandler;

public class GmMobHitListener {

    public void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(this::onDamage);
    }

    private void onDamage(LivingEntity entity, DamageSource source, float baseDamage,
                          float damage, boolean blocked) {
        // We want to fire skills when a GM mob hits a player
        // source.getAttacker() is the mob, entity is the player
        if (!(source.getAttacker() instanceof MobEntity attacker)) return;
        if (!(entity.getWorld() instanceof ServerWorld world)) return;

        String eventId = SecondDawnRP.GM_EVENT_SERVICE
                .getMobEventId(attacker.getUuid());
        if (eventId == null) return;

        var event = SecondDawnRP.GM_EVENT_SERVICE.findActiveEvent(eventId);
        if (event == null) return;

        EncounterTemplate template = SecondDawnRP.GM_EVENT_SERVICE
                .findTemplate(event.getTemplateId()).orElse(null);
        if (template == null) return;

        GmSkillHandler.onMobHit(world, attacker, entity, template);
    }
}