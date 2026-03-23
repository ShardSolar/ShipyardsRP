package net.shard.seconddawnrp.warpcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.warpcore.data.FaultType;
import net.shard.seconddawnrp.warpcore.data.WarpCoreEntry;

/**
 * Commands for the warp core system.
 *
 * <pre>
 * /warpcore status                     — show current reactor state, fuel, coil health
 * /warpcore startup                    — initiate startup sequence (requires engineering.reactor)
 * /warpcore shutdown                   — initiate shutdown (requires engineering.reactor)
 * /warpcore fuel add <amount>          — load fuel rods (requires engineering.reactor)
 * /warpcore fuel check                 — show current fuel level
 * /warpcore linkcoil <componentId>     — link a degradation component as the resonance coil (admin)
 * /warpcore reset                      — reset from FAILED to OFFLINE (admin)
 * /warpcore register                   — register the block you're standing on as the controller (admin)
 * /warpcore unregister                 — remove warp core registration (admin)
 * /gm warpcore fault <type>            — inject a fault (GM)
 * </pre>
 */
public final class WarpCoreCommands {

    private WarpCoreCommands() {}

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource> COMPONENT_SUGGESTIONS =
            (ctx, builder) -> {
                if (net.shard.seconddawnrp.SecondDawnRP.DEGRADATION_SERVICE == null)
                    return builder.buildFuture();
                net.shard.seconddawnrp.SecondDawnRP.DEGRADATION_SERVICE.getAllComponents()
                        .forEach(e -> builder.suggest(
                                e.getComponentId(),
                                net.minecraft.text.Text.literal(e.getDisplayName()
                                        + " (" + e.getHealth() + "/100)")));
                return builder.buildFuture();
            };

    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess,
            CommandManager.RegistrationEnvironment environment) {

        var warpcore = CommandManager.literal("warpcore");

        // /warpcore status — any player
        warpcore.then(CommandManager.literal("status")
                .executes(ctx -> executeStatus(ctx.getSource())));

        // /warpcore fuel check — any player
        warpcore.then(CommandManager.literal("fuel")
                .then(CommandManager.literal("check")
                        .executes(ctx -> executeFuelCheck(ctx.getSource())))
                .then(CommandManager.literal("add")
                        .requires(src -> hasReactorPerm(src))
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> executeFuelAdd(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "amount"))))));

        // /warpcore startup — reactor cert
        warpcore.then(CommandManager.literal("startup")
                .requires(src -> hasReactorPerm(src))
                .executes(ctx -> executeStartup(ctx.getSource())));

        // /warpcore shutdown — reactor cert
        warpcore.then(CommandManager.literal("shutdown")
                .requires(src -> hasReactorPerm(src))
                .executes(ctx -> executeShutdown(ctx.getSource())));

        // /warpcore reset — admin only
        warpcore.then(CommandManager.literal("reset")
                .requires(src -> isAdmin(src))
                .executes(ctx -> executeReset(ctx.getSource())));

        // /warpcore register — admin only
        warpcore.then(CommandManager.literal("register")
                .requires(src -> isAdmin(src))
                .executes(ctx -> executeRegister(ctx.getSource())));

        // /warpcore unregister — admin only
        warpcore.then(CommandManager.literal("unregister")
                .requires(src -> isAdmin(src))
                .executes(ctx -> executeUnregister(ctx.getSource())));

        // /warpcore linkcoil <id> — admin only
        warpcore.then(CommandManager.literal("linkcoil")
                .requires(src -> isAdmin(src))
                .then(CommandManager.argument("componentId", StringArgumentType.word())
                        .suggests(COMPONENT_SUGGESTIONS)
                        .executes(ctx -> executeLinkCoil(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "componentId")))));

        // /gm warpcore fault <type> — GM
        var gmWarpcore = CommandManager.literal("warpcore")
                .requires(src -> isGM(src));
        gmWarpcore.then(CommandManager.literal("fault")
                .then(CommandManager.literal("fuel")
                        .executes(ctx -> executeInjectFault(ctx.getSource(), FaultType.FUEL_DEPLETED)))
                .then(CommandManager.literal("coil")
                        .executes(ctx -> executeInjectFault(ctx.getSource(), FaultType.COIL_DEGRADED)))
                .then(CommandManager.literal("cascade")
                        .executes(ctx -> executeInjectFault(ctx.getSource(), FaultType.CASCADING_FAILURE)))
                .then(CommandManager.literal("gm")
                        .executes(ctx -> executeInjectFault(ctx.getSource(), FaultType.GM_INJECTED))));

        dispatcher.register(warpcore);
        dispatcher.register(CommandManager.literal("gm").then(gmWarpcore));
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static int executeStatus(ServerCommandSource source) {
        if (!SecondDawnRP.WARP_CORE_SERVICE.isRegistered()) {
            source.sendFeedback(() ->
                    Text.literal("No warp core registered.").formatted(Formatting.GRAY), false);
            return 1;
        }
        WarpCoreEntry e = SecondDawnRP.WARP_CORE_SERVICE.getEntry().get();
        var adapter = SecondDawnRP.WARP_CORE_SERVICE.getAdapter();

        source.sendFeedback(() ->
                Text.literal("── Warp Core Status ──").formatted(Formatting.GOLD), false);
        source.sendFeedback(() ->
                Text.literal("State: ").formatted(Formatting.GRAY)
                        .append(Text.literal(e.getState().name())
                                .formatted(stateColor(e.getState()))), false);
        source.sendFeedback(() ->
                Text.literal("Power output: ").formatted(Formatting.GRAY)
                        .append(Text.literal(e.getCurrentPowerOutput() + "%")
                                .formatted(Formatting.WHITE)), false);
        source.sendFeedback(() ->
                Text.literal("Fuel rods: ").formatted(Formatting.GRAY)
                        .append(Text.literal(e.getFuelRods() + " / "
                                        + SecondDawnRP.WARP_CORE_SERVICE.getConfig().getMaxFuelRods())
                                .formatted(Formatting.WHITE)), false);

        int coilId = e.getResonanceCoilComponentId() != null
                ? SecondDawnRP.DEGRADATION_SERVICE
                .getById(e.getResonanceCoilComponentId())
                .map(c -> c.getHealth()).orElse(-1) : -1;
        if (coilId >= 0) {
            final int health = coilId;
            source.sendFeedback(() ->
                    Text.literal("Resonance coil: ").formatted(Formatting.GRAY)
                            .append(Text.literal(health + "/100").formatted(Formatting.WHITE)), false);
        } else {
            source.sendFeedback(() ->
                    Text.literal("Resonance coil: ").formatted(Formatting.GRAY)
                            .append(Text.literal("not linked").formatted(Formatting.DARK_GRAY)), false);
        }
        return 1;
    }

    private static int executeFuelCheck(ServerCommandSource source) {
        if (!SecondDawnRP.WARP_CORE_SERVICE.isRegistered()) {
            source.sendError(Text.literal("No warp core registered."));
            return 0;
        }
        WarpCoreEntry e = SecondDawnRP.WARP_CORE_SERVICE.getEntry().get();
        int max = SecondDawnRP.WARP_CORE_SERVICE.getConfig().getMaxFuelRods();
        source.sendFeedback(() ->
                Text.literal("Fuel rods: " + e.getFuelRods() + " / " + max
                                + " (" + (e.getFuelRods() * 100 / Math.max(1, max)) + "%)")
                        .formatted(Formatting.GOLD), false);
        return 1;
    }

    private static int executeFuelAdd(ServerCommandSource source, int amount) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) { source.sendError(Text.literal("Must be a player.")); return 0; }
        if (!SecondDawnRP.WARP_CORE_SERVICE.isRegistered()) {
            source.sendError(Text.literal("No warp core registered.")); return 0;
        }
        int added = SecondDawnRP.WARP_CORE_SERVICE.loadFuel(amount);
        source.sendFeedback(() ->
                Text.literal("Loaded " + added + " fuel rod(s). Total: "
                                + SecondDawnRP.WARP_CORE_SERVICE.getEntry().get().getFuelRods())
                        .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeStartup(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) { source.sendError(Text.literal("Must be a player.")); return 0; }
        SecondDawnRP.WARP_CORE_SERVICE.initiateStartup(player);
        return 1;
    }

    private static int executeShutdown(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) { source.sendError(Text.literal("Must be a player.")); return 0; }
        SecondDawnRP.WARP_CORE_SERVICE.initiateShutdown(player);
        return 1;
    }

    private static int executeReset(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) { source.sendError(Text.literal("Must be a player.")); return 0; }
        SecondDawnRP.WARP_CORE_SERVICE.resetFromFailed(player);
        return 1;
    }

    private static int executeRegister(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) { source.sendError(Text.literal("Must be a player.")); return 0; }
        if (SecondDawnRP.WARP_CORE_SERVICE.isRegistered()) {
            source.sendError(Text.literal("A warp core is already registered. Unregister it first."));
            return 0;
        }
        String worldKey = player.getWorld().getRegistryKey().getValue().toString();
        long posLong = player.getBlockPos().down().asLong();
        SecondDawnRP.WARP_CORE_SERVICE.register(worldKey, posLong, player.getUuid());
        source.sendFeedback(() ->
                Text.literal("Warp core registered at your position.").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int executeUnregister(ServerCommandSource source) {
        if (!SecondDawnRP.WARP_CORE_SERVICE.isRegistered()) {
            source.sendError(Text.literal("No warp core registered."));
            return 0;
        }
        SecondDawnRP.WARP_CORE_SERVICE.unregister();
        source.sendFeedback(() ->
                Text.literal("Warp core unregistered.").formatted(Formatting.YELLOW), true);
        return 1;
    }

    private static int executeLinkCoil(ServerCommandSource source, String componentId) {
        var opt = SecondDawnRP.DEGRADATION_SERVICE.getById(componentId);
        if (opt.isEmpty()) {
            source.sendError(Text.literal("No component with id '" + componentId + "'."));
            return 0;
        }
        SecondDawnRP.WARP_CORE_SERVICE.linkResonanceCoil(componentId);
        source.sendFeedback(() ->
                Text.literal("Resonance coil linked to component '"
                        + opt.get().getDisplayName() + "'.").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int executeInjectFault(ServerCommandSource source, FaultType type) {
        if (!SecondDawnRP.WARP_CORE_SERVICE.isRegistered()) {
            source.sendError(Text.literal("No warp core registered.")); return 0;
        }
        SecondDawnRP.WARP_CORE_SERVICE.injectFault(type, "GM-injected fault.");
        source.sendFeedback(() ->
                Text.literal("Fault injected: " + type.getDisplayName()).formatted(Formatting.RED), true);
        return 1;
    }

    // ── Permission helpers ────────────────────────────────────────────────────

    private static boolean hasReactorPerm(ServerCommandSource src) {
        var player = src.getPlayer();
        if (player == null) return src.hasPermissionLevel(2);
        return player.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, "st.engineering.admin")
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, "engineering.reactor");
    }

    private static boolean isAdmin(ServerCommandSource src) {
        var player = src.getPlayer();
        if (player == null) return src.hasPermissionLevel(2);
        return player.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, "st.engineering.admin");
    }

    private static boolean isGM(ServerCommandSource src) {
        var player = src.getPlayer();
        if (player == null) return src.hasPermissionLevel(2);
        return player.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, "st.gm.use");
    }

    private static Formatting stateColor(net.shard.seconddawnrp.warpcore.data.ReactorState state) {
        return switch (state) {
            case ONLINE    -> Formatting.GREEN;
            case STARTING  -> Formatting.AQUA;
            case UNSTABLE  -> Formatting.YELLOW;
            case CRITICAL  -> Formatting.RED;
            case FAILED    -> Formatting.DARK_RED;
            case OFFLINE   -> Formatting.GRAY;
        };
    }
}