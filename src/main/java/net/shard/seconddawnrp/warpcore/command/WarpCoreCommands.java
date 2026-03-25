package net.shard.seconddawnrp.warpcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.warpcore.data.FaultType;
import net.shard.seconddawnrp.warpcore.data.WarpCoreEntry;

/**
 * /warpcore commands — all targeting commands accept an optional [id] argument.
 * When only one core is registered, [id] may be omitted.
 *
 * <pre>
 * /warpcore list                        — list all registered cores
 * /warpcore status [id]                 — detailed status
 * /warpcore startup [id]                — initiate startup sequence
 * /warpcore shutdown [id]               — initiate controlled shutdown
 * /warpcore reset [id]                  — reset from FAILED/CRITICAL
 * /warpcore fuel add <n> [id]           — add fuel rods
 * /warpcore linkcoil <componentId> [id] — link resonance coil
 * /warpcore unlinkcoil [id]             — unlink resonance coil
 * /warpcore sources [id]                — show power source info
 * /warpcore fault <type> [id]           — inject fault (admin)
 * /warpcore unregister [id]             — remove registration (admin)
 * </pre>
 */
public final class WarpCoreCommands {

    private WarpCoreCommands() {}

    private static final SuggestionProvider<ServerCommandSource> CORE_ID =
            (ctx, builder) -> {
                SecondDawnRP.WARP_CORE_SERVICE.getAll()
                        .forEach(e -> builder.suggest(e.getEntryId(),
                                Text.literal(e.getState().name() + " — fuel: " + e.getFuelRods())));
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> COMPONENT_ID =
            (ctx, builder) -> {
                SecondDawnRP.DEGRADATION_SERVICE.getAllComponents()
                        .forEach(c -> builder.suggest(c.getComponentId(),
                                Text.literal(c.getDisplayName() + " — " + c.getStatus().name())));
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> FAULT_TYPES =
            (ctx, builder) -> {
                for (FaultType t : FaultType.values()) builder.suggest(t.name());
                return builder.buildFuture();
            };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess access,
                                CommandManager.RegistrationEnvironment env) {
        var warpcore = CommandManager.literal("warpcore");

        // list
        warpcore.then(CommandManager.literal("list")
                .executes(ctx -> executeList(ctx.getSource())));

        // status [id]
        warpcore.then(CommandManager.literal("status")
                .executes(ctx -> executeStatus(ctx.getSource(), resolveId(ctx.getSource())))
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(CORE_ID)
                        .executes(ctx -> executeStatus(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        // startup [id]
        warpcore.then(CommandManager.literal("startup")
                .executes(ctx -> executeStartup(ctx.getSource(), resolveId(ctx.getSource())))
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(CORE_ID)
                        .executes(ctx -> executeStartup(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        // shutdown [id]
        warpcore.then(CommandManager.literal("shutdown")
                .executes(ctx -> executeShutdown(ctx.getSource(), resolveId(ctx.getSource())))
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(CORE_ID)
                        .executes(ctx -> executeShutdown(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        // reset [id]
        warpcore.then(CommandManager.literal("reset")
                .executes(ctx -> executeReset(ctx.getSource(), resolveId(ctx.getSource())))
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(CORE_ID)
                        .executes(ctx -> executeReset(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        // fuel add <n> [id]
        warpcore.then(CommandManager.literal("fuel")
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> executeFuelAdd(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "count"),
                                        resolveId(ctx.getSource())))
                                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(CORE_ID)
                                        .executes(ctx -> executeFuelAdd(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                StringArgumentType.getString(ctx, "id")))))));

        // linkcoil <componentId> [id]
        warpcore.then(CommandManager.literal("linkcoil")
                .then(CommandManager.argument("componentId", StringArgumentType.word())
                        .suggests(COMPONENT_ID)
                        .executes(ctx -> executeLinkCoil(ctx.getSource(),
                                StringArgumentType.getString(ctx, "componentId"),
                                resolveId(ctx.getSource())))
                        .then(CommandManager.argument("id", StringArgumentType.word()).suggests(CORE_ID)
                                .executes(ctx -> executeLinkCoil(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "componentId"),
                                        StringArgumentType.getString(ctx, "id"))))));

        // unlinkcoil <componentId> [coreId]
        warpcore.then(CommandManager.literal("unlinkcoil")
                .requires(src -> isAdmin(src))
                .then(CommandManager.argument("componentId", StringArgumentType.word())
                        .suggests(COMPONENT_ID)
                        .executes(ctx -> executeUnlinkCoil(ctx.getSource(),
                                StringArgumentType.getString(ctx, "componentId"),
                                resolveId(ctx.getSource())))
                        .then(CommandManager.argument("id", StringArgumentType.word()).suggests(CORE_ID)
                                .executes(ctx -> executeUnlinkCoil(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "componentId"),
                                        StringArgumentType.getString(ctx, "id"))))));

        // unlinkallcoils [id]
        warpcore.then(CommandManager.literal("unlinkallcoils")
                .requires(src -> isAdmin(src))
                .executes(ctx -> executeUnlinkAllCoils(ctx.getSource(), resolveId(ctx.getSource())))
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(CORE_ID)
                        .executes(ctx -> executeUnlinkAllCoils(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        // sources [id]
        warpcore.then(CommandManager.literal("sources")
                .executes(ctx -> executeSources(ctx.getSource(), resolveId(ctx.getSource())))
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(CORE_ID)
                        .executes(ctx -> executeSources(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        // fault <type> [id] — admin
        warpcore.then(CommandManager.literal("fault")
                .requires(src -> isAdmin(src))
                .then(CommandManager.argument("type", StringArgumentType.word()).suggests(FAULT_TYPES)
                        .executes(ctx -> executeFault(ctx.getSource(),
                                StringArgumentType.getString(ctx, "type"),
                                resolveId(ctx.getSource())))
                        .then(CommandManager.argument("id", StringArgumentType.word()).suggests(CORE_ID)
                                .executes(ctx -> executeFault(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "type"),
                                        StringArgumentType.getString(ctx, "id"))))));

        // unregister [id] — admin
        warpcore.then(CommandManager.literal("unregister")
                .requires(src -> isAdmin(src))
                .executes(ctx -> executeUnregister(ctx.getSource(), resolveId(ctx.getSource())))
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(CORE_ID)
                        .executes(ctx -> executeUnregister(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        dispatcher.register(warpcore);
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static int executeList(ServerCommandSource src) {
        var all = SecondDawnRP.WARP_CORE_SERVICE.getAll();
        if (all.isEmpty()) {
            src.sendFeedback(() -> Text.literal("No warp cores registered.").formatted(Formatting.GRAY), false);
            return 1;
        }
        src.sendFeedback(() -> Text.literal("── Warp Cores ──").formatted(Formatting.GOLD), false);
        all.forEach(e -> {
            int coil = SecondDawnRP.WARP_CORE_SERVICE.getCoilHealth(e);
            String coilStr = coil < 0 ? "no coil" : coil + "/100";
            src.sendFeedback(() -> Text.literal("  " + e.getEntryId()
                            + " — " + e.getState().name()
                            + " | fuel: " + e.getFuelRods()
                            + " | coil: " + coilStr)
                    .formatted(stateColour(e)), false);
        });
        return 1;
    }

    private static int executeStatus(ServerCommandSource src, String id) {
        if (id == null) { src.sendError(Text.literal("No core registered or specify an ID.")); return 0; }
        var opt = SecondDawnRP.WARP_CORE_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown core: " + id)); return 0; }
        WarpCoreEntry e = opt.get();
        var adapter = SecondDawnRP.WARP_CORE_SERVICE.getAdapter(id);
        int coil = SecondDawnRP.WARP_CORE_SERVICE.getCoilHealth(e);

        src.sendFeedback(() -> Text.literal("── Warp Core: " + id + " ──").formatted(Formatting.GOLD), false);
        src.sendFeedback(() -> Text.literal("State:  " + e.getState().name()).formatted(stateColour(e)), false);
        src.sendFeedback(() -> Text.literal("Fuel:   " + e.getFuelRods() + " / "
                + SecondDawnRP.WARP_CORE_SERVICE.getConfig().getMaxFuelRods() + " rods").formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("Power:  " + e.getCurrentPowerOutput() + "%").formatted(Formatting.GRAY), false);
        var coilIds = e.getResonanceCoilIds();
        if (coilIds.isEmpty()) {
            src.sendFeedback(() -> Text.literal("Coils:  Not linked").formatted(Formatting.GRAY), false);
        } else {
            src.sendFeedback(() -> Text.literal("Coils:  " + coilIds.size() + " linked | effective health: " + coil + "/100").formatted(Formatting.GRAY), false);
            coilIds.forEach(cid -> src.sendFeedback(() ->
                    Text.literal("  - " + cid).formatted(Formatting.DARK_GRAY), false));
        }
        if (adapter != null)
            src.sendFeedback(() -> Text.literal("Source: " + adapter.getPrimaryFuelLabel()
                    + " (" + adapter.getFuelLevelPercent() + "%)").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int executeStartup(ServerCommandSource src, String id) {
        if (id == null) { src.sendError(Text.literal("No core registered or specify an ID.")); return 0; }
        var player = src.getPlayer();
        if (player == null) { src.sendError(Text.literal("Must be a player.")); return 0; }
        SecondDawnRP.WARP_CORE_SERVICE.initiateStartup(id, player);
        return 1;
    }

    private static int executeShutdown(ServerCommandSource src, String id) {
        if (id == null) { src.sendError(Text.literal("No core registered or specify an ID.")); return 0; }
        var player = src.getPlayer();
        if (player == null) { src.sendError(Text.literal("Must be a player.")); return 0; }
        SecondDawnRP.WARP_CORE_SERVICE.initiateShutdown(id, player);
        return 1;
    }

    private static int executeReset(ServerCommandSource src, String id) {
        if (id == null) { src.sendError(Text.literal("No core registered or specify an ID.")); return 0; }
        var player = src.getPlayer();
        if (player == null) { src.sendError(Text.literal("Must be a player.")); return 0; }
        SecondDawnRP.WARP_CORE_SERVICE.resetFromFailed(id, player);
        return 1;
    }

    private static int executeFuelAdd(ServerCommandSource src, int count, String id) {
        if (id == null) { src.sendError(Text.literal("No core registered or specify an ID.")); return 0; }
        int loaded = SecondDawnRP.WARP_CORE_SERVICE.loadFuel(id, count);
        src.sendFeedback(() -> Text.literal("Loaded " + loaded + " fuel rods into " + id + ".")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeLinkCoil(ServerCommandSource src, String componentId, String id) {
        if (id == null) { src.sendError(Text.literal("No core registered or specify an ID.")); return 0; }
        if (SecondDawnRP.WARP_CORE_SERVICE.linkResonanceCoil(id, componentId)) {
            src.sendFeedback(() -> Text.literal("Resonance coil '" + componentId + "' linked to " + id + ".")
                    .formatted(Formatting.GREEN), false);
        } else {
            src.sendError(Text.literal("Unknown core: " + id));
        }
        return 1;
    }

    private static int executeUnlinkCoil(ServerCommandSource src, String componentId, String id) {
        if (id == null) { src.sendError(Text.literal("No core registered or specify an ID.")); return 0; }
        if (SecondDawnRP.WARP_CORE_SERVICE.unlinkResonanceCoil(id, componentId)) {
            src.sendFeedback(() -> Text.literal("Coil '" + componentId + "' unlinked from " + id + ".")
                    .formatted(Formatting.YELLOW), false);
        } else {
            src.sendError(Text.literal("Unknown core: " + id));
        }
        return 1;
    }

    private static int executeUnlinkAllCoils(ServerCommandSource src, String id) {
        if (id == null) { src.sendError(Text.literal("No core registered or specify an ID.")); return 0; }
        if (SecondDawnRP.WARP_CORE_SERVICE.unlinkAllCoils(id)) {
            src.sendFeedback(() -> Text.literal("All coils unlinked from " + id + ".")
                    .formatted(Formatting.YELLOW), false);
        } else {
            src.sendError(Text.literal("Unknown core: " + id));
        }
        return 1;
    }

    private static int executeSources(ServerCommandSource src, String id) {
        if (id == null) { src.sendError(Text.literal("No core registered or specify an ID.")); return 0; }
        var opt = SecondDawnRP.WARP_CORE_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown core: " + id)); return 0; }
        WarpCoreEntry e = opt.get();
        var adapter = SecondDawnRP.WARP_CORE_SERVICE.getAdapter(id);
        src.sendFeedback(() -> Text.literal("── Power Sources: " + id + " ──").formatted(Formatting.GOLD), false);

        // Raw TREnergy face scan — shows exactly what each face sees
        if (src.getServer() != null) {
            for (net.minecraft.server.world.ServerWorld w : src.getServer().getWorlds()) {
                if (!w.getRegistryKey().getValue().toString().equals(e.getWorldKey())) continue;
                net.minecraft.util.math.BlockPos pos = net.minecraft.util.math.BlockPos.fromLong(e.getBlockPosLong());
                src.sendFeedback(() -> Text.literal("  Controller pos: " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + " in " + e.getWorldKey()).formatted(Formatting.GRAY), false);
                for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.values()) {
                    try {
                        // Query the adjacent block's face pointing back at the controller
                        net.minecraft.util.math.BlockPos adjacent = pos.offset(dir);
                        var storage = team.reborn.energy.api.EnergyStorage.SIDED.find(w, adjacent, dir.getOpposite());
                        if (storage != null) {
                            src.sendFeedback(() -> Text.literal("  [" + dir.name() + " adjacent] TREnergy found: "
                                            + storage.getAmount() + " / " + storage.getCapacity() + " E")
                                    .formatted(Formatting.GREEN), false);
                        } else {
                            src.sendFeedback(() -> Text.literal("  [" + dir.name() + " adjacent] no energy storage")
                                    .formatted(Formatting.DARK_GRAY), false);
                        }
                    } catch (Throwable t) {
                        src.sendFeedback(() -> Text.literal("  [" + dir.name() + "] TREnergy API error: " + t.getMessage())
                                .formatted(Formatting.RED), false);
                    }
                }
                break;
            }
        }

        if (adapter instanceof net.shard.seconddawnrp.warpcore.adapter.TRenergyPowerAdapter tr) {
            src.sendFeedback(() -> Text.literal("  Active adapter: TREnergy — "
                            + tr.getStoredEnergy() + " / " + tr.getMaxCapacity()
                            + " E (" + adapter.getFuelLevelPercent() + "% fill)")
                    .formatted(Formatting.GREEN), false);
        } else {
            src.sendFeedback(() -> Text.literal("  Active adapter: Standalone (fuel rods) — "
                            + e.getFuelRods() + " / "
                            + SecondDawnRP.WARP_CORE_SERVICE.getConfig().getMaxFuelRods())
                    .formatted(Formatting.YELLOW), false);
        }
        return 1;
    }

    private static int executeFault(ServerCommandSource src, String typeName, String id) {
        if (id == null) { src.sendError(Text.literal("No core registered or specify an ID.")); return 0; }
        FaultType type;
        try { type = FaultType.valueOf(typeName.toUpperCase()); }
        catch (IllegalArgumentException e) { src.sendError(Text.literal("Unknown fault type: " + typeName)); return 0; }
        SecondDawnRP.WARP_CORE_SERVICE.injectFault(id, type, "Manual injection by " + src.getName());
        src.sendFeedback(() -> Text.literal("Fault injected: " + typeName + " → " + id)
                .formatted(Formatting.RED), true);
        return 1;
    }

    private static int executeUnregister(ServerCommandSource src, String id) {
        if (id == null) { src.sendError(Text.literal("No core registered or specify an ID.")); return 0; }
        if (SecondDawnRP.WARP_CORE_SERVICE.unregister(id)) {
            src.sendFeedback(() -> Text.literal("Warp core " + id + " unregistered.")
                    .formatted(Formatting.YELLOW), true);
        } else {
            src.sendError(Text.literal("Unknown core: " + id));
        }
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the entry ID when the argument is omitted.
     * Returns the single registered core's ID, or null if zero or multiple.
     */
    private static String resolveId(ServerCommandSource src) {
        var all = SecondDawnRP.WARP_CORE_SERVICE.getAll();
        if (all.size() == 1) return all.iterator().next().getEntryId();
        return null;
    }

    private static boolean isAdmin(ServerCommandSource src) {
        var p = src.getPlayer();
        if (p == null) return src.hasPermissionLevel(2);
        return p.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(p, "st.engineering.admin");
    }

    private static Formatting stateColour(WarpCoreEntry e) {
        return switch (e.getState()) {
            case ONLINE   -> Formatting.GREEN;
            case STARTING -> Formatting.AQUA;
            case UNSTABLE -> Formatting.YELLOW;
            case CRITICAL -> Formatting.RED;
            case FAILED   -> Formatting.DARK_RED;
            case OFFLINE  -> Formatting.GRAY;
        };
    }
}