package net.shard.seconddawnrp.gmevent.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.EnvironmentalEffectEntry;
import net.shard.seconddawnrp.gmevent.data.TriggerEntry;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.List;

public final class GmEventNetworking {

    private GmEventNetworking() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(OpenEnvConfigS2CPacket.ID, OpenEnvConfigS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(EnvRegistryS2CPacket.ID, EnvRegistryS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenTriggerConfigS2CPacket.ID, OpenTriggerConfigS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(ToolVisibilityS2CPacket.ID, ToolVisibilityS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(GmToolRefreshS2CPacket.ID, GmToolRefreshS2CPacket.CODEC);

        PayloadTypeRegistry.playC2S().register(SaveTriggerConfigC2SPacket.ID, SaveTriggerConfigC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveEnvConfigC2SPacket.ID, SaveEnvConfigC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveTemplateC2SPacket.ID, SaveTemplateC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ActivateSpawnBlockC2SPacket.ID, ActivateSpawnBlockC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(PushToPoolC2SPacket.ID, PushToPoolC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(FireSpawnC2SPacket.ID, FireSpawnC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DespawnAllC2SPacket.ID, DespawnAllC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DespawnToolSpawnedC2SPacket.ID, DespawnToolSpawnedC2SPacket.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(
                SaveTriggerConfigC2SPacket.ID,
                (payload, context) -> context.server().execute(() -> {
                    var opt = SecondDawnRP.TRIGGER_SERVICE.getById(payload.entryId());
                    if (opt.isEmpty()) return;

                    TriggerEntry entry = opt.get();
                    entry.setTriggerMode(payload.triggerMode());
                    entry.setFireMode(payload.fireMode());
                    entry.setRadiusBlocks(payload.radiusBlocks());
                    entry.setCooldownTicks(payload.cooldownTicks());
                    entry.setArmed(payload.armed());
                    entry.setActions(payload.actions());
                    SecondDawnRP.TRIGGER_SERVICE.saveEntry(entry);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(
                SaveEnvConfigC2SPacket.ID,
                (payload, context) -> context.server().execute(() -> {
                    var opt = SecondDawnRP.ENV_EFFECT_SERVICE.getById(payload.entryId());
                    if (opt.isEmpty()) return;

                    EnvironmentalEffectEntry entry = opt.get();
                    entry.setVanillaEffects(payload.vanillaEffects());
                    entry.setMedicalConditionId(payload.medicalConditionId());
                    entry.setMedicalConditionSeverity(payload.medicalConditionSeverity());
                    entry.setRadiusBlocks(payload.radiusBlocks());
                    entry.setLingerMode(payload.lingerMode());
                    entry.setLingerDurationTicks(payload.lingerDurationTicks());
                    entry.setFireMode(payload.fireMode());
                    entry.setOnEntryCooldownTicks(payload.onEntryCooldownTicks());
                    entry.setVisibility(payload.visibility());
                    entry.setActive(payload.active());
                    SecondDawnRP.ENV_EFFECT_SERVICE.saveEntry(entry);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(
                SaveTemplateC2SPacket.ID,
                (payload, context) -> context.server().execute(() -> handleSaveTemplate(context.player(), payload))
        );

        ServerPlayNetworking.registerGlobalReceiver(
                ActivateSpawnBlockC2SPacket.ID,
                (payload, context) -> context.server().execute(() -> handleActivateSpawnBlock(context.player(), payload))
        );

        ServerPlayNetworking.registerGlobalReceiver(
                PushToPoolC2SPacket.ID,
                (payload, context) -> context.server().execute(() -> handlePushToPool(context.player(), payload))
        );

        ServerPlayNetworking.registerGlobalReceiver(
                FireSpawnC2SPacket.ID,
                (payload, context) -> context.server().execute(() -> {
                    var player = context.player();
                    var server = context.server();

                    PlayerProfile profile = getActorProfile(player);
                    if (profile == null || !SecondDawnRP.GM_PERMISSION_SERVICE.canTriggerEvents(player, profile)) {
                        player.sendMessage(Text.literal("[GM] No permission."), false);
                        return;
                    }

                    Identifier worldId = Identifier.tryParse(payload.worldKey());
                    if (worldId == null) {
                        player.sendMessage(Text.literal("[GM] Invalid world key."), false);
                        return;
                    }

                    var world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, worldId));
                    if (world == null) {
                        player.sendMessage(Text.literal("[GM] Could not resolve world."), false);
                        return;
                    }

                    var templateOpt = SecondDawnRP.GM_EVENT_SERVICE.findTemplate(payload.templateId());
                    if (templateOpt.isEmpty()) {
                        player.sendMessage(Text.literal("[GM] Invalid template: " + payload.templateId()), false);
                        return;
                    }

                    var template = templateOpt.get();

                    Identifier mobId = Identifier.tryParse(template.getMobTypeId());
                    if (mobId == null) {
                        player.sendMessage(Text.literal("[GM] Invalid mob ID in template."), false);
                        return;
                    }

                    var entityType = Registries.ENTITY_TYPE.get(mobId);
                    if (entityType == null) {
                        player.sendMessage(Text.literal("[GM] Unknown mob type: " + template.getMobTypeId()), false);
                        return;
                    }

                    var entity = entityType.create(world);
                    if (!(entity instanceof MobEntity mob)) {
                        player.sendMessage(Text.literal("[GM] Template does not spawn a mob entity."), false);
                        return;
                    }

                    BlockPos pos = new BlockPos(payload.x(), payload.y(), payload.z());

                    mob.refreshPositionAndAngles(
                            pos.getX() + 0.5,
                            pos.getY(),
                            pos.getZ() + 0.5,
                            0.0f,
                            0.0f
                    );

                    if (template.getMaxHealth() > 0) {
                        var healthAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                        if (healthAttr != null) {
                            healthAttr.setBaseValue(template.getMaxHealth());
                        }
                        mob.setHealth((float) template.getMaxHealth());
                    }

                    if (template.getArmor() > 0) {
                        var armorAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
                        if (armorAttr != null) {
                            armorAttr.setBaseValue(template.getArmor());
                        }
                    }

                    net.shard.seconddawnrp.gmevent.service.GmSkillHandler.applyVanillaEffects(
                            mob,
                            template.getStatusEffects()
                    );

                    if (template.resolvePreventDespawn(SecondDawnRP.GM_EVENT_SERVICE.getEventConfig())) {
                        mob.setPersistent();
                    }

                    world.spawnEntity(mob);

                    SecondDawnRP.GM_EVENT_SERVICE.registerToolSpawnedMob(
                            player.getUuid(),
                            mob.getUuid()
                    );

                    player.sendMessage(
                            Text.literal("[GM] Spawned: " + template.getDisplayName()
                                    + " at " + pos.toShortString()),
                            false
                    );
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(
                DespawnToolSpawnedC2SPacket.ID,
                (payload, context) -> context.server().execute(() -> {
                    var player = context.player();

                    PlayerProfile profile = getActorProfile(player);
                    if (profile == null || !SecondDawnRP.GM_PERMISSION_SERVICE.canStopEvents(player, profile)) {
                        player.sendMessage(Text.literal("[GM] No permission."), false);
                        return;
                    }

                    int removed = SecondDawnRP.GM_EVENT_SERVICE.despawnToolSpawnedMobs(player.getUuid());
                    player.sendMessage(
                            Text.literal("[GM] Despawned " + removed + " of your tool-spawned mob(s)."),
                            false
                    );
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(
                DespawnAllC2SPacket.ID,
                (payload, context) -> context.server().execute(() -> {
                    var player = context.player();

                    PlayerProfile profile = getActorProfile(player);
                    if (profile == null || !SecondDawnRP.GM_PERMISSION_SERVICE.canStopEvents(player, profile)) {
                        player.sendMessage(Text.literal("[GM] No permission."), false);
                        return;
                    }

                    int removed = SecondDawnRP.GM_EVENT_SERVICE.despawnAllSpawnedMobs();
                    player.sendMessage(
                            Text.literal("[GM] Despawned " + removed + " total spawned mob(s)."),
                            false
                    );
                })
        );
    }

    private static PlayerProfile getActorProfile(ServerPlayerEntity player) {
        return SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
    }

    private static void handleSaveTemplate(ServerPlayerEntity player, SaveTemplateC2SPacket packet) {
        PlayerProfile profile = getActorProfile(player);
        if (profile == null || !SecondDawnRP.GM_PERMISSION_SERVICE.canManageTemplates(player, profile)) {
            player.sendMessage(Text.literal("[GM] No permission."), false);
            return;
        }

        var template = new net.shard.seconddawnrp.gmevent.data.EncounterTemplate(
                packet.id(), packet.displayName(), packet.mobTypeId(),
                packet.maxHealth(), packet.armor(), packet.totalSpawnCount(),
                packet.maxActiveAtOnce(), packet.spawnRadiusBlocks(), packet.spawnIntervalTicks(),
                packet.getBehaviour(), packet.statusEffects(), List.of(), List.of()
        );

        SecondDawnRP.GM_EVENT_SERVICE.saveTemplate(template);
        player.sendMessage(Text.literal("[GM] Template saved: " + packet.displayName()), false);
        sendGmToolRefresh(player);
    }

    private static void handleActivateSpawnBlock(ServerPlayerEntity player, ActivateSpawnBlockC2SPacket packet) {
        PlayerProfile profile = getActorProfile(player);
        if (profile == null || !SecondDawnRP.GM_PERMISSION_SERVICE.canTriggerEvents(player, profile)) {
            player.sendMessage(Text.literal("[GM] No permission."), false);
            return;
        }

        var world = player.getServerWorld();
        var pos = new BlockPos(packet.x(), packet.y(), packet.z());

        String templateId = packet.templateId();
        if (templateId != null && !templateId.isBlank()) {
            SecondDawnRP.GM_EVENT_SERVICE.registerSpawnBlock(
                    world, pos, templateId, packet.linkedTaskId()
            );
        }

        var entry = SecondDawnRP.GM_EVENT_SERVICE.findSpawnBlock(world, pos).orElse(null);
        if (entry == null) {
            player.sendMessage(Text.literal("[GM] No spawn block registered at that position."), false);
            return;
        }

        if (packet.linkedTaskId() != null && !packet.linkedTaskId().isBlank()) {
            entry.setLinkedTaskId(packet.linkedTaskId());
        }

        boolean ok = SecondDawnRP.GM_EVENT_SERVICE.triggerSpawnBlock(world, pos);
        player.sendMessage(Text.literal(
                ok ? "[GM] Event triggered: " + entry.getTemplateId()
                        + " at " + pos.toShortString()
                        : "[GM] Trigger failed — check template is valid."
        ), false);
    }

    private static void handlePushToPool(ServerPlayerEntity player, PushToPoolC2SPacket packet) {
        PlayerProfile profile = getActorProfile(player);
        if (profile == null || !SecondDawnRP.TASK_PERMISSION_SERVICE.canCreateTasks(player, profile)) {
            player.sendMessage(Text.literal("[GM] No permission."), false);
            return;
        }

        try {
            var division = net.shard.seconddawnrp.division.Division.valueOf(packet.divisionName());
            var entry = SecondDawnRP.TASK_SERVICE.createPoolTask(
                    "gm_" + packet.templateId() + "_" + System.currentTimeMillis() % 10000,
                    packet.taskDisplayName(),
                    packet.taskDescription(),
                    division,
                    net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType.MANUAL_CONFIRM,
                    "GM Event: " + packet.templateId(),
                    1,
                    50,
                    true,
                    player.getUuid()
            );

            player.sendMessage(Text.literal(
                    entry != null ? "[GM] Task pushed to pool: " + entry.getTaskId() : "[GM] Push failed."
            ), false);

            if (entry != null) {
                sendGmToolRefresh(player);
            }
        } catch (Exception e) {
            player.sendMessage(Text.literal("[GM] Push failed: " + e.getMessage()), false);
        }
    }

    private static void sendGmToolRefresh(ServerPlayerEntity player) {
        var entries = SecondDawnRP.GM_EVENT_SERVICE.getTemplates().stream()
                .map(t -> new GmToolRefreshS2CPacket.TemplateEntry(
                        t.getId(), t.getDisplayName(), t.getMobTypeId(),
                        t.getMaxHealth(), t.getArmor(), t.getTotalSpawnCount(),
                        t.getMaxActiveAtOnce(), t.getSpawnRadiusBlocks(),
                        t.getSpawnIntervalTicks(), t.getSpawnBehaviour().name(),
                        t.getStatusEffects()))
                .toList();

        ServerPlayNetworking.send(player, new GmToolRefreshS2CPacket(entries));
    }
}