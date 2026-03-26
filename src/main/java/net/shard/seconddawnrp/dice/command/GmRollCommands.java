package net.shard.seconddawnrp.dice.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.dice.data.GroupRollSession;
import net.shard.seconddawnrp.division.Division;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * GM commands for the dice system.
 *
 * <pre>
 *  /gm roll broadcast [player]
 *  /gm roll broadcastall
 *  /gm rolls public / private
 *  /gm roll group [players...]
 *
 *  /gm dc set [value]
 *  /gm dc set [value] [player]
 *  /gm dc clear
 *
 *  /gm scenario create [name] dc:[n] div:[div]
 *  /gm scenario call [player] [name...]    ← player FIRST, then greedy name
 *  /gm scenario list
 * </pre>
 */
public final class GmRollCommands {

    private GmRollCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("gm")
                .requires(src -> src.hasPermissionLevel(3))

                // ── /gm rolls public / private ────────────────────────────────
                .then(CommandManager.literal("rolls")
                        .then(CommandManager.literal("public")
                                .executes(ctx -> {
                                    SecondDawnRP.ROLL_SERVICE.setPublicMode(true);
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("[Dice] Rolls are now public — auto-broadcast.")
                                                    .formatted(Formatting.GREEN), true);
                                    return 1;
                                }))
                        .then(CommandManager.literal("private")
                                .executes(ctx -> {
                                    SecondDawnRP.ROLL_SERVICE.setPublicMode(false);
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("[Dice] Rolls are now private — hold-and-broadcast.")
                                                    .formatted(Formatting.YELLOW), true);
                                    return 1;
                                })))

                // ── /gm roll ... ──────────────────────────────────────────────
                .then(CommandManager.literal("roll")

                        .then(CommandManager.literal("broadcast")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                            boolean ok = SecondDawnRP.ROLL_SERVICE.gmBroadcast(target.getUuid());
                                            if (!ok) {
                                                ctx.getSource().sendError(Text.literal(
                                                        target.getName().getString() + " has no held roll."));
                                                return 0;
                                            }
                                            ctx.getSource().sendFeedback(() ->
                                                    Text.literal("[Dice] Broadcasted roll for "
                                                                    + target.getName().getString())
                                                            .formatted(Formatting.GREEN), false);
                                            return 1;
                                        })))

                        .then(CommandManager.literal("broadcastall")
                                .executes(ctx -> {
                                    var held = SecondDawnRP.ROLL_SERVICE.getAllHeld();
                                    if (held.isEmpty()) {
                                        ctx.getSource().sendError(Text.literal("No held rolls to broadcast."));
                                        return 0;
                                    }
                                    int count = held.size();
                                    held.keySet().forEach(uuid -> SecondDawnRP.ROLL_SERVICE.gmBroadcast(uuid));
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("[Dice] Broadcasted " + count + " held roll(s).")
                                                    .formatted(Formatting.GREEN), false);
                                    return 1;
                                }))

                        .then(CommandManager.literal("group")
                                .then(CommandManager.argument("players", EntityArgumentType.players())
                                        .executes(ctx -> {
                                            Collection<ServerPlayerEntity> targets =
                                                    EntityArgumentType.getPlayers(ctx, "players");
                                            ServerPlayerEntity gm = ctx.getSource().getPlayerOrThrow();
                                            List<ServerPlayerEntity> list = new ArrayList<>(targets);
                                            SecondDawnRP.ROLL_SERVICE.startGroupRoll(gm.getUuid(), list);
                                            ctx.getSource().sendFeedback(() ->
                                                    Text.literal("[Group Roll] Waiting for " + list.size()
                                                                    + " player(s) to /roll. 60 second timeout.")
                                                            .formatted(Formatting.GOLD), false);
                                            return 1;
                                        }))))

                // ── /gm dc ... ────────────────────────────────────────────────
                .then(CommandManager.literal("dc")
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("value", IntegerArgumentType.integer(1, 40))
                                        .executes(ctx -> {
                                            int dc = IntegerArgumentType.getInteger(ctx, "value");
                                            SecondDawnRP.ROLL_SERVICE.setDc(dc);
                                            ctx.getSource().sendFeedback(() ->
                                                    Text.literal("[Dice] Scene DC set to " + dc + ".")
                                                            .formatted(Formatting.AQUA), false);
                                            return 1;
                                        })
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .executes(ctx -> {
                                                    int dc = IntegerArgumentType.getInteger(ctx, "value");
                                                    ServerPlayerEntity target =
                                                            EntityArgumentType.getPlayer(ctx, "player");
                                                    SecondDawnRP.ROLL_SERVICE.setPlayerDc(target.getUuid(), dc);
                                                    ctx.getSource().sendFeedback(() ->
                                                            Text.literal("[Dice] DC " + dc + " set for "
                                                                            + target.getName().getString() + ".")
                                                                    .formatted(Formatting.AQUA), false);
                                                    return 1;
                                                }))))
                        .then(CommandManager.literal("clear")
                                .executes(ctx -> {
                                    SecondDawnRP.ROLL_SERVICE.clearDc();
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("[Dice] DC cleared.").formatted(Formatting.GRAY), false);
                                    return 1;
                                })))

                // ── /gm scenario ... ──────────────────────────────────────────
                .then(CommandManager.literal("scenario")

                        // /gm scenario create [name with spaces] dc:[n] div:[division]
                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("options", StringArgumentType.greedyString())
                                        .executes(ctx -> createScenario(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "options")))))

                        // /gm scenario call [player] [name with spaces]
                        // Player argument comes FIRST so the greedy name can be last
                        .then(CommandManager.literal("call")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    ServerPlayerEntity target =
                                                            EntityArgumentType.getPlayer(ctx, "player");
                                                    // Greedy name — use as-is, lookup is case-insensitive
                                                    String name = StringArgumentType.getString(ctx, "name").trim();
                                                    boolean ok = SecondDawnRP.ROLL_SERVICE.callScenario(name, target);
                                                    if (!ok) {
                                                        // Try to suggest close matches
                                                        String available = SecondDawnRP.ROLL_SERVICE.getScenarios()
                                                                .keySet().stream()
                                                                .reduce((a, b) -> a + ", " + b)
                                                                .orElse("(none)");
                                                        ctx.getSource().sendError(Text.literal(
                                                                "Unknown scenario: \"" + name + "\". "
                                                                        + "Available: " + available));
                                                        return 0;
                                                    }
                                                    ctx.getSource().sendFeedback(() ->
                                                            Text.literal("[Scenario] Called '" + name + "' for "
                                                                            + target.getName().getString())
                                                                    .formatted(Formatting.GREEN), false);
                                                    return 1;
                                                }))))

                        // /gm scenario list
                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    var scenarios = SecondDawnRP.ROLL_SERVICE.getScenarios();
                                    if (scenarios.isEmpty()) {
                                        ctx.getSource().sendFeedback(() ->
                                                Text.literal("[Scenario] No scenarios saved this session.")
                                                        .formatted(Formatting.GRAY), false);
                                        return 1;
                                    }
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("[Scenarios]").formatted(Formatting.GOLD), false);
                                    scenarios.values().forEach(s ->
                                            ctx.getSource().sendFeedback(() ->
                                                    Text.literal("  " + s.getName()
                                                                    + " — DC " + s.getDc()
                                                                    + (s.getDivision() != null
                                                                    ? " div:" + s.getDivision().name() : ""))
                                                            .formatted(Formatting.WHITE), false));
                                    return 1;
                                })))
        );
    }

    // ── Scenario creation parser ──────────────────────────────────────────────

    private static int createScenario(ServerCommandSource source, String opts) {
        // Format: "Name With Spaces dc:18 div:OPERATIONS bonus:2"
        // Split off dc:/div:/bonus: tokens, everything else is the name
        String[] parts = opts.split("\\s+");
        StringBuilder nameParts = new StringBuilder();
        int dc = 15;
        Division division = null;
        int divisionBonus = 2;

        for (String part : parts) {
            if (part.startsWith("dc:")) {
                try { dc = Integer.parseInt(part.substring(3)); } catch (NumberFormatException ignored) {}
            } else if (part.startsWith("div:")) {
                try { division = Division.valueOf(part.substring(4).toUpperCase()); }
                catch (IllegalArgumentException ignored) {}
            } else if (part.startsWith("bonus:")) {
                try { divisionBonus = Integer.parseInt(part.substring(6)); } catch (NumberFormatException ignored) {}
            } else {
                if (!nameParts.isEmpty()) nameParts.append(" ");
                nameParts.append(part);
            }
        }

        String name = nameParts.toString().trim();
        if (name.isBlank()) {
            source.sendError(Text.literal(
                    "Usage: /gm scenario create [Name] dc:[n] div:[DIVISION]"));
            return 0;
        }

        final int finalDc = dc;
        final Division finalDiv = division;
        SecondDawnRP.ROLL_SERVICE.createScenario(name, dc, division, divisionBonus);

        source.sendFeedback(() -> Text.literal("[Scenario] Created '" + name
                        + "' — DC " + finalDc
                        + (finalDiv != null ? " div:" + finalDiv.name() : "")
                        + " (session only)")
                .formatted(Formatting.GREEN), false);
        return 1;
    }
}