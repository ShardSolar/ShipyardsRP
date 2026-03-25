package net.shard.seconddawnrp.degradation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;

import java.util.Collection;
import java.util.Comparator;

public final class EngineeringCommands {

    private EngineeringCommands() {}

    /** Suggests all currently registered component IDs. */
    private static final SuggestionProvider<ServerCommandSource> COMPONENT_ID_SUGGESTIONS =
            (ctx, builder) -> {
                if (SecondDawnRP.DEGRADATION_SERVICE == null) return builder.buildFuture();
                SecondDawnRP.DEGRADATION_SERVICE.getAllComponents()
                        .stream()
                        .sorted(Comparator.comparing(ComponentEntry::getHealth))
                        .forEach(e -> builder.suggest(
                                e.getComponentId(),
                                Text.literal(e.getDisplayName()
                                        + " — " + e.getStatus().name()
                                        + " (" + e.getHealth() + "/100)")));
                return builder.buildFuture();
            };

    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess,
            CommandManager.RegistrationEnvironment environment) {

        var engineering = CommandManager.literal("engineering").requires(src ->
                src.hasPermissionLevel(2)
                        || SecondDawnRP.PERMISSION_SERVICE.hasPermission(
                        src.getPlayer(), "st.engineering.admin"));

        // /engineering register [displayName]
        engineering.then(CommandManager.literal("register")
                .then(CommandManager.argument("displayName", StringArgumentType.greedyString())
                        .executes(ctx -> executeRegister(ctx.getSource(),
                                StringArgumentType.getString(ctx, "displayName"))))
                .executes(ctx -> executeRegister(ctx.getSource(), null)));

        // /engineering remove <id>
        engineering.then(CommandManager.literal("remove")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(COMPONENT_ID_SUGGESTIONS)
                        .executes(ctx -> executeRemove(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"))))
                .executes(ctx -> executeRemoveAtFeet(ctx.getSource())));

        // /engineering status
        engineering.then(CommandManager.literal("status")
                .executes(ctx -> executeStatus(ctx.getSource())));

        // /engineering list
        engineering.then(CommandManager.literal("list")
                .executes(ctx -> executeList(ctx.getSource())));

        // /engineering sethealth <id> <value>
        engineering.then(CommandManager.literal("sethealth")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(COMPONENT_ID_SUGGESTIONS)
                        .then(CommandManager.argument("value",
                                        IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> executeSetHealth(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "value"))))));

        // /engineering setrepair <id> <item_id> [count]
        engineering.then(CommandManager.literal("setrepair")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(COMPONENT_ID_SUGGESTIONS)
                        .then(CommandManager.argument("item", StringArgumentType.word())
                                .then(CommandManager.argument("count",
                                                IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> executeSetRepair(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "item"),
                                                IntegerArgumentType.getInteger(ctx, "count"))))
                                .executes(ctx -> executeSetRepair(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        StringArgumentType.getString(ctx, "item"),
                                        1)))));

        // /engineering clearrepair <id>
        engineering.then(CommandManager.literal("clearrepair")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(COMPONENT_ID_SUGGESTIONS)
                        .executes(ctx -> executeClearRepair(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        // /engineering save
        engineering.then(CommandManager.literal("save")
                .executes(ctx -> executeSave(ctx.getSource())));

        // /engineering locate <id> — sends beacon particles to guide player
        engineering.then(CommandManager.literal("locate")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(COMPONENT_ID_SUGGESTIONS)
                        .executes(ctx -> executeLocate(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        dispatcher.register(engineering);
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static int executeRegister(ServerCommandSource source, String displayNameArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Must be run by a player."));
            return 0;
        }

        BlockPos pos = player.getBlockPos().down();
        String worldKey = player.getWorld().getRegistryKey().getValue().toString();
        String blockTypeId = player.getWorld()
                .getBlockState(pos).getBlock().getTranslationKey();
        String displayName = displayNameArg != null
                ? displayNameArg
                : blockTypeId.replace("block.minecraft.", "").replace("_", " ");

        try {
            ComponentEntry entry = SecondDawnRP.DEGRADATION_SERVICE.register(
                    worldKey, pos.asLong(), blockTypeId, displayName,
                    player.getUuid());
            source.sendFeedback(() -> Text.literal("Registered component '")
                    .formatted(Formatting.GREEN)
                    .append(Text.literal(entry.getDisplayName()).formatted(Formatting.WHITE))
                    .append(Text.literal("' (id: " + entry.getComponentId() + ")")
                            .formatted(Formatting.GRAY)), true);
        } catch (IllegalStateException e) {
            source.sendError(Text.literal(e.getMessage()));
            return 0;
        }
        return 1;
    }

    /** Remove by explicit component ID — supports tab completion. */
    private static int executeRemove(ServerCommandSource source, String componentId) {
        var opt = SecondDawnRP.DEGRADATION_SERVICE.getById(componentId);
        if (opt.isEmpty()) {
            source.sendError(Text.literal("No component with id '" + componentId + "'."));
            return 0;
        }
        String name = opt.get().getDisplayName();
        SecondDawnRP.DEGRADATION_SERVICE.unregister(
                opt.get().getWorldKey(), opt.get().getBlockPosLong());
        source.sendFeedback(() ->
                Text.literal("Removed component '").formatted(Formatting.YELLOW)
                        .append(Text.literal(name).formatted(Formatting.WHITE))
                        .append(Text.literal("'.").formatted(Formatting.YELLOW)), true);
        return 1;
    }

    /** Remove by standing on the block — fallback when no ID is provided. */
    private static int executeRemoveAtFeet(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Must be run by a player, or provide a component ID."));
            return 0;
        }

        BlockPos pos = player.getBlockPos().down();
        String worldKey = player.getWorld().getRegistryKey().getValue().toString();

        var opt = SecondDawnRP.DEGRADATION_SERVICE.getByPosition(worldKey, pos.asLong());
        if (opt.isEmpty()) {
            source.sendError(Text.literal(
                    "No component at your position. Use /engineering remove <id> to remove by ID."));
            return 0;
        }
        String name = opt.get().getDisplayName();
        SecondDawnRP.DEGRADATION_SERVICE.unregister(worldKey, pos.asLong());
        source.sendFeedback(() ->
                Text.literal("Removed component '").formatted(Formatting.YELLOW)
                        .append(Text.literal(name).formatted(Formatting.WHITE))
                        .append(Text.literal("'.").formatted(Formatting.YELLOW)), true);
        return 1;
    }

    private static int executeStatus(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Must be run by a player."));
            return 0;
        }

        BlockPos pos = player.getBlockPos().down();
        String worldKey = player.getWorld().getRegistryKey().getValue().toString();

        var opt = SecondDawnRP.DEGRADATION_SERVICE.getByPosition(worldKey, pos.asLong());
        if (opt.isEmpty()) {
            source.sendError(Text.literal("No component registered at your position."));
            return 0;
        }

        ComponentEntry e = opt.get();
        Formatting color = statusColor(e.getStatus());
        source.sendFeedback(() ->
                Text.literal("[" + e.getDisplayName() + "] ")
                        .formatted(Formatting.AQUA)
                        .append(Text.literal(e.getStatus().name() + " ")
                                .formatted(color))
                        .append(Text.literal("(" + e.getHealth() + "/100)")
                                .formatted(Formatting.GRAY)), false);
        return 1;
    }

    private static int executeList(ServerCommandSource source) {
        Collection<ComponentEntry> all = SecondDawnRP.DEGRADATION_SERVICE.getAllComponents();
        if (all.isEmpty()) {
            source.sendFeedback(() ->
                    Text.literal("No components registered.").formatted(Formatting.GRAY), false);
            return 1;
        }

        source.sendFeedback(() ->
                Text.literal("── Registered Components (" + all.size() + ") ──")
                        .formatted(Formatting.AQUA), false);

        all.stream()
                .sorted(Comparator.comparing(ComponentEntry::getHealth))
                .forEach(e -> source.sendFeedback(() ->
                        Text.literal("  " + e.getDisplayName() + " — ")
                                .formatted(Formatting.GRAY)
                                .append(Text.literal(e.getStatus().name()
                                                + " (" + e.getHealth() + "/100)")
                                        .formatted(statusColor(e.getStatus()))), false));
        return 1;
    }

    private static int executeSetHealth(
            ServerCommandSource source, String componentId, int value) {
        var opt = SecondDawnRP.DEGRADATION_SERVICE.getById(componentId);
        if (opt.isEmpty()) {
            source.sendError(Text.literal("No component with id '" + componentId + "'."));
            return 0;
        }
        ComponentEntry entry = opt.get();
        entry.setHealth(value);
        SecondDawnRP.DEGRADATION_SERVICE.forceSave(entry);
        source.sendFeedback(() ->
                Text.literal("Set health of '" + entry.getDisplayName()
                                + "' to " + value + " → " + entry.getStatus().name())
                        .formatted(Formatting.YELLOW), true);
        return 1;
    }

    private static int executeSetRepair(
            ServerCommandSource source, String componentId, String itemId, int count) {
        var opt = SecondDawnRP.DEGRADATION_SERVICE.getById(componentId);
        if (opt.isEmpty()) {
            source.sendError(Text.literal("No component with id '" + componentId + "'."));
            return 0;
        }
        ComponentEntry entry = opt.get();
        entry.setRepairItemId(itemId.toLowerCase());
        entry.setRepairItemCount(count);
        SecondDawnRP.DEGRADATION_SERVICE.forceSave(entry);
        source.sendFeedback(() ->
                Text.literal("Set repair item for '" + entry.getDisplayName()
                        + "' to " + count + "x " + itemId).formatted(Formatting.YELLOW), true);
        return 1;
    }

    private static int executeClearRepair(ServerCommandSource source, String componentId) {
        var opt = SecondDawnRP.DEGRADATION_SERVICE.getById(componentId);
        if (opt.isEmpty()) {
            source.sendError(Text.literal("No component with id '" + componentId + "'."));
            return 0;
        }
        ComponentEntry entry = opt.get();
        entry.setRepairItemId(null);
        entry.setRepairItemCount(0);
        SecondDawnRP.DEGRADATION_SERVICE.forceSave(entry);
        String def = SecondDawnRP.DEGRADATION_SERVICE.getConfig().getDefaultRepairItemCount()
                + "x " + SecondDawnRP.DEGRADATION_SERVICE.getConfig().getDefaultRepairItemId();
        source.sendFeedback(() ->
                Text.literal("Cleared repair item for '" + entry.getDisplayName()
                                + "' — now using global default (" + def + ")")
                        .formatted(Formatting.YELLOW), true);
        return 1;
    }

    private static int executeSave(ServerCommandSource source) {
        SecondDawnRP.DEGRADATION_SERVICE.saveAll();
        source.sendFeedback(() ->
                Text.literal("Degradation state saved.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeLocate(ServerCommandSource source, String componentId) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) { source.sendError(Text.literal("Must be a player.")); return 0; }

        var opt = SecondDawnRP.DEGRADATION_SERVICE.getById(componentId);
        if (opt.isEmpty()) {
            source.sendError(Text.literal("Unknown component: " + componentId));
            return 0;
        }
        var entry = opt.get();
        net.minecraft.util.math.BlockPos target =
                net.minecraft.util.math.BlockPos.fromLong(entry.getBlockPosLong());

        source.sendFeedback(() -> Text.literal("Component '" + entry.getDisplayName()
                + "' is at " + target.getX() + ", " + target.getY() + ", " + target.getZ()
                + " in " + entry.getWorldKey()).formatted(Formatting.GOLD), false);

        // Send packet directly to the player — client spawns particles regardless of distance
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new net.shard.seconddawnrp.degradation.network.LocateComponentS2CPacket(
                        entry.getDisplayName(),
                        target.getX() + 0.5, target.getY(), target.getZ() + 0.5));
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Formatting statusColor(ComponentStatus status) {
        return switch (status) {
            case NOMINAL  -> Formatting.GREEN;
            case DEGRADED -> Formatting.YELLOW;
            case CRITICAL -> Formatting.RED;
            case OFFLINE  -> Formatting.DARK_RED;
        };
    }
}