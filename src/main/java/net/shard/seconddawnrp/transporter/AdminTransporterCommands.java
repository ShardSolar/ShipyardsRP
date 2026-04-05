package net.shard.seconddawnrp.transporter;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Admin transporter management commands.
 *
 * /admin transporter addlocation <name> <x> <y> <z>   — register a named ship destination
 * /admin transporter removelocation <name>              — remove a named ship destination
 * /admin transporter listlocations                      — list all registered ship destinations
 * /admin transporter register <x> <y> <z>              — register a controller block at position
 */
public class AdminTransporterCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                TransporterService transporterService) {

        dispatcher.register(CommandManager.literal("admin")
                .requires(src -> src.hasPermissionLevel(3))
                .then(CommandManager.literal("transporter")

                        // addlocation <name> <x> <y> <z>
                        .then(CommandManager.literal("addlocation")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .then(CommandManager.argument("x", DoubleArgumentType.doubleArg())
                                                .then(CommandManager.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(CommandManager.argument("z", DoubleArgumentType.doubleArg())
                                                                .executes(ctx -> {
                                                                    String name = StringArgumentType.getString(ctx, "name");
                                                                    double x = DoubleArgumentType.getDouble(ctx, "x");
                                                                    double y = DoubleArgumentType.getDouble(ctx, "y");
                                                                    double z = DoubleArgumentType.getDouble(ctx, "z");
                                                                    ServerPlayerEntity player = ctx.getSource().getPlayer();

                                                                    String worldKey = player != null
                                                                            ? player.getWorld().getRegistryKey()
                                                                            .getValue().toString()
                                                                            : "minecraft:overworld";
                                                                    String registeredBy = player != null
                                                                            ? player.getUuid().toString() : "server";

                                                                    transporterService.addShipLocation(
                                                                            name, x, y, z, worldKey, registeredBy);

                                                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                                                    "[Transporter] Ship location registered: §e" + name
                                                                                            + " §7at " + String.format("%.1f %.1f %.1f", x, y, z)
                                                                                            + " in " + worldKey)
                                                                            .formatted(Formatting.GREEN), true);
                                                                    return 1;
                                                                }))))))

                        // removelocation <name>
                        .then(CommandManager.literal("removelocation")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            transporterService.getShipLocations().keySet()
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            if (!transporterService.getShipLocations().containsKey(name)) {
                                                ctx.getSource().sendFeedback(() -> Text.literal(
                                                                "[Transporter] No location named: " + name)
                                                        .formatted(Formatting.RED), false);
                                                return 0;
                                            }
                                            transporterService.removeShipLocation(name);
                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                            "[Transporter] Removed ship location: " + name)
                                                    .formatted(Formatting.YELLOW), true);
                                            return 1;
                                        })))

                        // listlocations
                        .then(CommandManager.literal("listlocations")
                                .executes(ctx -> {
                                    var locations = transporterService.getShipLocations();
                                    if (locations.isEmpty()) {
                                        ctx.getSource().sendFeedback(() -> Text.literal(
                                                        "[Transporter] No ship locations registered.")
                                                .formatted(Formatting.YELLOW), false);
                                        return 0;
                                    }
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                    "─── Ship Locations ────────────────────")
                                            .formatted(Formatting.DARK_AQUA), false);
                                    for (var loc : locations.values()) {
                                        ctx.getSource().sendFeedback(() -> Text.literal(
                                                        String.format("  §e%-20s §7%.1f %.1f %.1f  %s",
                                                                loc.name(), loc.x(), loc.y(), loc.z(), loc.worldKey())),
                                                false);
                                    }
                                    return locations.size();
                                }))

                        // register <x> <y> <z>  — registers the controller at given position
                        // In practice GMs right-click the block to register it;
                        // this command is a fallback for console/admin use
                        .then(CommandManager.literal("register")
                                .then(CommandManager.argument("x", DoubleArgumentType.doubleArg())
                                        .then(CommandManager.argument("y", DoubleArgumentType.doubleArg())
                                                .then(CommandManager.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(ctx -> {
                                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                                            "[Transporter] To register a controller, right-click "
                                                                                    + "the Transporter Controller block with a GM tool, "
                                                                                    + "or use /admin transporter addlocation to add destinations.")
                                                                    .formatted(Formatting.YELLOW), false);
                                                            return 0;
                                                        })))))
                ));
    }
}