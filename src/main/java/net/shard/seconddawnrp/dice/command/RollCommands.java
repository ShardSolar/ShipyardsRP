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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Player-facing dice and RP commands.
 *
 * <pre>
 *  /roll                              — roll 1d20 with all modifiers
 *  /rp [action]                       — narrate a roleplay action
 *  /rp record start                   — begin recording (owner only)
 *  /rp record start radius:[n]        — record all players within n blocks
 *  /rp record start players:[n1],[n2] — record specific players
 *  /rp record stop                    — end recording, finalize PADD
 * </pre>
 */
public final class RollCommands {

    private RollCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        // /roll
        dispatcher.register(CommandManager.literal("roll")
                .requires(src -> src.isExecutedByPlayer())
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    SecondDawnRP.ROLL_SERVICE.roll(player, 0);
                    return 1;
                })
        );

        // /rp [action]
        dispatcher.register(CommandManager.literal("rp")
                .requires(src -> src.isExecutedByPlayer())

                // /rp record ...
                .then(CommandManager.literal("record")
                        .then(CommandManager.literal("start")
                                .executes(ctx -> startRecording(ctx.getSource(), 0, Set.of()))
                                .then(CommandManager.argument("options", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String opts = StringArgumentType.getString(ctx, "options");
                                            return startRecordingWithOptions(ctx.getSource(), opts);
                                        })))
                        .then(CommandManager.literal("stop")
                                .executes(ctx -> stopRecording(ctx.getSource()))))

                // /rp [action] — must come after sub-literals
                .then(CommandManager.argument("action", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            String action = StringArgumentType.getString(ctx, "action");
                            SecondDawnRP.ROLL_SERVICE.rp(player, action);
                            return 1;
                        }))
        );
    }

    // ── /rp record start ─────────────────────────────────────────────────────

    private static int startRecording(ServerCommandSource source,
                                      int radius, Set<UUID> namedPlayers) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();

            if (SecondDawnRP.RP_PADD_SERVICE.hasActiveSession(player.getUuid())) {
                source.sendError(Text.literal(
                        "You already have an active recording. Use /rp record stop first."));
                return 0;
            }

            SecondDawnRP.RP_PADD_SERVICE.startSession(player.getUuid(), radius, namedPlayers);

            String modeStr = radius > 0 ? " (radius: " + radius + " blocks)"
                    : namedPlayers.isEmpty() ? " (personal)"
                    : " (" + namedPlayers.size() + " named players)";
            source.sendFeedback(() -> Text.literal("[RP PADD] Recording started" + modeStr + ".")
                    .formatted(Formatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int startRecordingWithOptions(ServerCommandSource source, String opts) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            int radius = 0;
            Set<UUID> namedPlayers = new HashSet<>();

            // Parse "radius:N players:name1,name2"
            for (String part : opts.split("\\s+")) {
                if (part.startsWith("radius:")) {
                    try { radius = Integer.parseInt(part.substring(7)); }
                    catch (NumberFormatException e) { /* ignore bad value */ }
                } else if (part.startsWith("players:")) {
                    String[] names = part.substring(8).split(",");
                    for (String name : names) {
                        ServerPlayerEntity target = source.getServer()
                                .getPlayerManager().getPlayer(name.trim());
                        if (target != null) namedPlayers.add(target.getUuid());
                    }
                }
            }

            return startRecording(source, radius, namedPlayers);
        } catch (Exception e) {
            source.sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── /rp record stop ───────────────────────────────────────────────────────

    private static int stopRecording(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();

            if (!SecondDawnRP.RP_PADD_SERVICE.hasActiveSession(player.getUuid())) {
                source.sendError(Text.literal("No active recording to stop."));
                return 0;
            }

            var log = SecondDawnRP.RP_PADD_SERVICE.stopSession(player.getUuid());

            // Write log to the RP PADD item in the player's inventory
            boolean written = net.shard.seconddawnrp.dice.item.RpPaddItem
                    .writeSessionLog(player, log);

            if (written) {
                source.sendFeedback(() -> Text.literal(
                                "[RP PADD] Recording stopped. " + log.size()
                                        + " entries saved. Sign the PADD and submit it.")
                        .formatted(Formatting.GREEN), false);
            } else {
                source.sendFeedback(() -> Text.literal(
                                "[RP PADD] Recording stopped (" + log.size()
                                        + " entries). No RP PADD found in hotbar — log was discarded.")
                        .formatted(Formatting.YELLOW), false);
            }

            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
}