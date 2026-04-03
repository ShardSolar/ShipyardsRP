package net.shard.seconddawnrp.gmevent.service;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.gmevent.data.*;
import net.shard.seconddawnrp.gmevent.repository.EncounterTemplateRepository;
import net.shard.seconddawnrp.gmevent.repository.SpawnBlockRepository;
import net.shard.seconddawnrp.tasksystem.service.TaskService;

import java.util.*;

public class GmEventService {

    private final EncounterTemplateRepository templateRepository;
    private final SpawnBlockRepository spawnBlockRepository;
    private final TaskService taskService;
    private final GmEventConfig eventConfig;

    private final List<EncounterTemplate> templates = new ArrayList<>();
    private final List<SpawnBlockEntry> spawnBlocks = new ArrayList<>();
    private final Map<String, ActiveEvent> activeEvents = new HashMap<>();
    private final Map<UUID, String> mobToEvent = new HashMap<>();
    private final Map<String, Integer> spawnTimers = new HashMap<>();

    /**
     * Tool-spawn ownership:
     * mob UUID -> GM/player UUID who spawned it with the Spawn Item Tool.
     *
     * Event-spawned mobs are NOT stored here.
     */
    private final Map<UUID, UUID> toolSpawnOwners = new HashMap<>();

    private static MinecraftServer server;

    public GmEventService(
            EncounterTemplateRepository templateRepository,
            SpawnBlockRepository spawnBlockRepository,
            TaskService taskService,
            GmEventConfig eventConfig
    ) {
        this.templateRepository = Objects.requireNonNull(templateRepository);
        this.spawnBlockRepository = Objects.requireNonNull(spawnBlockRepository);
        this.taskService = Objects.requireNonNull(taskService);
        this.eventConfig = Objects.requireNonNull(eventConfig);
        this.templates.addAll(templateRepository.loadAll());
        this.spawnBlocks.addAll(spawnBlockRepository.loadAll());
    }

    public static void setServer(MinecraftServer s) {
        server = s;
    }

    // ── Template management ───────────────────────────────────────────────────

    public List<EncounterTemplate> getTemplates() {
        return List.copyOf(templates);
    }

    public Optional<EncounterTemplate> findTemplate(String id) {
        return templates.stream().filter(t -> t.getId().equals(id)).findFirst();
    }

    public void saveTemplate(EncounterTemplate template) {
        templates.removeIf(t -> t.getId().equals(template.getId()));
        templates.add(template);
        templateRepository.save(template);
    }

    public void deleteTemplate(String id) {
        templates.removeIf(t -> t.getId().equals(id));
        templateRepository.delete(id);
    }

    // ── Spawn block management ────────────────────────────────────────────────

    public boolean registerSpawnBlock(ServerWorld world, BlockPos pos,
                                      String templateId, String linkedTaskId) {
        String worldKey = world.getRegistryKey().getValue().toString();
        spawnBlocks.removeIf(e -> e.matches(worldKey, pos));
        spawnBlocks.add(new SpawnBlockEntry(worldKey, pos, templateId, linkedTaskId));
        saveSpawnBlocks();
        return true;
    }

    public boolean removeSpawnBlock(ServerWorld world, BlockPos pos) {
        String worldKey = world.getRegistryKey().getValue().toString();
        boolean removed = spawnBlocks.removeIf(e -> e.matches(worldKey, pos));
        if (removed) saveSpawnBlocks();
        return removed;
    }

    public Optional<SpawnBlockEntry> findSpawnBlock(ServerWorld world, BlockPos pos) {
        String worldKey = world.getRegistryKey().getValue().toString();
        return spawnBlocks.stream().filter(e -> e.matches(worldKey, pos)).findFirst();
    }

    public List<SpawnBlockEntry> getAllSpawnBlocks() {
        return List.copyOf(spawnBlocks);
    }

    // ── Event triggering ──────────────────────────────────────────────────────

    public Optional<ActiveEvent> triggerEvent(ServerWorld world, BlockPos origin,
                                              String templateId, String linkedTaskId) {
        EncounterTemplate template = findTemplate(templateId).orElse(null);
        if (template == null) return Optional.empty();

        String eventId = UUID.randomUUID().toString().substring(0, 8);
        ActiveEvent event = new ActiveEvent(eventId, templateId, linkedTaskId);
        activeEvents.put(eventId, event);

        broadcastToAll("[EVENT] Encounter initiated: " + template.getDisplayName());

        if (template.getSpawnBehaviour() == SpawnBehaviour.INSTANT) {
            spawnBatch(world, origin, template, event);
        } else if (template.getSpawnBehaviour() == SpawnBehaviour.TIMED) {
            spawnTimers.put(eventId, 0);
        }

        return Optional.of(event);
    }

    public boolean triggerSpawnBlock(ServerWorld world, BlockPos pos) {
        SpawnBlockEntry entry = findSpawnBlock(world, pos).orElse(null);
        if (entry == null || entry.getTemplateId() == null) return false;
        return triggerEvent(world, pos, entry.getTemplateId(), entry.getLinkedTaskId()).isPresent();
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public void tick(MinecraftServer tickServer) {
        if (tickServer == null) return;

        List<String> toEnd = new ArrayList<>();

        for (Map.Entry<String, ActiveEvent> mapEntry : activeEvents.entrySet()) {
            String eventId = mapEntry.getKey();
            ActiveEvent event = mapEntry.getValue();

            if (event.isEnded()) {
                toEnd.add(eventId);
                continue;
            }

            EncounterTemplate template = findTemplate(event.getTemplateId()).orElse(null);
            if (template == null) continue;

            // ── Timed spawns ──────────────────────────────────────────────────
            if (template.getSpawnBehaviour() == SpawnBehaviour.TIMED) {
                int ticksLeft = spawnTimers.getOrDefault(eventId, 0) - 1;

                if (ticksLeft <= 0
                        && event.getTotalSpawned() < template.getTotalSpawnCount()
                        && event.getActiveCount() < template.getMaxActiveAtOnce()) {
                    ServerWorld world = tickServer.getWorld(net.minecraft.world.World.OVERWORLD);
                    if (world != null) {
                        spawnSingle(world, new BlockPos(0, 64, 0), template, event);
                    }
                    spawnTimers.put(eventId, template.getSpawnIntervalTicks());
                } else {
                    spawnTimers.put(eventId, ticksLeft);
                }
            }

            // ── Per-mob tick — skills + protection ────────────────────────────
            for (ServerWorld world : tickServer.getWorlds()) {
                for (UUID mobUuid : List.copyOf(event.getSpawnedMobUuids())) {
                    var entity = world.getEntity(mobUuid);
                    if (!(entity instanceof MobEntity mob)) continue;

                    if (template.resolvePreventDespawn(eventConfig)) {
                        mob.setPersistent();
                    }

                    GmSkillHandler.tickMobProtection(world, mob, template, eventConfig);
                    GmSkillHandler.tickMob(world, mob, event, template);
                }
            }
        }

        toEnd.forEach(activeEvents::remove);
    }

    // ── Event ending ──────────────────────────────────────────────────────────

    public boolean stopEvent(String eventId) {
        ActiveEvent event = activeEvents.get(eventId);
        if (event == null) return false;

        if (server != null) {
            for (ServerWorld world : server.getWorlds()) {
                for (UUID mobUuid : List.copyOf(event.getSpawnedMobUuids())) {
                    var entity = world.getEntity(mobUuid);
                    if (entity != null) entity.discard();
                }
            }
        }

        event.end();
        activeEvents.remove(eventId);
        spawnTimers.remove(eventId);
        mobToEvent.entrySet().removeIf(e -> e.getValue().equals(eventId));

        broadcastToAll("[EVENT] Encounter ended: " + eventId);
        return true;
    }

    public void stopAllEvents() {
        List.copyOf(activeEvents.keySet()).forEach(this::stopEvent);
    }

    public List<ActiveEvent> getActiveEvents() {
        return List.copyOf(activeEvents.values());
    }

    // ── Tool-spawn ownership / despawn ───────────────────────────────────────

    /**
     * Register a mob as manually spawned by a specific GM using the Spawn Item Tool.
     */
    public void registerToolSpawnedMob(UUID ownerUuid, UUID mobUuid) {
        if (ownerUuid == null || mobUuid == null) return;
        toolSpawnOwners.put(mobUuid, ownerUuid);
    }

    /**
     * Despawn only tool-spawned mobs belonging to a specific player.
     * Intended for the H hotkey.
     */
    public int despawnToolSpawnedMobs(UUID ownerUuid) {
        if (server == null || ownerUuid == null) return 0;

        int removed = 0;

        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(toolSpawnOwners.entrySet())) {
            UUID mobUuid = entry.getKey();
            UUID mobOwner = entry.getValue();

            if (!ownerUuid.equals(mobOwner)) continue;

            for (ServerWorld world : server.getWorlds()) {
                var entity = world.getEntity(mobUuid);
                if (entity != null) {
                    entity.discard();
                    removed++;
                    break;
                }
            }

            toolSpawnOwners.remove(mobUuid);
        }

        return removed;
    }

    /**
     * Despawn every mob spawned by:
     * - the Spawn Item Tool
     * - active events
     * - spawn blocks
     *
     * Intended for a GM command like /gmevent despawnall.
     */
    public int despawnAllSpawnedMobs() {
        if (server == null) return 0;

        int removed = 0;

        // 1) Tool-spawned mobs
        for (UUID mobUuid : new ArrayList<>(toolSpawnOwners.keySet())) {
            for (ServerWorld world : server.getWorlds()) {
                var entity = world.getEntity(mobUuid);
                if (entity != null) {
                    entity.discard();
                    removed++;
                    break;
                }
            }
            toolSpawnOwners.remove(mobUuid);
        }

        // 2) Event / spawn-block mobs tracked by ActiveEvent
        for (ActiveEvent event : new ArrayList<>(activeEvents.values())) {
            for (UUID mobUuid : new ArrayList<>(event.getSpawnedMobUuids())) {
                for (ServerWorld world : server.getWorlds()) {
                    var entity = world.getEntity(mobUuid);
                    if (entity != null) {
                        entity.discard();
                        removed++;
                        break;
                    }
                }
            }

            event.getSpawnedMobUuids().clear();
            event.end();
        }

        activeEvents.clear();
        spawnTimers.clear();
        mobToEvent.clear();

        return removed;
    }

    // ── Kill tracking ─────────────────────────────────────────────────────────

    public void onMobDeath(UUID mobUuid) {
        // Clean up tool-spawn ownership if this was a manual spawn
        toolSpawnOwners.remove(mobUuid);

        String eventId = mobToEvent.remove(mobUuid);
        if (eventId == null) return;

        ActiveEvent event = activeEvents.get(eventId);
        if (event == null) return;

        event.recordKill(mobUuid);

        EncounterTemplate template = findTemplate(event.getTemplateId()).orElse(null);
        if (template == null) return;

        boolean allKilled = event.getTotalKilled() >= template.getTotalSpawnCount()
                && event.getActiveCount() == 0;

        if (allKilled) {
            broadcastToAll("[EVENT] All hostiles neutralised. Encounter complete.");
            handleEventComplete(event, template);
            event.end();
            activeEvents.remove(eventId);
            spawnTimers.remove(eventId);
        }
    }

    private void handleEventComplete(ActiveEvent event, EncounterTemplate template) {
        if (event.getLinkedTaskId() == null || event.getLinkedTaskId().isBlank()) return;
        if (server == null) return;

        for (var player : server.getPlayerManager().getPlayerList()) {
            var profile = net.shard.seconddawnrp.SecondDawnRP.PROFILE_MANAGER
                    .getLoadedProfile(player.getUuid());
            if (profile == null) continue;
            if (!taskService.hasActiveTask(profile, event.getLinkedTaskId())) continue;
            taskService.incrementProgress(profile, event.getLinkedTaskId(), 1);
        }
    }

    // ── Damage cancellation ───────────────────────────────────────────────────

    public boolean shouldCancelDamage(UUID mobUuid, DamageSource source) {
        String eventId = mobToEvent.get(mobUuid);
        if (eventId == null) return false;

        ActiveEvent event = activeEvents.get(eventId);
        if (event == null) return false;

        EncounterTemplate template = findTemplate(event.getTemplateId()).orElse(null);
        if (template == null) return false;
        if (server == null) return false;

        for (var world : server.getWorlds()) {
            var entity = world.getEntity(mobUuid);
            if (entity instanceof MobEntity mob) {
                return GmSkillHandler.shouldCancelDamage(source, mob, template, eventConfig);
            }
        }

        return false;
    }

    public GmEventConfig getEventConfig() {
        return eventConfig;
    }

    // ── Spawn helpers ─────────────────────────────────────────────────────────

    private void spawnBatch(ServerWorld world, BlockPos origin,
                            EncounterTemplate template, ActiveEvent event) {
        int toSpawn = Math.min(
                template.getTotalSpawnCount() - event.getTotalSpawned(),
                template.getMaxActiveAtOnce() - event.getActiveCount()
        );

        for (int i = 0; i < toSpawn; i++) {
            spawnSingle(world, randomPosNear(origin, template.getSpawnRadiusBlocks()), template, event);
        }
    }

    private void spawnSingle(ServerWorld world, BlockPos pos,
                             EncounterTemplate template, ActiveEvent event) {
        Identifier mobId = Identifier.tryParse(template.getMobTypeId());
        if (mobId == null) return;

        EntityType<?> entityType = Registries.ENTITY_TYPE.get(mobId);
        if (entityType == null) return;

        var entity = entityType.create(world);
        if (!(entity instanceof MobEntity mob)) return;

        mob.refreshPositionAndAngles(
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0
        );

        if (template.getMaxHealth() > 0) {
            var healthAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (healthAttr != null) healthAttr.setBaseValue(template.getMaxHealth());
            mob.setHealth((float) template.getMaxHealth());
        }

        if (template.getArmor() > 0) {
            var armorAttr = mob.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
            if (armorAttr != null) armorAttr.setBaseValue(template.getArmor());
        }

        GmSkillHandler.applyVanillaEffects(mob, template.getStatusEffects());

        if (template.resolvePreventDespawn(eventConfig)) {
            mob.setPersistent();
        }

        world.spawnEntity(mob);

        event.addSpawnedMob(mob.getUuid());
        mobToEvent.put(mob.getUuid(), event.getEventId());
    }

    private BlockPos randomPosNear(BlockPos origin, int radius) {
        Random rand = new Random();
        int dx = rand.nextInt(radius * 2 + 1) - radius;
        int dz = rand.nextInt(radius * 2 + 1) - radius;
        return origin.add(dx, 0, dz);
    }

    private void saveSpawnBlocks() {
        spawnBlockRepository.saveAll(spawnBlocks);
    }

    private void broadcastToAll(String message) {
        if (server == null) return;
        server.getPlayerManager().broadcast(Text.literal(message), false);
    }

    public String getMobEventId(UUID mobUuid) {
        return mobToEvent.get(mobUuid);
    }

    public ActiveEvent findActiveEvent(String eventId) {
        return activeEvents.get(eventId);
    }
}