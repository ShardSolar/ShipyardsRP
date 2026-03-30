package net.shard.seconddawnrp.medical;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;

/**
 * /gurney commands.
 *
 * /gurney release           — carrier releases their current patient
 * /gurney release <player>  — GM force-releases a specific carrier
 * /gurney down <player>     — GM manually downs a player (for testing/events)
 * /gurney revive <player>   — GM force-revives a downed player (bypass treatment)
 */
public class GurneyCommands {

    private GurneyCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("gurney")

                        // /gurney release — carrier releases themselves
                        .then(CommandManager.literal("release")
                                // /gurney release (self)
                                .executes(ctx -> {
                                    try {
                                        ServerPlayerEntity player =
                                                ctx.getSource().getPlayerOrThrow();
                                        SecondDawnRP.GURNEY_SERVICE.detachByCarrier(player);
                                        return 1;
                                    } catch (Exception e) {
                                        ctx.getSource().sendError(
                                                Text.literal("Error: " + e.getMessage()));
                                        return 0;
                                    }
                                })
                                // /gurney release <player> — GM releases specific carrier
                                .then(CommandManager.argument("carrier",
                                                EntityArgumentType.player())
                                        .requires(src -> src.hasPermissionLevel(3))
                                        .executes(ctx -> {
                                            try {
                                                ServerPlayerEntity carrier =
                                                        EntityArgumentType.getPlayer(ctx, "carrier");
                                                SecondDawnRP.GURNEY_SERVICE.detachByCarrier(carrier);
                                                ctx.getSource().sendFeedback(
                                                        () -> Text.literal("[Gurney] Released patient from "
                                                                        + carrier.getName().getString() + ".")
                                                                .formatted(Formatting.GREEN), true);
                                                return 1;
                                            } catch (Exception e) {
                                                ctx.getSource().sendError(
                                                        Text.literal("Error: " + e.getMessage()));
                                                return 0;
                                            }
                                        })))

                        // /gurney down <player> — GM downs a player manually
                        .then(CommandManager.literal("down")
                                .requires(src -> src.hasPermissionLevel(3))
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            try {
                                                ServerPlayerEntity target =
                                                        EntityArgumentType.getPlayer(ctx, "player");
                                                SecondDawnRP.DOWNED_SERVICE.downPlayer(target, true);
                                                ctx.getSource().sendFeedback(
                                                        () -> Text.literal("[Gurney] "
                                                                        + target.getName().getString()
                                                                        + " has been downed.")
                                                                .formatted(Formatting.GREEN), true);
                                                return 1;
                                            } catch (Exception e) {
                                                ctx.getSource().sendError(
                                                        Text.literal("Error: " + e.getMessage()));
                                                return 0;
                                            }
                                        })))

                        // /gurney revive <player> — GM force-revives
                        .then(CommandManager.literal("revive")
                                .requires(src -> src.hasPermissionLevel(3))
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            try {
                                                ServerPlayerEntity target =
                                                        EntityArgumentType.getPlayer(ctx, "player");
                                                SecondDawnRP.DOWNED_SERVICE.revivePlayer(target);
                                                ctx.getSource().sendFeedback(
                                                        () -> Text.literal("[Gurney] "
                                                                        + target.getName().getString()
                                                                        + " has been revived.")
                                                                .formatted(Formatting.GREEN), true);
                                                return 1;
                                            } catch (Exception e) {
                                                ctx.getSource().sendError(
                                                        Text.literal("Error: " + e.getMessage()));
                                                return 0;
                                            }
                                        })))
        );
    }
}