package net.shard.seconddawnrp.gmevent.service;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.shard.seconddawnrp.gmevent.data.ActiveEvent;
import net.shard.seconddawnrp.gmevent.data.EncounterTemplate;
import net.shard.seconddawnrp.gmevent.data.GmEventConfig;
import net.shard.seconddawnrp.gmevent.data.GmSkill;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GmSkillHandler {

    // ── Tick — called every server tick per active event mob ──────────────────

    public static void tickMob(ServerWorld world, MobEntity mob,
                               ActiveEvent event, EncounterTemplate template) {
        Set<String> skills = parseSkills(template.getStatusEffects());


        long tick = world.getTime();

        if (skills.contains(GmSkill.WEAKNESS_AOE.toStorageKey())) {
            tickWeaknessAoe(world, mob, tick);
        }
        if (skills.contains(GmSkill.FIRE_AURA.toStorageKey())) {
            tickFireAura(world, mob, tick);
        }
        if (skills.contains(GmSkill.REGENERATION.toStorageKey())) {
            tickRegeneration(mob, tick);
        }
        if (skills.contains(GmSkill.ENRAGE.toStorageKey())) {
            tickEnrage(mob);
        }
        if (skills.contains(GmSkill.SHIELD_ALLIES.toStorageKey())) {
            tickShieldAllies(world, mob, event, tick);
        }
    }

    // ── Protection — called every tick per active event mob ───────────────────

    public static void tickMobProtection(ServerWorld world, MobEntity mob,
                                         EncounterTemplate template,
                                         GmEventConfig config) {
        if (template.resolvePreventSunlight(config)) {
            if (mob.isOnFire() && world.isDay()
                    && world.isSkyVisible(mob.getBlockPos())) {
                mob.extinguish();
            }
        }
    }

    // ── Hit — called when a GM mob damages a living entity ────────────────────

    public static void onMobHit(ServerWorld world, MobEntity mob,
                                LivingEntity target, EncounterTemplate template) {
        Set<String> skills = parseSkills(template.getStatusEffects());

        if (skills.contains(GmSkill.KNOCKBACK_STRIKE.toStorageKey())) {
            applyKnockbackStrike(mob, target);
        }
        if (skills.contains(GmSkill.TELEPORT_BEHIND.toStorageKey())) {
            applyTeleportBehind(world, mob, target);
        }
    }

    // ── Death — called when a GM mob dies ─────────────────────────────────────

    public static void onMobDeath(ServerWorld world, MobEntity mob,
                                  ActiveEvent event, EncounterTemplate template) {
        Set<String> skills = parseSkills(template.getStatusEffects());

        if (skills.contains(GmSkill.SUMMON_ADDS.toStorageKey())) {
            applySummonAdds(world, mob, event);
        }
    }

    // ── Damage cancellation ───────────────────────────────────────────────────

    public static boolean shouldCancelDamage(DamageSource source, MobEntity mob,
                                             EncounterTemplate template,
                                             GmEventConfig config) {
        String sourceName = source.getType().msgId();

        if (template.resolvePreventSunlight(config)) {
            if (sourceName.equals("onFire") || sourceName.equals("inFire")) {
                if (mob.isOnFire() && mob.getWorld().isDay()
                        && mob.getWorld().isSkyVisible(mob.getBlockPos())) {
                    return true;
                }
            }
        }

        if (template.resolvePreventSuffocation(config)) {
            if (sourceName.equals("inWall")) return true;
        }

        if (template.resolvePreventDrowning(config)) {
            if (sourceName.equals("drown")) return true;
        }

        if (template.resolvePreventFallDamage(config)) {
            if (sourceName.equals("fall")) return true;
        }

        return false;
    }

    // ── Vanilla effect application on spawn ───────────────────────────────────

    public static void applyVanillaEffects(MobEntity mob, List<String> statusEffects) {
        if (statusEffects == null) return;
        for (String effect : statusEffects) {
            if (effect.startsWith("skill:")) continue;
            parseAndApplyVanillaEffect(mob, effect);
        }
    }

    // ── Skill implementations ─────────────────────────────────────────────────

    private static void tickWeaknessAoe(ServerWorld world, MobEntity mob, long tick) {
        if (tick % 40 != 0) return;
        Box box = mob.getBoundingBox().expand(6);
        world.getEntitiesByClass(
                        net.minecraft.entity.player.PlayerEntity.class, box, p -> true)
                .forEach(p -> p.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0)));
    }

    private static void tickFireAura(ServerWorld world, MobEntity mob, long tick) {
        if (tick % 20 != 0) return;
        Box box = mob.getBoundingBox().expand(4);
        world.getEntitiesByClass(
                        net.minecraft.entity.player.PlayerEntity.class, box, p -> true)
                .forEach(p -> p.setOnFireFor(3));
    }

    private static void tickRegeneration(MobEntity mob, long tick) {
        if (tick % 40 != 0) return;
        float max = mob.getMaxHealth();
        if (mob.getHealth() < max) {
            mob.setHealth(Math.min(mob.getHealth() + 1.0f, max));
        }
    }

    private static void tickEnrage(MobEntity mob) {
        boolean enraged = mob.getHealth() < mob.getMaxHealth() * 0.5f;
        if (!enraged) return;
        mob.addStatusEffect(
                new StatusEffectInstance(StatusEffects.SPEED,    40, 1, true, false));
        mob.addStatusEffect(
                new StatusEffectInstance(StatusEffects.STRENGTH, 40, 1, true, false));
    }

    private static void tickShieldAllies(ServerWorld world, MobEntity mob,
                                         ActiveEvent event, long tick) {
        if (tick % 20 != 0) return;
        Box box = mob.getBoundingBox().expand(8);
        world.getEntitiesByClass(MobEntity.class, box,
                        ally -> event.getSpawnedMobUuids().contains(ally.getUuid())
                                && !ally.getUuid().equals(mob.getUuid()))
                .forEach(ally -> ally.addStatusEffect(
                        new StatusEffectInstance(
                                StatusEffects.RESISTANCE, 30, 0, true, false)));
    }

    private static void applyKnockbackStrike(MobEntity mob, LivingEntity target) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0) {
            target.addVelocity(dx / len * 1.5, 0.4, dz / len * 1.5);
            target.velocityModified = true;
        }
    }

    private static void applyTeleportBehind(ServerWorld world, MobEntity mob,
                                            LivingEntity target) {
        float yaw = target.getYaw();
        double rad = Math.toRadians(yaw);
        double bx = target.getX() + Math.sin(rad) * 2;
        double bz = target.getZ() - Math.cos(rad) * 2;
        mob.teleport(bx, target.getY(), bz, true);
    }

    private static void applySummonAdds(ServerWorld world, MobEntity mob,
                                        ActiveEvent event) {
        for (int i = 0; i < 2; i++) {
            var silverfish = net.minecraft.entity.EntityType.SILVERFISH.create(world);
            if (silverfish == null) continue;
            silverfish.refreshPositionAndAngles(
                    mob.getX() + (world.random.nextFloat() - 0.5f) * 2,
                    mob.getY(),
                    mob.getZ() + (world.random.nextFloat() - 0.5f) * 2,
                    world.random.nextFloat() * 360, 0);
            world.spawnEntity(silverfish);
            event.addSpawnedMob(silverfish.getUuid());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void parseAndApplyVanillaEffect(MobEntity mob, String effectKey) {
        String[] parts = effectKey.split(":");
        if (parts.length < 2) return;

        String nsId = parts[0] + ":" + parts[1];

        int parsedAmplitude = 0;
        if (parts.length >= 3) {
            try { parsedAmplitude = Integer.parseInt(parts[2]) - 1; }
            catch (Exception ignored) {}
        }
        final int amplitude = parsedAmplitude;

        var id = net.minecraft.util.Identifier.tryParse(nsId);
        if (id == null) return;

        Registries.STATUS_EFFECT.getEntry(id).ifPresent(entry ->
                mob.addStatusEffect(new StatusEffectInstance(
                        entry, Integer.MAX_VALUE, amplitude, true, false))
        );
    }

    private static Set<String> parseSkills(List<String> effects) {
        Set<String> set = new HashSet<>();
        if (effects == null) return set;
        for (String e : effects) {
            if (e.startsWith("skill:")) set.add(e);
        }
        return set;
    }
}