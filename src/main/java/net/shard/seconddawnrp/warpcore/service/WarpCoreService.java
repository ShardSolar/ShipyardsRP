package net.shard.seconddawnrp.warpcore.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;
import net.shard.seconddawnrp.warpcore.adapter.PowerSourceAdapter;
import net.shard.seconddawnrp.warpcore.adapter.StandalonePowerAdapter;
import net.shard.seconddawnrp.warpcore.adapter.TRenergyPowerAdapter;
import net.shard.seconddawnrp.warpcore.data.FaultType;
import net.shard.seconddawnrp.warpcore.data.ReactorState;
import net.shard.seconddawnrp.warpcore.data.WarpCoreConfig;
import net.shard.seconddawnrp.warpcore.data.WarpCoreEntry;
import net.shard.seconddawnrp.warpcore.repository.WarpCoreRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Core service for the warp core system. Supports multiple registered cores.
 *
 * <p>Each core is identified by its {@code entryId}. Commands targeting a
 * specific core take the entryId as an argument. When only one core is
 * registered, commands that omit the ID default to that core.
 */
public class WarpCoreService {

    private static final int STARTUP_RETRY_TICKS = 20;

    private final WarpCoreRepository repository;
    private final WarpCoreConfig config;
    private MinecraftServer server;

    /** All registered warp cores, keyed by entryId. */
    private final Map<String, WarpCoreEntry> entries = new LinkedHashMap<>();

    /** Per-core power adapters, keyed by entryId. */
    private final Map<String, PowerSourceAdapter> adapters = new HashMap<>();

    public WarpCoreService(WarpCoreRepository repository, WarpCoreConfig config) {
        this.repository = repository;
        this.config = config;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void setServer(MinecraftServer server) {
        this.server = server;
        for (WarpCoreEntry e : entries.values()) {
            adapters.put(e.getEntryId(), buildAdapter(e));
        }
    }

    public void reload() {
        entries.clear();
        adapters.clear();
        for (WarpCoreEntry e : repository.loadAll()) {
            entries.put(e.getEntryId(), e);
            adapters.put(e.getEntryId(), buildAdapter(e));
        }

        if (entries.isEmpty()) {
            System.out.println("[SecondDawnRP] No warp cores registered.");
        } else {
            System.out.println("[SecondDawnRP] Loaded " + entries.size() + " warp core(s).");
        }
    }

    public void save() {
        repository.saveAll(entries.values());
    }

    private PowerSourceAdapter buildAdapter(WarpCoreEntry e) {
        if (server != null) {
            ServerWorld world = resolveWorld(e.getWorldKey());
            if (world != null) {
                BlockPos pos = BlockPos.fromLong(e.getBlockPosLong());
                TRenergyPowerAdapter tr = new TRenergyPowerAdapter(world, pos);
                if (tr.getStoredEnergy() > 0 || tr.getMaxCapacity() > 0) {
                    System.out.println("[SecondDawnRP] Warp core " + e.getEntryId()
                            + " using TREnergy via connected cables.");
                    return tr;
                }
                return tr;
            }
        }
        return new StandalonePowerAdapter(e, config);
    }

    // ── Server Tick ───────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        if (entries.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (WarpCoreEntry entry : new ArrayList<>(entries.values())) {
            // Reactor operation remains output-only from the core.
            PowerSourceAdapter adapter = adapters.get(entry.getEntryId());
            if (!(adapter instanceof StandalonePowerAdapter)) {
                adapter = new StandalonePowerAdapter(entry, config);
                adapters.put(entry.getEntryId(), adapter);
            }

            if (adapter instanceof StandalonePowerAdapter sa) {
                int health = getCoilHealth(entry);
                sa.updateCoilHealth(health < 0 ? 100 : health);
            }

            fillEnergyBuffer(entry, server);

            switch (entry.getState()) {
                case STARTING -> tickStarting(entry, adapter, now);
                case ONLINE   -> tickOnline(entry, adapter, now);
                case UNSTABLE -> tickUnstable(entry, adapter, now);
                case CRITICAL -> tickCritical(entry, adapter, now);
                case FAILED, OFFLINE -> {}
            }
        }
    }

    /** Public refresh — called when a player opens the monitor. Always returns standalone adapter. */
    public void refreshAdapterForEntry(WarpCoreEntry entry) {
        adapters.put(entry.getEntryId(), new StandalonePowerAdapter(entry, config));
    }

    /** Not used in normal operation, but preserved if you later want dynamic adapter switching again. */
    private PowerSourceAdapter refreshAdapter(WarpCoreEntry entry, PowerSourceAdapter current) {
        if (server == null) return current;

        ServerWorld world = resolveWorld(entry.getWorldKey());
        if (world == null) return current;

        BlockPos pos = BlockPos.fromLong(entry.getBlockPosLong());
        boolean hasTREnergy = false;

        try {
            for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
                net.minecraft.util.math.BlockPos adjacent = pos.offset(dir);
                if (team.reborn.energy.api.EnergyStorage.SIDED.find(world, adjacent, dir.getOpposite()) != null) {
                    hasTREnergy = true;
                    break;
                }
            }
        } catch (Throwable t) {
            return current;
        }

        if (hasTREnergy && !(current instanceof TRenergyPowerAdapter)) {
            System.out.println("[SecondDawnRP] Warp core " + entry.getEntryId()
                    + " — cable connected, switching to TREnergy adapter.");
            TRenergyPowerAdapter newAdapter = new TRenergyPowerAdapter(world, pos);
            entry.setCurrentPowerOutput(newAdapter.getPowerOutput());
            return newAdapter;
        }

        if (!hasTREnergy && current instanceof TRenergyPowerAdapter) {
            System.out.println("[SecondDawnRP] Warp core " + entry.getEntryId()
                    + " — cable disconnected, falling back to standalone adapter.");
            return new StandalonePowerAdapter(entry, config);
        }

        return current;
    }

    private void tickStarting(WarpCoreEntry entry, PowerSourceAdapter adapter, long now) {
        int remaining = entry.getStartupTicksRemaining();
        if (remaining > 0) {
            entry.setStartupTicksRemaining(remaining - 1);
            return;
        }

        String startupBlocker = getStartupBlocker(entry);
        if (startupBlocker != null) {
            broadcastEngineering(
                    Text.literal("[Warp Core] ").formatted(Formatting.RED)
                            .append(Text.literal(entry.getEntryId()).formatted(Formatting.WHITE))
                            .append(Text.literal(" STARTUP FAILURE — ").formatted(Formatting.RED))
                            .append(Text.literal(startupBlocker).formatted(Formatting.YELLOW))
            );

            entry.setStartupTicksRemaining(STARTUP_RETRY_TICKS);
            repository.saveAll(entries.values());
            return;
        }

        transitionTo(entry, ReactorState.ONLINE);
        entry.setCurrentPowerOutput(config.getPowerOutputNominal());
        broadcastAll(Text.literal("[Warp Core] " + entry.getEntryId()
                + " — reactor online. Power output nominal.").formatted(Formatting.GREEN));
        repository.saveAll(entries.values());
    }

    private void tickOnline(WarpCoreEntry entry, PowerSourceAdapter adapter, long now) {
        if (adapter instanceof StandalonePowerAdapter) {
            tickFuelDrain(entry, now);
        }

        int coilHealth = getCoilHealth(entry);
        int fuel = entry.getFuelRods();

        boolean fuelLow = adapter instanceof StandalonePowerAdapter
                && fuel <= config.getFuelCriticalThreshold();
        boolean coilLow = coilHealth >= 0
                && coilHealth < config.getCoilInstabilityThreshold();

        if (fuelLow || coilLow) {
            transitionTo(entry, ReactorState.UNSTABLE);
            entry.setCurrentPowerOutput(config.getPowerOutputUnstable());

            broadcastEngineering(Text.literal("[Warp Core] " + entry.getEntryId()
                    + " — WARNING: reactor destabilising.").formatted(Formatting.YELLOW));

            maybeGenerateFaultTask(entry,
                    fuelLow ? FaultType.FUEL_DEPLETED : FaultType.COIL_DEGRADED,
                    now);

            repository.saveAll(entries.values());
        }
    }

    private void tickUnstable(WarpCoreEntry entry, PowerSourceAdapter adapter, long now) {
        if (adapter instanceof StandalonePowerAdapter) {
            tickFuelDrain(entry, now);
        }

        int coilHealth = getCoilHealth(entry);
        int fuel = entry.getFuelRods();

        boolean fuelOut = adapter instanceof StandalonePowerAdapter && fuel <= 0;
        boolean coilDead = coilHealth == 0;

        if (fuelOut || coilDead) {
            triggerFault(entry,
                    fuelOut ? FaultType.FUEL_DEPLETED : FaultType.COIL_DEGRADED,
                    fuelOut ? "Fuel rods depleted." : "Resonance coil offline.");
        }
    }

    private void tickCritical(WarpCoreEntry entry, PowerSourceAdapter adapter, long now) {
        if (entry.getLastFaultTaskMs() > 0
                && System.currentTimeMillis() - entry.getLastFaultTaskMs()
                > config.getFaultTaskCooldownMs() * 3L) {
            triggerFault(entry, FaultType.CASCADING_FAILURE, "Critical fault unresolved.");
        }
    }

    private void tickFuelDrain(WarpCoreEntry entry, long now) {
        long elapsed = now - entry.getLastFuelDrainMs();
        if (elapsed < config.getFuelDrainIntervalMs()) return;

        int drain = config.getFuelDrainPerTickBase()
                + (int) Math.ceil(entry.getCurrentPowerOutput()
                * config.getFuelDrainOutputScale() / 100.0);

        entry.setFuelRods(entry.getFuelRods() - drain);
        entry.setLastFuelDrainMs(now);
        repository.saveAll(entries.values());
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    public boolean initiateStartup(String entryId, ServerPlayerEntity player) {
        WarpCoreEntry entry = entries.get(entryId);
        if (entry == null) {
            player.sendMessage(Text.literal("Unknown core: " + entryId).formatted(Formatting.RED), false);
            return false;
        }

        if (entry.getState() != ReactorState.OFFLINE) {
            player.sendMessage(Text.literal("Core is not OFFLINE (state: " + entry.getState() + ").")
                    .formatted(Formatting.RED), false);
            return false;
        }

        if (!hasReactorPermission(player)) {
            player.sendMessage(Text.literal("Requires engineering.reactor certification.")
                    .formatted(Formatting.RED), false);
            return false;
        }

        String startupBlocker = getStartupBlocker(entry);
        if (startupBlocker != null) {
            player.sendMessage(
                    Text.literal("[Warp Core] ").formatted(Formatting.RED)
                            .append(Text.literal("STARTUP DENIED — ").formatted(Formatting.DARK_RED))
                            .append(Text.literal(startupBlocker).formatted(Formatting.YELLOW)),
                    false
            );
            return false;
        }

        transitionTo(entry, ReactorState.STARTING);
        entry.setStartupTicksRemaining(config.getStartupDurationTicks());
        repository.saveAll(entries.values());

        broadcastEngineering(Text.literal("[Warp Core] " + entryId + " — startup initiated by "
                + player.getName().getString() + ".").formatted(Formatting.AQUA));
        return true;
    }

    public boolean initiateShutdown(String entryId, ServerPlayerEntity player) {
        WarpCoreEntry entry = entries.get(entryId);
        if (entry == null) {
            player.sendMessage(Text.literal("Unknown core: " + entryId).formatted(Formatting.RED), false);
            return false;
        }

        if (entry.getState() == ReactorState.OFFLINE || entry.getState() == ReactorState.FAILED) {
            player.sendMessage(Text.literal("Core is already " + entry.getState() + ".")
                    .formatted(Formatting.RED), false);
            return false;
        }

        if (!hasReactorPermission(player)) {
            player.sendMessage(Text.literal("Requires engineering.reactor certification.")
                    .formatted(Formatting.RED), false);
            return false;
        }

        transitionTo(entry, ReactorState.OFFLINE);
        entry.setCurrentPowerOutput(config.getPowerOutputOffline());
        entry.setStartupTicksRemaining(0);
        repository.saveAll(entries.values());

        broadcastEngineering(Text.literal("[Warp Core] " + entryId + " — shutdown initiated by "
                + player.getName().getString() + ".").formatted(Formatting.YELLOW));
        return true;
    }

    public boolean resetFromFailed(String entryId, ServerPlayerEntity player) {
        WarpCoreEntry entry = entries.get(entryId);
        if (entry == null) {
            player.sendMessage(Text.literal("Unknown core: " + entryId).formatted(Formatting.RED), false);
            return false;
        }

        if (entry.getState() != ReactorState.FAILED && entry.getState() != ReactorState.CRITICAL) {
            player.sendMessage(Text.literal("Core is not in a failed state.").formatted(Formatting.RED), false);
            return false;
        }

        if (!hasReactorPermission(player)) {
            player.sendMessage(Text.literal("Requires engineering.reactor certification.")
                    .formatted(Formatting.RED), false);
            return false;
        }

        String startupBlocker = getStartupBlocker(entry);
        if (startupBlocker != null) {
            player.sendMessage(
                    Text.literal("[Warp Core] ").formatted(Formatting.RED)
                            .append(Text.literal("RESET DENIED — ").formatted(Formatting.DARK_RED))
                            .append(Text.literal(startupBlocker).formatted(Formatting.YELLOW)),
                    false
            );
            return false;
        }

        transitionTo(entry, ReactorState.OFFLINE);
        entry.setCurrentPowerOutput(config.getPowerOutputOffline());
        entry.setStartupTicksRemaining(0);
        repository.saveAll(entries.values());

        broadcastEngineering(Text.literal("[Warp Core] " + entryId
                        + " — stability restored by " + player.getName().getString()
                        + ". Reactor reset to OFFLINE and ready for startup.")
                .formatted(Formatting.GREEN));
        return true;
    }

    public int loadFuel(String entryId, int count) {
        WarpCoreEntry entry = entries.get(entryId);
        if (entry == null) return 0;

        int space = config.getMaxFuelRods() - entry.getFuelRods();
        int toAdd = Math.min(count, space);
        if (toAdd > 0) {
            entry.setFuelRods(entry.getFuelRods() + toAdd);
            repository.saveAll(entries.values());
        }
        return toAdd;
    }

    public boolean linkResonanceCoil(String entryId, String componentId) {
        WarpCoreEntry entry = entries.get(entryId);
        if (entry == null) return false;

        entry.addResonanceCoil(componentId);
        repository.saveAll(entries.values());
        return true;
    }

    public boolean unlinkResonanceCoil(String entryId, String componentId) {
        WarpCoreEntry entry = entries.get(entryId);
        if (entry == null) return false;

        entry.removeResonanceCoil(componentId);
        repository.saveAll(entries.values());
        return true;
    }

    public boolean unlinkAllCoils(String entryId) {
        WarpCoreEntry entry = entries.get(entryId);
        if (entry == null) return false;

        new ArrayList<>(entry.getResonanceCoilIds()).forEach(entry::removeResonanceCoil);
        repository.saveAll(entries.values());
        return true;
    }

    public void injectFault(String entryId, FaultType type, String reason) {
        WarpCoreEntry entry = entries.get(entryId);
        if (entry != null) {
            triggerFault(entry, type, reason);
        }
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public WarpCoreEntry register(String worldKey, long blockPosLong, UUID registeredBy) {
        boolean exists = entries.values().stream()
                .anyMatch(e -> e.getWorldKey().equals(worldKey) && e.getBlockPosLong() == blockPosLong);

        if (exists) {
            throw new IllegalStateException("Already registered at this position.");
        }

        String entryId = "wc_" + Long.toHexString(blockPosLong & 0xFFFFFFL)
                + "_" + Long.toHexString(System.currentTimeMillis() & 0xFFFFL);

        WarpCoreEntry entry = new WarpCoreEntry(
                entryId,
                worldKey,
                blockPosLong,
                ReactorState.OFFLINE,
                0,
                System.currentTimeMillis(),
                0L,
                0,
                new ArrayList<>(),
                registeredBy
        );

        entries.put(entryId, entry);
        adapters.put(entryId, buildAdapter(entry));
        repository.saveAll(entries.values());

        System.out.println("[SecondDawnRP] Warp core registered: " + entryId + " at " + worldKey);
        return entry;
    }

    public boolean unregister(String entryId) {
        if (!entries.containsKey(entryId)) return false;

        entries.remove(entryId);
        adapters.remove(entryId);
        repository.delete(entryId);
        return true;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Collection<WarpCoreEntry> getAll() {
        return entries.values();
    }

    public Optional<WarpCoreEntry> getById(String entryId) {
        return Optional.ofNullable(entries.get(entryId));
    }

    public Optional<WarpCoreEntry> getByPosition(String worldKey, long blockPosLong) {
        return entries.values().stream()
                .filter(e -> e.getWorldKey().equals(worldKey) && e.getBlockPosLong() == blockPosLong)
                .findFirst();
    }

    /** Returns the single entry if only one core is registered — backward compat. */
    public Optional<WarpCoreEntry> getEntry() {
        if (entries.size() == 1) {
            return Optional.of(entries.values().iterator().next());
        }
        return Optional.empty();
    }

    public boolean isRegistered() {
        return !entries.isEmpty();
    }

    public int getPowerOutput() {
        return entries.values().stream()
                .mapToInt(WarpCoreEntry::getCurrentPowerOutput)
                .max()
                .orElse(0);
    }

    public PowerSourceAdapter getAdapter(String entryId) {
        return adapters.get(entryId);
    }

    /** Returns adapter for the single registered core — backward compat. */
    public PowerSourceAdapter getAdapter() {
        if (adapters.size() == 1) {
            return adapters.values().iterator().next();
        }
        return null;
    }

    public WarpCoreConfig getConfig() {
        return config;
    }

    /**
     * Returns weighted coil health across all linked coils.
     * Formula: (average * 0.7) + (worst * 0.3)
     * Returns -1 if no coils are linked.
     */
    public int getCoilHealth(WarpCoreEntry entry) {
        var ids = entry.getResonanceCoilIds();
        if (ids.isEmpty()) return -1;

        ArrayList<Integer> healths = new ArrayList<>();
        for (String id : ids) {
            SecondDawnRP.DEGRADATION_SERVICE.getById(id)
                    .map(ComponentEntry::getHealth)
                    .ifPresent(healths::add);
        }

        if (healths.isEmpty()) return -1;

        double avg = healths.stream().mapToInt(i -> i).average().orElse(0);
        int worst = healths.stream().mapToInt(i -> i).min().orElse(0);
        return (int) Math.round(avg * 0.7 + worst * 0.3);
    }

    public int getCoilHealth() {
        return getEntry().map(this::getCoilHealth).orElse(-1);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void triggerFault(WarpCoreEntry entry, FaultType type, String reason) {
        ReactorState newState = (type == FaultType.CASCADING_FAILURE || type == FaultType.STARTUP_FAILURE)
                ? ReactorState.FAILED
                : ReactorState.CRITICAL;

        transitionTo(entry, newState);
        entry.setCurrentPowerOutput(
                newState == ReactorState.FAILED
                        ? config.getPowerOutputOffline()
                        : config.getPowerOutputCritical()
        );

        repository.saveAll(entries.values());

        long now = System.currentTimeMillis();
        maybeGenerateFaultTask(entry, type, now);

        broadcastAll(Text.literal("[Warp Core] " + entry.getEntryId()
                        + " FAULT: " + type.getDisplayName() + " — " + reason)
                .formatted(Formatting.RED));
    }

    private void maybeGenerateFaultTask(WarpCoreEntry entry, FaultType type, long now) {
        if (now - entry.getLastFaultTaskMs() < config.getFaultTaskCooldownMs()) return;

        String displayName = "Warp Core Fault: " + type.getDisplayName();
        SecondDawnRP.TASK_SERVICE.createPoolTask(
                "warpcore_fault_" + Long.toHexString(now & 0xFFFFFFL),
                displayName,
                type.getTaskDescription() + " [" + entry.getEntryId() + " at "
                        + entry.getWorldKey() + ":" + entry.getBlockPosLong() + "]",
                Division.ENGINEERING,
                TaskObjectiveType.MANUAL_CONFIRM,
                "warpcore",
                1,
                30,
                true,
                null
        );
        entry.setLastFaultTaskMs(now);
    }

    private void transitionTo(WarpCoreEntry entry, ReactorState newState) {
        ReactorState old = entry.getState();
        entry.setState(newState);

        System.out.println("[SecondDawnRP] WarpCore " + entry.getEntryId() + ": " + old + " → " + newState);

        if (newState == ReactorState.CRITICAL || newState == ReactorState.FAILED) {
            SecondDawnRP.DEGRADATION_SERVICE.setReactorCritical(true, config.getCriticalDegradationMultiplier());
        } else if (old == ReactorState.CRITICAL || old == ReactorState.FAILED) {
            SecondDawnRP.DEGRADATION_SERVICE.setReactorCritical(false, 1.0);
        }
    }

    /**
     * Fills the controller block entity energy buffer each tick.
     * Uses a remaining budget so output is not spam-attempted into every side.
     */
    private void fillEnergyBuffer(WarpCoreEntry entry, MinecraftServer server) {
        if (server == null) return;

        int powerOutput = entry.getCurrentPowerOutput();
        if (powerOutput <= 0) return;

        long maxOutput = config.getMaxEnergyOutputPerTick();
        long remaining = maxOutput * powerOutput / 100L;
        if (remaining <= 0) return;

        ServerWorld world = resolveWorld(entry.getWorldKey());
        if (world == null) return;

        BlockPos pos = BlockPos.fromLong(entry.getBlockPosLong());

        try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            long insertedTotal = 0L;

            for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
                if (remaining <= 0) break;

                BlockPos adjacent = pos.offset(dir);
                var target = team.reborn.energy.api.EnergyStorage.SIDED.find(world, adjacent, dir.getOpposite());
                if (target == null || !target.supportsInsertion()) continue;

                long inserted = target.insert(remaining, transaction);
                if (inserted > 0) {
                    remaining -= inserted;
                    insertedTotal += inserted;
                }
            }

            if (insertedTotal > 0) {
                transaction.commit();
            }
        } catch (Throwable t) {
            if (server.getTicks() % 200 == 0) {
                System.out.println("[SecondDawnRP] Energy push error for "
                        + entry.getEntryId() + ": " + t.getMessage());
            }
        }
    }

    /**
     * Returns null when startup conditions are satisfied, or a player-facing blocker string when not.
     */
    private String getStartupBlocker(WarpCoreEntry entry) {
        int coilHealth = getCoilHealth(entry);
        if (coilHealth >= 0 && coilHealth < config.getCoilStartupMinimumHealth()) {
            return "COIL INSTABILITY — resonance coil health too low (" + coilHealth + "/100)";
        }

        if (entry.getFuelRods() <= config.getFuelCriticalThreshold()) {
            return "INSUFFICIENT FUEL — not enough fuel rods loaded";
        }

        long minGen = config.getStartupMinGeneratorEnergy();
        if (minGen > 0 && !hasAdjacentGeneratorEnergy(entry, minGen)) {
            return "INSUFFICIENT POWER — requires at least " + minGen + " startup energy on an adjacent face";
        }

        return null;
    }

    private void broadcastAll(Text message) {
        if (server == null) return;
        server.getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(message, false));
    }

    private void broadcastEngineering(Text message) {
        if (server == null) return;
        server.getPlayerManager().getPlayerList().stream()
                .filter(p -> {
                    var profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(p.getUuid());
                    return profile != null && Division.ENGINEERING.equals(profile.getDivision());
                })
                .forEach(p -> p.sendMessage(message, false));
    }

    private boolean hasReactorPermission(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, "st.engineering.admin")
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, "engineering.reactor");
    }

    /** Checks if any adjacent block exposes TREnergy with at least minEnergy stored. */
    private boolean hasAdjacentGeneratorEnergy(WarpCoreEntry entry, long minEnergy) {
        if (server == null) return false;

        ServerWorld world = resolveWorld(entry.getWorldKey());
        if (world == null) return false;

        BlockPos pos = BlockPos.fromLong(entry.getBlockPosLong());

        try {
            for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
                BlockPos adjacent = pos.offset(dir);
                var storage = team.reborn.energy.api.EnergyStorage.SIDED.find(world, adjacent, dir.getOpposite());
                if (storage != null && storage.getAmount() >= minEnergy) {
                    return true;
                }
            }
        } catch (Throwable t) {
            // If TREnergy is absent or fails, don't hard-lock dev environments.
            return true;
        }

        return false;
    }

    private ServerWorld resolveWorld(String worldKey) {
        if (server == null) return null;

        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(worldKey)) {
                return w;
            }
        }
        return null;
    }

    public int getTotalPowerOutput() {
        return entries.values().stream()
                .filter(core -> core.getState() != ReactorState.OFFLINE
                        && core.getState() != ReactorState.FAILED)
                .mapToInt(WarpCoreEntry::getCurrentPowerOutput)
                .sum();
    }

    public String getOverallState() {
        return entries.values().stream()
                .map(c -> c.getState().name())
                .reduce("ONLINE", (a, b) -> {
                    int pa = statePriority(a);
                    int pb = statePriority(b);
                    return pa > pb ? a : b;
                });
    }

    private int statePriority(String state) {
        return switch (state) {
            case "FAILED"   -> 5;
            case "CRITICAL" -> 4;
            case "UNSTABLE" -> 3;
            case "STARTING" -> 2;
            case "ONLINE"   -> 1;
            default         -> 0;
        };
    }
}