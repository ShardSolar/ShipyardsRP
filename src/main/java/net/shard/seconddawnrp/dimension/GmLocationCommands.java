package net.shard.seconddawnrp.dimension;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * /gm location — dimension management commands
 * /gm tp       — GM cross-dimension teleport
 */
public class GmLocationCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                LocationService locationService) {

        dispatcher.register(CommandManager.literal("gm")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("location")

                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    List<LocationDefinition> all = locationService.getAllDimensions();
                                    if (all.isEmpty()) {
                                        ctx.getSource().sendFeedback(() -> Text.literal(
                                                        "[Dimensions] No dimensions registered.")
                                                .formatted(Formatting.YELLOW), false);
                                        return 0;
                                    }
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                    "─── Registered Dimensions ──────────────────")
                                            .formatted(Formatting.DARK_AQUA), false);
                                    for (LocationDefinition def : all) {
                                        boolean active = locationService.isActive(def.dimensionId());
                                        double[] entry = locationService.getEntryPoint(def.dimensionId());
                                        String status = active ? "§aACTIVE" : "§cINACTIVE";
                                        ctx.getSource().sendFeedback(() -> Text.literal(
                                                String.format("  %s §7%s  %s  §8entry: %.1f %.1f %.1f",
                                                        status, def.dimensionId(), def.displayName(),
                                                        entry[0], entry[1], entry[2])), false);
                                        if (!def.description().isBlank()) {
                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                    "    §7" + def.description()), false);
                                        }
                                    }
                                    return all.size();
                                }))

                        .then(CommandManager.literal("activate")
                                .then(CommandManager.argument("dimensionId", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            locationService.getAllDimensions().stream()
                                                    .map(LocationDefinition::dimensionId)
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "dimensionId");
                                            if (locationService.activate(id)) {
                                                ctx.getSource().sendFeedback(() -> Text.literal(
                                                                "[Dimensions] Activated: " + id)
                                                        .formatted(Formatting.GREEN), true);
                                                return 1;
                                            }
                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                            "[Dimensions] Unknown dimension: " + id)
                                                    .formatted(Formatting.RED), false);
                                            return 0;
                                        })))

                        .then(CommandManager.literal("deactivate")
                                .then(CommandManager.argument("dimensionId", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            locationService.getAllDimensions().stream()
                                                    .map(LocationDefinition::dimensionId)
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "dimensionId");
                                            if (locationService.deactivate(id)) {
                                                ctx.getSource().sendFeedback(() -> Text.literal(
                                                                "[Dimensions] Deactivated: " + id
                                                                        + " — players already inside are not moved.")
                                                        .formatted(Formatting.YELLOW), true);
                                                return 1;
                                            }
                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                            "[Dimensions] Unknown dimension: " + id)
                                                    .formatted(Formatting.RED), false);
                                            return 0;
                                        })))

                        .then(CommandManager.literal("setentry")
                                .then(CommandManager.argument("dimensionId", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            locationService.getAllDimensions().stream()
                                                    .map(LocationDefinition::dimensionId)
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .then(CommandManager.argument("x", DoubleArgumentType.doubleArg())
                                                .then(CommandManager.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(CommandManager.argument("z", DoubleArgumentType.doubleArg())
                                                                .executes(ctx -> {
                                                                    String id = StringArgumentType.getString(ctx, "dimensionId");
                                                                    double x = DoubleArgumentType.getDouble(ctx, "x");
                                                                    double y = DoubleArgumentType.getDouble(ctx, "y");
                                                                    double z = DoubleArgumentType.getDouble(ctx, "z");
                                                                    if (locationService.get(id).isEmpty()) {
                                                                        ctx.getSource().sendFeedback(() -> Text.literal(
                                                                                        "[Dimensions] Unknown dimension: " + id)
                                                                                .formatted(Formatting.RED), false);
                                                                        return 0;
                                                                    }
                                                                    locationService.setEntryPoint(id, x, y, z);
                                                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                                                    String.format("[Dimensions] Entry for %s set to %.1f %.1f %.1f (session only)",
                                                                                            id, x, y, z))
                                                                            .formatted(Formatting.GREEN), true);
                                                                    return 1;
                                                                }))))))

                        .then(CommandManager.literal("attach")
                                .then(CommandManager.argument("dimensionId", StringArgumentType.word())
                                        .then(CommandManager.argument("tx", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("tz", IntegerArgumentType.integer())
                                                        .then(CommandManager.argument("radius", IntegerArgumentType.integer(1))
                                                                .executes(ctx -> {
                                                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                                                    "[Dimensions] /gm location attach is a Phase 12 stub.")
                                                                            .formatted(Formatting.YELLOW), false);
                                                                    return 0;
                                                                }))))))
                )

                // /gm tp <player> <dimension> <x> <y> <z>
                .then(CommandManager.literal("tp")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("dimension", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            locationService.getAllDimensions().stream()
                                                    .map(LocationDefinition::dimensionId)
                                                    .forEach(builder::suggest);
                                            builder.suggest("overworld");
                                            builder.suggest("the_nether");
                                            builder.suggest("the_end");
                                            return builder.buildFuture();
                                        })
                                        .then(CommandManager.argument("x", DoubleArgumentType.doubleArg())
                                                .then(CommandManager.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(CommandManager.argument("z", DoubleArgumentType.doubleArg())
                                                                .executes(ctx -> {
                                                                    ServerPlayerEntity target =
                                                                            EntityArgumentType.getPlayer(ctx, "player");
                                                                    String dimension = StringArgumentType.getString(ctx, "dimension");
                                                                    double x = DoubleArgumentType.getDouble(ctx, "x");
                                                                    double y = DoubleArgumentType.getDouble(ctx, "y");
                                                                    double z = DoubleArgumentType.getDouble(ctx, "z");

                                                                    boolean success = locationService.teleportPlayer(
                                                                            target, dimension, x, y, z);
                                                                    if (!success) {
                                                                        success = teleportVanilla(target, dimension, x, y, z);
                                                                    }

                                                                    if (success) {
                                                                        final String dim = dimension;
                                                                        ctx.getSource().sendFeedback(() -> Text.literal(
                                                                                        "[TP] Teleported " + target.getName().getString()
                                                                                                + " to " + dim + " at "
                                                                                                + String.format("%.1f %.1f %.1f", x, y, z))
                                                                                .formatted(Formatting.GREEN), true);
                                                                        return 1;
                                                                    }
                                                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                                                    "[TP] Unknown dimension: " + dimension)
                                                                            .formatted(Formatting.RED), false);
                                                                    return 0;
                                                                }))))))));
    }

    private static boolean teleportVanilla(ServerPlayerEntity player,
                                           String dimensionName,
                                           double x, double y, double z) {
        if (player.getServer() == null) return false;
        var key = switch (dimensionName) {
            case "overworld"  -> net.minecraft.world.World.OVERWORLD;
            case "the_nether" -> net.minecraft.world.World.NETHER;
            case "the_end"    -> net.minecraft.world.World.END;
            default           -> null;
        };
        if (key == null) return false;
        var world = player.getServer().getWorld(key);
        if (world == null) return false;
        player.teleport(world, x, y, z, player.getYaw(), player.getPitch());
        return true;
    }
}