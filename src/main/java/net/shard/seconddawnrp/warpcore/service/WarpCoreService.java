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
import net.shard.seconddawnrp.tasksystem.data.OpsTaskStatus;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;
import net.shard.seconddawnrp.warpcore.adapter.PowerSourceAdapter;
import net.shard.seconddawnrp.warpcore.adapter.StandalonePowerAdapter;
import net.shard.seconddawnrp.warpcore.data.FaultType;
import net.shard.seconddawnrp.warpcore.data.ReactorState;
import net.shard.seconddawnrp.warpcore.data.WarpCoreConfig;
import net.shard.seconddawnrp.warpcore.data.WarpCoreEntry;
import net.shard.seconddawnrp.warpcore.repository.WarpCoreRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Core service for the warp core system.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Reactor state machine — OFFLINE → STARTING → ONLINE → UNSTABLE → CRITICAL → FAILED
 *   <li>Startup / shutdown sequences with tick countdown
 *   <li>Fuel consumption — base real-time drain + output-scaled bonus drain
 *   <li>Resonance coil health monitoring via degradation system
 *   <li>Fault detection and task auto-generation
 *   <li>Power output reporting (fed to ShipState stub in Phase 12)
 *   <li>Degradation multiplier injection — CRITICAL state accelerates component drain
 * </ul>
 */
public class WarpCoreService {

    private final WarpCoreRepository repository;
    private final WarpCoreConfig config;

    private WarpCoreEntry entry;
    private PowerSourceAdapter adapter;
    private MinecraftServer server;

    public WarpCoreService(WarpCoreRepository repository, WarpCoreConfig config) {
        this.repository = repository;
        this.config = config;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void reload() {
        Optional<WarpCoreEntry> loaded = repository.load();
        if (loaded.isPresent()) {
            this.entry = loaded.get();
            // Rebuild adapter from loaded entry
            this.adapter = new StandalonePowerAdapter(entry, config);
            System.out.println("[SecondDawnRP] Warp core loaded — state: " + entry.getState()
                    + ", fuel: " + entry.getFuelRods());
        } else {
            this.entry = null;
            this.adapter = null;
            System.out.println("[SecondDawnRP] No warp core registered.");
        }
    }

    public void save() {
        if (entry != null) repository.save(entry);
    }

    // ── Server Tick ───────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        if (entry == null) return;

        long now = System.currentTimeMillis();

        // Update coil health from degradation system
        updateCoilHealth();

        // Tick state machine
        switch (entry.getState()) {
            case STARTING  -> tickStarting(now);
            case ONLINE    -> tickOnline(now);
            case UNSTABLE  -> tickUnstable(now);
            case CRITICAL  -> tickCritical(now);
            case FAILED    -> {} // wait for manual reset
            case OFFLINE   -> {} // idle
        }

        // Update power output on adapter
        if (adapter instanceof StandalonePowerAdapter sa) {
            int coilHealth = getCoilHealth();
            sa.updateCoilHealth(coilHealth);
        }
    }

    private void tickStarting(long now) {
        int remaining = entry.getStartupTicksRemaining();
        if (remaining > 0) {
            entry.setStartupTicksRemaining(remaining - 1);
            return;
        }

        // Startup sequence complete — validate conditions
        int coilHealth = getCoilHealth();
        if (coilHealth < config.getCoilStartupMinimumHealth()) {
            triggerFault(FaultType.STARTUP_FAILURE,
                    "Startup failed — resonance coil health too low (" + coilHealth + "/100).");
            return;
        }
        if (entry.getFuelRods() <= config.getFuelCriticalThreshold()) {
            triggerFault(FaultType.FUEL_DEPLETED,
                    "Startup failed — insufficient fuel rods.");
            return;
        }

        transitionTo(ReactorState.ONLINE);
        entry.setCurrentPowerOutput(config.getPowerOutputNominal());
        broadcastAll(Text.literal("[Warp Core] Reactor online. Power output nominal.")
                .formatted(Formatting.GREEN));
        repository.save(entry);
    }

    private void tickOnline(long now) {
        tickFuelDrain(now);

        int coilHealth = getCoilHealth();
        int fuel = entry.getFuelRods();

        // Check for instability conditions
        if (fuel <= config.getFuelCriticalThreshold()
                || coilHealth < config.getCoilInstabilityThreshold()) {
            transitionTo(ReactorState.UNSTABLE);
            entry.setCurrentPowerOutput(config.getPowerOutputUnstable());
            broadcastEngineering(Text.literal("[Warp Core] WARNING — reactor destabilising.")
                    .formatted(Formatting.YELLOW));
            maybeGenerateFaultTask(
                    fuel <= config.getFuelCriticalThreshold()
                            ? FaultType.FUEL_DEPLETED : FaultType.COIL_DEGRADED, now);
            repository.save(entry);
        } else if (fuel <= config.getFuelWarningThreshold()
                || coilHealth < config.getCoilInstabilityThreshold() + 10) {
            // Warning only — still ONLINE
            broadcastEngineering(Text.literal("[Warp Core] Caution — fuel or coil approaching warning threshold.")
                    .formatted(Formatting.YELLOW));
        }
    }

    private void tickUnstable(long now) {
        tickFuelDrain(now);

        int coilHealth = getCoilHealth();
        int fuel = entry.getFuelRods();

        if (fuel <= 0) {
            triggerFault(FaultType.FUEL_DEPLETED, "Fuel exhausted.");
            return;
        }
        if (coilHealth <= 0) {
            triggerFault(FaultType.COIL_DEGRADED, "Resonance coil failed.");
            return;
        }

        // If conditions recover, return to ONLINE
        if (fuel > config.getFuelCriticalThreshold()
                && coilHealth >= config.getCoilInstabilityThreshold()) {
            transitionTo(ReactorState.ONLINE);
            entry.setCurrentPowerOutput(config.getPowerOutputNominal());
            broadcastEngineering(Text.literal("[Warp Core] Reactor stabilised.")
                    .formatted(Formatting.GREEN));
            repository.save(entry);
        }
    }

    private void tickCritical(long now) {
        tickFuelDrain(now);

        if (entry.getFuelRods() <= 0 || getCoilHealth() <= 0) {
            triggerFault(FaultType.CASCADING_FAILURE, "Cascading failure.");
        }
    }

    private void tickFuelDrain(long now) {
        long elapsed = now - entry.getLastFuelDrainMs();
        if (elapsed < config.getFuelDrainIntervalMs()) return;

        int drain = config.getFuelDrainPerTickBase()
                + (entry.getCurrentPowerOutput() * config.getFuelDrainOutputScale() / 100);
        drain = Math.max(1, drain);

        entry.setFuelRods(entry.getFuelRods() - drain);
        entry.setLastFuelDrainMs(now);
        repository.save(entry);
    }

    // ── State Transitions ─────────────────────────────────────────────────────

    /**
     * Initiate startup sequence. Validates permissions and conditions.
     *
     * @return true if startup was initiated, false with reason sent to player
     */
    public boolean initiateStartup(ServerPlayerEntity player) {
        if (entry == null) {
            player.sendMessage(Text.literal("No warp core registered.").formatted(Formatting.RED), false);
            return false;
        }
        if (!entry.getState().canStartup()) {
            player.sendMessage(Text.literal("Cannot start reactor from state: "
                    + entry.getState().name()).formatted(Formatting.RED), false);
            return false;
        }
        if (!hasReactorPermission(player)) {
            player.sendMessage(Text.literal("You do not have the engineering.reactor certification.")
                    .formatted(Formatting.RED), false);
            return false;
        }

        int coilHealth = getCoilHealth();
        if (coilHealth < config.getCoilStartupMinimumHealth()) {
            player.sendMessage(
                    Text.literal("Startup blocked — resonance coil health too low ("
                                    + coilHealth + "/100, minimum " + config.getCoilStartupMinimumHealth() + ").")
                            .formatted(Formatting.RED), false);
            return false;
        }
        if (entry.getFuelRods() <= config.getFuelCriticalThreshold()) {
            player.sendMessage(
                    Text.literal("Startup blocked — insufficient fuel rods ("
                                    + entry.getFuelRods() + " loaded, need >"
                                    + config.getFuelCriticalThreshold() + ").")
                            .formatted(Formatting.RED), false);
            return false;
        }

        transitionTo(ReactorState.STARTING);
        entry.setStartupTicksRemaining(config.getStartupDurationTicks());
        repository.save(entry);

        broadcastEngineering(Text.literal("[Warp Core] Startup sequence initiated by "
                        + player.getName().getString() + ". Stand by.")
                .formatted(Formatting.AQUA));
        return true;
    }

    /**
     * Initiate shutdown sequence.
     */
    public boolean initiateShutdown(ServerPlayerEntity player) {
        if (entry == null) {
            player.sendMessage(Text.literal("No warp core registered.").formatted(Formatting.RED), false);
            return false;
        }
        if (!entry.getState().canShutdown()) {
            player.sendMessage(Text.literal("Cannot shut down reactor from state: "
                    + entry.getState().name()).formatted(Formatting.RED), false);
            return false;
        }
        if (!hasReactorPermission(player)) {
            player.sendMessage(Text.literal("You do not have the engineering.reactor certification.")
                    .formatted(Formatting.RED), false);
            return false;
        }

        transitionTo(ReactorState.OFFLINE);
        entry.setCurrentPowerOutput(config.getPowerOutputOffline());
        repository.save(entry);

        broadcastEngineering(Text.literal("[Warp Core] Reactor shutdown initiated by "
                        + player.getName().getString() + ".")
                .formatted(Formatting.YELLOW));
        return true;
    }

    /**
     * Manually inject a fault — GM use only.
     */
    public void injectFault(FaultType type, String reason) {
        triggerFault(type, reason);
    }

    /**
     * Reset from FAILED to OFFLINE — requires st.engineering.admin or GM.
     */
    public boolean resetFromFailed(ServerPlayerEntity player) {
        if (entry == null || entry.getState() != ReactorState.FAILED) {
            player.sendMessage(Text.literal("Reactor is not in FAILED state.").formatted(Formatting.RED), false);
            return false;
        }
        transitionTo(ReactorState.OFFLINE);
        entry.setCurrentPowerOutput(0);
        repository.save(entry);
        broadcastEngineering(Text.literal("[Warp Core] Emergency reset performed by "
                        + player.getName().getString() + ". Reactor offline.")
                .formatted(Formatting.YELLOW));
        return true;
    }

    // ── Fuel Management ───────────────────────────────────────────────────────

    /**
     * Load fuel rods into the reactor.
     *
     * @param count number of rods to add
     * @return how many were actually added (capped at max)
     */
    public int loadFuel(int count) {
        if (entry == null) return 0;
        int space = config.getMaxFuelRods() - entry.getFuelRods();
        int toAdd = Math.min(count, space);
        entry.setFuelRods(entry.getFuelRods() + toAdd);
        repository.save(entry);
        return toAdd;
    }

    // ── Resonance Coil Linking ────────────────────────────────────────────────

    /**
     * Link a registered degradation component as the resonance coil.
     * The component must already exist in the degradation system.
     */
    public boolean linkResonanceCoil(String componentId) {
        if (entry == null) return false;
        entry.setResonanceCoilComponentId(componentId);
        repository.save(entry);
        return true;
    }

    // ── Registration ─────────────────────────────────────────────────────────

    public boolean register(String worldKey, long blockPosLong, UUID registeredBy) {
        if (entry != null) return false; // only one warp core
        entry = new WarpCoreEntry(
                worldKey, blockPosLong, ReactorState.OFFLINE,
                0, System.currentTimeMillis(), 0L, 0, null, registeredBy);
        adapter = new StandalonePowerAdapter(entry, config);
        repository.save(entry);
        System.out.println("[SecondDawnRP] Warp core registered at " + worldKey + ":" + blockPosLong);
        return true;
    }

    public void unregister() {
        entry = null;
        adapter = null;
        repository.delete();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Optional<WarpCoreEntry> getEntry() {
        return Optional.ofNullable(entry);
    }

    public boolean isRegistered() {
        return entry != null;
    }

    /** Current power output — read by ShipState in Phase 12. */
    public int getPowerOutput() {
        return entry != null ? entry.getCurrentPowerOutput() : 0;
    }

    public PowerSourceAdapter getAdapter() {
        return adapter;
    }

    public WarpCoreConfig getConfig() {
        return config;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void triggerFault(FaultType type, String reason) {
        ReactorState newState = type == FaultType.CASCADING_FAILURE
                || type == FaultType.STARTUP_FAILURE ? ReactorState.FAILED : ReactorState.CRITICAL;

        transitionTo(newState);
        entry.setCurrentPowerOutput(newState == ReactorState.FAILED
                ? config.getPowerOutputOffline() : config.getPowerOutputCritical());
        repository.save(entry);

        long now = System.currentTimeMillis();
        maybeGenerateFaultTask(type, now);

        String msg = "[Warp Core] FAULT: " + type.getDisplayName() + " — " + reason;
        broadcastAll(Text.literal(msg).formatted(Formatting.RED));
    }

    private void maybeGenerateFaultTask(FaultType type, long now) {
        if (now - entry.getLastFaultTaskMs() < config.getFaultTaskCooldownMs()) return;

        String displayName = "Warp Core Fault: " + type.getDisplayName();
        SecondDawnRP.TASK_SERVICE.createPoolTask(
                "warpcore_fault_" + Long.toHexString(now & 0xFFFFFFL),
                displayName,
                type.getTaskDescription()
                        + " [warpcore:" + entry.getWorldKey() + ":"
                        + entry.getBlockPosLong() + "]",
                Division.ENGINEERING,
                TaskObjectiveType.MANUAL_CONFIRM,
                "warpcore",
                1,
                75,
                true,
                null
        );
        entry.setLastFaultTaskMs(now);
        System.out.println("[SecondDawnRP] Auto-generated warp core fault task: " + displayName);
    }

    private void transitionTo(ReactorState newState) {
        ReactorState old = entry.getState();
        entry.setState(newState);
        System.out.println("[SecondDawnRP] Warp core: " + old + " → " + newState);

        // Notify degradation service of critical state for multiplier
        if (newState == ReactorState.CRITICAL || newState == ReactorState.FAILED) {
            SecondDawnRP.DEGRADATION_SERVICE.setReactorCritical(true,
                    config.getCriticalDegradationMultiplier());
        } else if (old == ReactorState.CRITICAL || old == ReactorState.FAILED) {
            SecondDawnRP.DEGRADATION_SERVICE.setReactorCritical(false, 1.0);
        }
    }

    private int getCoilHealth() {
        if (entry == null || entry.getResonanceCoilComponentId() == null) return 100;
        return SecondDawnRP.DEGRADATION_SERVICE
                .getById(entry.getResonanceCoilComponentId())
                .map(ComponentEntry::getHealth)
                .orElse(100);
    }

    private void updateCoilHealth() {
        if (adapter instanceof StandalonePowerAdapter sa) {
            sa.updateCoilHealth(getCoilHealth());
        }
    }

    private void broadcastAll(Text message) {
        if (server == null) return;
        server.getPlayerManager().getPlayerList()
                .forEach(p -> p.sendMessage(message, false));
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
}