package net.shard.seconddawnrp.degradation.service;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;
import net.shard.seconddawnrp.degradation.data.DegradationConfig;
import net.shard.seconddawnrp.degradation.network.ComponentWarningS2CPacket;
import net.shard.seconddawnrp.degradation.repository.ComponentRepository;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;
import net.shard.seconddawnrp.tasksystem.service.TaskService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DegradationService {

    private static final int MAX_HEALTH = 100;
    private static final int MAX_ACTIVE_AUTO_REPAIR_TASKS = 6;
    private static final int OFFLINE_RECOVERY_HEALTH = 30;

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
            DegradationConfig config
    ) {
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
            normalizeEntry(entry);
            cache.put(entry.getComponentId(), entry);
        }
        System.out.println("[SecondDawnRP] Degradation system loaded " + cache.size() + " components.");
    }

    public void saveAll() {
        for (ComponentEntry entry : cache.values()) {
            normalizeEntry(entry);
        }
        repository.saveAll(cache.values());
    }

    // ── Server Tick ───────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (ComponentEntry entry : new ArrayList<>(cache.values())) {
            refreshMissingState(entry, false, now);
            tickDrain(entry, now);
            tickWarningPulse(entry);
        }

        integrityChecker.tick(server);
    }

    private void tickDrain(ComponentEntry entry, long now) {
        if (entry.isMissingBlock()) {
            if (entry.getHealth() != 0 || entry.getStatus() != ComponentStatus.OFFLINE) {
                entry.setHealth(0);
                entry.setMissingBlock(true);
                normalizeEntry(entry);
                repository.save(entry);
            }
            return;
        }

        if (entry.getStatus() == ComponentStatus.OFFLINE || entry.getHealth() <= 0) {
            if (entry.getHealth() != 0 || entry.getStatus() != ComponentStatus.OFFLINE) {
                ComponentStatus previousStatus = entry.getStatus();
                entry.setHealth(0);
                normalizeEntry(entry);

                if (previousStatus != ComponentStatus.OFFLINE) {
                    ensureRepairTaskForComponent(entry, now, false);
                    onComponentOffline(entry, false);
                    notifyBlockStateChanged(entry);
                }

                repository.save(entry);
            }
            return;
        }

        long elapsed = now - entry.getLastDrainTickMs();
        if (elapsed < config.getDrainIntervalMs()) {
            return;
        }

        int baseDrain = switch (entry.getStatus()) {
            case NOMINAL -> config.getDrainPerTickNominal();
            case DEGRADED -> config.getDrainPerTickDegraded();
            case CRITICAL -> config.getDrainPerTickCritical();
            case OFFLINE -> 0;
        };

        int drain = reactorCritical
                ? (int) Math.ceil(baseDrain * reactorDrainMultiplier)
                : baseDrain;

        ComponentStatus previousStatus = entry.getStatus();

        entry.setHealth(entry.getHealth() - drain);
        entry.setLastDrainTickMs(now);
        normalizeEntry(entry);

        if (previousStatus != ComponentStatus.CRITICAL
                && entry.getStatus() == ComponentStatus.CRITICAL) {
            ensureRepairTaskForComponent(entry, now, false);
        }

        if (previousStatus != ComponentStatus.OFFLINE
                && entry.getStatus() == ComponentStatus.OFFLINE) {
            ensureRepairTaskForComponent(entry, now, false);
            onComponentOffline(entry, false);
        }

        if (previousStatus != entry.getStatus()) {
            notifyBlockStateChanged(entry);
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
            case DEGRADED -> config.getWarningPulseTicksDegraded();
            case CRITICAL, OFFLINE -> config.getWarningPulseTicksCritical();
            case NOMINAL -> Integer.MAX_VALUE;
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

        double radius = config.getWarningRadiusBlocks();
        world.getPlayers().stream()
                .filter(p -> p.getBlockPos().isWithinDistance(pos, radius))
                .forEach(p -> net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, packet));
    }

    // ── Missing Block Handling ────────────────────────────────────────────────

    /**
     * Call this when a registered block is physically broken.
     * The component remains registered, but is marked missing and forced OFFLINE.
     */
    public Optional<ComponentEntry> markBlockMissing(String worldKey, long blockPosLong) {
        return markBlockMissing(worldKey, blockPosLong, System.currentTimeMillis());
    }

    public Optional<ComponentEntry> markBlockMissing(String worldKey, long blockPosLong, long now) {
        Optional<ComponentEntry> opt = getByPosition(worldKey, blockPosLong);
        if (opt.isEmpty()) return Optional.empty();

        ComponentEntry entry = opt.get();
        boolean alreadyMissing = entry.isMissingBlock();

        entry.setMissingBlock(true);
        entry.setHealth(0);
        normalizeEntry(entry);

        if (!alreadyMissing) {
            ensureRepairTaskForComponent(entry, now, true);
            onComponentOffline(entry, true);
            notifyBlockStateChanged(entry);
            repository.save(entry);
        }

        return Optional.of(entry);
    }

    /**
     * Checks the actual world block and updates missing state.
     * If restoreHealthOnReturn is true, replacing the block can bring the component back online.
     */
    public void refreshMissingState(ComponentEntry entry, boolean restoreHealthOnReturn, long now) {
        ServerWorld world = resolveWorld(entry.getWorldKey());
        if (world == null) return;

        BlockPos pos = BlockPos.fromLong(entry.getBlockPosLong());
        BlockState state = world.getBlockState(pos);

        boolean blockMissingNow = state.isAir();

        if (blockMissingNow && !entry.isMissingBlock()) {
            entry.setMissingBlock(true);
            entry.setHealth(0);
            normalizeEntry(entry);
            ensureRepairTaskForComponent(entry, now, true);
            onComponentOffline(entry, true);
            notifyBlockStateChanged(entry);
            repository.save(entry);
            return;
        }

        if (!blockMissingNow && entry.isMissingBlock()) {
            entry.setMissingBlock(false);

            if (restoreHealthOnReturn) {
                entry.setHealth(Math.max(OFFLINE_RECOVERY_HEALTH, config.getHealthPerRepair()));
            } else {
                entry.setHealth(Math.max(entry.getHealth(), 1));
            }

            normalizeEntry(entry);
            notifyBlockStateChanged(entry);
            repository.save(entry);
        }
    }

    // ── Task Auto-Generation ──────────────────────────────────────────────────

    private void ensureRepairTaskForComponent(ComponentEntry entry, long now, boolean missingBlock) {
        long cooldown = config.getTaskGenerationCooldownMs();
        if (now - entry.getLastTaskGeneratedMs() < cooldown) {
            return;
        }

        if (taskService.hasActiveTaskForTarget(entry.getComponentId())) {
            return;
        }

        if (taskService.countActiveTasksMatching("[autoRepair:true]") >= MAX_ACTIVE_AUTO_REPAIR_TASKS) {
            System.out.println("[SecondDawnRP] Auto-repair task cap reached; skipping task for component '"
                    + entry.getComponentId() + "'.");
            return;
        }

        String displayName = missingBlock
                ? "Replace Missing Component: " + entry.getDisplayName()
                : "Repair: " + entry.getDisplayName();

        String description = "Component " + entry.getDisplayName()
                + (missingBlock
                ? " is MISSING from its registered position."
                : " is in " + entry.getStatus().name() + " status (health: " + entry.getHealth() + "/100).")
                + " Locate and restore the component at position "
                + positionLabel(entry.getBlockPosLong())
                + " in world '" + entry.getWorldKey() + "'."
                + " [componentId:" + entry.getComponentId() + "]"
                + " [autoRepair:true]"
                + (missingBlock ? " [missingBlock:true]" : "");

        taskService.createPoolTask(
                generateTaskId(entry),
                displayName,
                description,
                Division.ENGINEERING,
                TaskObjectiveType.MANUAL_CONFIRM,
                entry.getComponentId(),
                1,
                missingBlock ? 25 : (entry.getStatus() == ComponentStatus.OFFLINE ? 20 : 15),
                true,
                null
        );

        entry.setLastTaskGeneratedMs(now);
        repository.save(entry);

        System.out.println("[SecondDawnRP] Auto-generated repair task for component '" + entry.getComponentId() + "'.");
    }

    private String generateTaskId(ComponentEntry entry) {
        String base = ("repair_" + entry.getComponentId()).toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return base;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public ComponentEntry register(
            String worldKey,
            long blockPosLong,
            String blockTypeId,
            String displayName,
            UUID registeredBy
    ) {
        boolean alreadyExists = cache.values().stream()
                .anyMatch(e -> e.getWorldKey().equals(worldKey)
                        && e.getBlockPosLong() == blockPosLong);

        if (alreadyExists) {
            throw new IllegalStateException("A component is already registered at this position.");
        }

        String id = generateComponentId(displayName, blockPosLong);
        long now = System.currentTimeMillis();

        ComponentEntry entry = new ComponentEntry(
                id,
                worldKey,
                blockPosLong,
                blockTypeId,
                displayName,
                100,
                ComponentStatus.NOMINAL,
                now,
                0L,
                registeredBy,
                null,
                0,
                false
        );

        normalizeEntry(entry);

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
        pulseCounters.remove(existing.get().getComponentId());
        return true;
    }

    // ── Repair ────────────────────────────────────────────────────────────────

    public Optional<ComponentEntry> applyRepair(String worldKey, long blockPosLong) {
        Optional<ComponentEntry> opt = getByPosition(worldKey, blockPosLong);
        if (opt.isEmpty()) return Optional.empty();

        ComponentEntry entry = opt.get();
        ComponentStatus previousStatus = entry.getStatus();

        if (entry.isMissingBlock()) {
            // Can't repair a physically missing block with a normal repair action.
            return Optional.of(entry);
        }

        if (previousStatus == ComponentStatus.OFFLINE || entry.getHealth() <= 0) {
            entry.setHealth(Math.max(OFFLINE_RECOVERY_HEALTH, config.getHealthPerRepair()));
        } else {
            entry.setHealth(entry.getHealth() + config.getHealthPerRepair());
        }

        normalizeEntry(entry);

        if (previousStatus != entry.getStatus()) {
            notifyBlockStateChanged(entry);
        }

        repository.save(entry);
        return Optional.of(entry);
    }

    public Optional<ComponentEntry> applyDamage(String worldKey, long blockPosLong, int amount) {
        Optional<ComponentEntry> opt = getByPosition(worldKey, blockPosLong);
        if (opt.isEmpty()) return Optional.empty();

        ComponentEntry entry = opt.get();

        if (entry.isMissingBlock()) {
            return Optional.of(entry);
        }

        ComponentStatus previousStatus = entry.getStatus();
        entry.setHealth(entry.getHealth() - amount);

        long now = System.currentTimeMillis();
        normalizeEntry(entry);

        if (previousStatus != ComponentStatus.CRITICAL
                && entry.getStatus() == ComponentStatus.CRITICAL) {
            ensureRepairTaskForComponent(entry, now, false);
        }

        if (previousStatus != ComponentStatus.OFFLINE
                && entry.getStatus() == ComponentStatus.OFFLINE) {
            ensureRepairTaskForComponent(entry, now, false);
            onComponentOffline(entry, false);
        }

        if (previousStatus != entry.getStatus()) {
            notifyBlockStateChanged(entry);
        }

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

    public Optional<ComponentEntry> getById(String componentId) {
        return Optional.ofNullable(cache.get(componentId));
    }

    public void forceSave(ComponentEntry entry) {
        normalizeEntry(entry);
        cache.put(entry.getComponentId(), entry);
        repository.save(entry);
    }

    public DegradationConfig getConfig() {
        return config;
    }

    public void setReactorCritical(boolean critical, double multiplier) {
        this.reactorCritical = critical;
        this.reactorDrainMultiplier = multiplier;
        if (critical) {
            System.out.println("[SecondDawnRP] Reactor critical — degradation multiplier: " + multiplier + "x");
        }
    }

    public boolean isReactorCritical() {
        return reactorCritical;
    }

    public boolean isFunctionLocked(ComponentStatus status) {
        return status == ComponentStatus.CRITICAL || status == ComponentStatus.OFFLINE;
    }

    public boolean isBlockDisabled(String worldKey, long blockPosLong) {
        return cache.values().stream()
                .anyMatch(e -> e.getWorldKey().equals(worldKey)
                        && e.getBlockPosLong() == blockPosLong
                        && (e.isMissingBlock() || isFunctionLocked(e.getStatus())));
    }

    private void onComponentOffline(ComponentEntry entry, boolean missingBlock) {
        System.out.println("[SecondDawnRP] Component OFFLINE: " + entry.getDisplayName()
                + (missingBlock ? " (missing block)" : ""));

        if (server == null) return;

        String msgText = missingBlock
                ? "[Engineering] MISSING COMPONENT: " + entry.getDisplayName()
                + " has been removed from its registered position. Immediate replacement required."
                : "[Engineering] OFFLINE: " + entry.getDisplayName()
                + " is non-functional. Immediate repair required.";

        Text msg = Text.literal(msgText).formatted(Formatting.DARK_RED);

        server.getPlayerManager().getPlayerList()
                .forEach(p -> p.sendMessage(msg, false));
    }

    private void notifyBlockStateChanged(ComponentEntry entry) {
        ServerWorld world = resolveWorld(entry.getWorldKey());
        if (world == null) return;

        BlockPos pos = BlockPos.fromLong(entry.getBlockPosLong());
        var state = world.getBlockState(pos);

        world.updateNeighborsAlways(pos, state.getBlock());
        world.updateComparators(pos, state.getBlock());
        world.markDirty(pos);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void normalizeEntry(ComponentEntry entry) {
        if (entry == null) return;
        entry.normalizeState();
    }

    private ServerWorld resolveWorld(String worldKey) {
        if (server == null) return null;

        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey().getValue().toString().equals(worldKey)) {
                return world;
            }
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
}