package net.shard.seconddawnrp.degradation.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;
import net.shard.seconddawnrp.degradation.data.DegradationConfig;
import net.shard.seconddawnrp.degradation.network.ComponentWarningS2CPacket;
import net.shard.seconddawnrp.degradation.repository.ComponentRepository;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.tasksystem.data.OpsTaskStatus;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;
import net.shard.seconddawnrp.tasksystem.service.TaskService;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DegradationService {

    private final ComponentRepository repository;
    private final TaskService taskService;
    private final DegradationConfig config;

    private final Map<String, ComponentEntry> cache = new HashMap<>();
    private final Map<String, Integer> pulseCounters = new HashMap<>();

    private MinecraftServer server;
    private boolean reactorCritical = false;
    private double reactorDrainMultiplier = 1.0;
    private final ComponentIntegrityChecker integrityChecker = new ComponentIntegrityChecker(this);

    public DegradationService(
            ComponentRepository repository,
            TaskService taskService,
            DegradationConfig config) {
        this.repository = repository;
        this.taskService = taskService;
        this.config = config;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void reload() {
        cache.clear();
        for (ComponentEntry entry : repository.loadAll()) {
            cache.put(entry.getComponentId(), entry);
        }
        System.out.println("[SecondDawnRP] Degradation system loaded "
                + cache.size() + " components.");
    }

    public void saveAll() {
        repository.saveAll(cache.values());
    }

    // ── Server Tick ───────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (ComponentEntry entry : new java.util.ArrayList<>(cache.values())) {
            tickDrain(entry, now);
            tickWarningPulse(entry);
        }
        integrityChecker.tick(server);
    }

    private void tickDrain(ComponentEntry entry, long now) {
        long elapsed = now - entry.getLastDrainTickMs();
        if (elapsed < config.getDrainIntervalMs()) return;

        int baseDrain = switch (entry.getStatus()) {
            case NOMINAL   -> config.getDrainPerTickNominal();
            case DEGRADED  -> config.getDrainPerTickDegraded();
            case CRITICAL, OFFLINE -> config.getDrainPerTickCritical();
        };
        int drain = reactorCritical
                ? (int) Math.ceil(baseDrain * reactorDrainMultiplier)
                : baseDrain;

        ComponentStatus previousStatus = entry.getStatus();
        entry.setHealth(entry.getHealth() - drain);
        entry.setLastDrainTickMs(now);

        if (previousStatus != ComponentStatus.CRITICAL
                && entry.getStatus() == ComponentStatus.CRITICAL) {
            maybeGenerateRepairTask(entry, now);
        }

        repository.save(entry);
    }

    private void tickWarningPulse(ComponentEntry entry) {
        if (server == null) return;
        if (entry.getStatus() == ComponentStatus.NOMINAL) {
            pulseCounters.remove(entry.getComponentId());
            return;
        }

        int pulseTicks = switch (entry.getStatus()) {
            case DEGRADED        -> config.getWarningPulseTicksDegraded();
            case CRITICAL, OFFLINE -> config.getWarningPulseTicksCritical();
            default              -> Integer.MAX_VALUE;
        };

        int counter = pulseCounters.getOrDefault(entry.getComponentId(), 0) + 1;
        if (counter >= pulseTicks) {
            broadcastWarning(entry);
            counter = 0;
        }
        pulseCounters.put(entry.getComponentId(), counter);
    }

    private void broadcastWarning(ComponentEntry entry) {
        if (server == null) return;
        BlockPos pos = BlockPos.fromLong(entry.getBlockPosLong());
        ServerWorld world = resolveWorld(entry.getWorldKey());
        if (world == null) return;

        ComponentWarningS2CPacket packet = new ComponentWarningS2CPacket(
                entry.getComponentId(),
                entry.getWorldKey(),
                entry.getBlockPosLong(),
                entry.getStatus(),
                entry.getHealth()
        );

        double r = config.getWarningRadiusBlocks();
        world.getPlayers().stream()
                .filter(p -> p.getBlockPos().isWithinDistance(pos, r))
                .forEach(p -> net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
                        .send(p, packet));
    }

    // ── Task Auto-Generation ──────────────────────────────────────────────────

    private void maybeGenerateRepairTask(ComponentEntry entry, long now) {
        long cooldown = config.getTaskGenerationCooldownMs();
        if (now - entry.getLastTaskGeneratedMs() < cooldown) return;

        String displayName = "Repair: " + entry.getDisplayName();
        String description = "Component " + entry.getDisplayName()
                + " has reached CRITICAL status (health: " + entry.getHealth() + "/100)."
                + " Locate and repair the component at position "
                + positionLabel(entry.getBlockPosLong())
                + " in world '" + entry.getWorldKey() + "'."
                + " [componentId:" + entry.getComponentId() + "]";

        taskService.createPoolTask(
                generateTaskId(displayName),
                displayName,
                description,
                Division.ENGINEERING,
                TaskObjectiveType.MANUAL_CONFIRM,
                entry.getComponentId(),
                1,
                50,
                true,
                null
        );

        entry.setLastTaskGeneratedMs(now);
        System.out.println("[SecondDawnRP] Auto-generated repair task for component '"
                + entry.getComponentId() + "'.");
    }

    // ── Registration ─────────────────────────────────────────────────────────

    public ComponentEntry register(
            String worldKey,
            long blockPosLong,
            String blockTypeId,
            String displayName,
            UUID registeredBy) {
        boolean alreadyExists = cache.values().stream()
                .anyMatch(e -> e.getWorldKey().equals(worldKey)
                        && e.getBlockPosLong() == blockPosLong);
        if (alreadyExists) {
            throw new IllegalStateException(
                    "A component is already registered at this position.");
        }

        String id = generateComponentId(displayName, blockPosLong);
        long now = System.currentTimeMillis();
        ComponentEntry entry = new ComponentEntry(
                id, worldKey, blockPosLong, blockTypeId, displayName,
                100, ComponentStatus.NOMINAL, now, 0L, registeredBy,
                null, 0);
        cache.put(id, entry);
        repository.save(entry);
        return entry;
    }

    public boolean unregister(String worldKey, long blockPosLong) {
        Optional<ComponentEntry> existing = cache.values().stream()
                .filter(e -> e.getWorldKey().equals(worldKey)
                        && e.getBlockPosLong() == blockPosLong)
                .findFirst();
        if (existing.isEmpty()) return false;
        cache.remove(existing.get().getComponentId());
        repository.delete(existing.get().getComponentId());
        return true;
    }

    // ── Repair ────────────────────────────────────────────────────────────────

    public Optional<ComponentEntry> applyRepair(String worldKey, long blockPosLong) {
        Optional<ComponentEntry> opt = cache.values().stream()
                .filter(e -> e.getWorldKey().equals(worldKey)
                        && e.getBlockPosLong() == blockPosLong)
                .findFirst();
        if (opt.isEmpty()) return Optional.empty();

        ComponentEntry entry = opt.get();
        entry.setHealth(entry.getHealth() + config.getHealthPerRepair());
        repository.save(entry);
        return Optional.of(entry);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Collection<ComponentEntry> getAllComponents() {
        return cache.values();
    }

    public Optional<ComponentEntry> getByPosition(String worldKey, long blockPosLong) {
        return cache.values().stream()
                .filter(e -> e.getWorldKey().equals(worldKey)
                        && e.getBlockPosLong() == blockPosLong)
                .findFirst();
    }


    /**
     * Apply direct combat damage to a component, bypassing the time-based
     * drain interval. Used by ComponentDamageListener and Phase 5 combat events.
     *
     * @param amount health points to drain (positive value)
     * @return the updated entry, or empty if no component at this position
     */
    public Optional<ComponentEntry> applyDamage(String worldKey, long blockPosLong, int amount) {
        Optional<ComponentEntry> opt = cache.values().stream()
                .filter(e -> e.getWorldKey().equals(worldKey)
                        && e.getBlockPosLong() == blockPosLong)
                .findFirst();
        if (opt.isEmpty()) return Optional.empty();

        ComponentEntry entry = opt.get();
        ComponentStatus previousStatus = entry.getStatus();
        entry.setHealth(entry.getHealth() - amount);

        long now = System.currentTimeMillis();
        if (previousStatus != ComponentStatus.CRITICAL
                && entry.getStatus() == ComponentStatus.CRITICAL) {
            maybeGenerateRepairTask(entry, now);
        }

        repository.save(entry);
        return Optional.of(entry);
    }
    public Optional<ComponentEntry> getById(String componentId) {
        return Optional.ofNullable(cache.get(componentId));
    }

    public void forceSave(ComponentEntry entry) {
        cache.put(entry.getComponentId(), entry);
        repository.save(entry);
    }

    public DegradationConfig getConfig() {
        return config;
    }

    /**
     * Called by WarpCoreService when the reactor enters or leaves CRITICAL/FAILED state.
     * While critical, all component drain rates are multiplied by the given multiplier.
     */
    public void setReactorCritical(boolean critical, double multiplier) {
        this.reactorCritical = critical;
        this.reactorDrainMultiplier = multiplier;
        if (critical) {
            System.out.println("[SecondDawnRP] Reactor critical — degradation multiplier: " + multiplier + "x");
        }
    }

    public boolean isReactorCritical() { return reactorCritical; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ServerWorld resolveWorld(String worldKey) {
        if (server == null) return null;
        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(worldKey)) return w;
        }
        return null;
    }

    private static String positionLabel(long blockPosLong) {
        BlockPos pos = BlockPos.fromLong(blockPosLong);
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private String generateComponentId(String displayName, long blockPosLong) {
        String base = displayName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        String candidate = base + "_" + Long.toHexString(blockPosLong & 0xFFFFFFL);
        int suffix = 2;
        while (cache.containsKey(candidate)) {
            candidate = base + "_" + Long.toHexString(blockPosLong & 0xFFFFFFL) + "_" + suffix++;
        }
        return candidate;
    }

    private static String generateTaskId(String displayName) {
        String base = displayName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return base + "_" + Long.toHexString(System.currentTimeMillis() & 0xFFFFFFL);
    }
}