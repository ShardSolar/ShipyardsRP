package net.shard.seconddawnrp.character;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GM commands for character lifecycle.
 *
 * <pre>
 *  /gm character kill [player] [transferPercent]  — two-step death
 *  /gm character killconfirm
 *  /gm character set name    [player] [name]
 *  /gm character set bio     [player] [bio]
 *  /gm character set species [player] [speciesId]
 *  /gm character set permadeath [player] [true|false]
 *  /gm injury modify [player] [days]
 * </pre>
 */
public final class GmCharacterCommands {

    private static final Map<UUID, PendingKill> PENDING_KILLS = new HashMap<>();
    private static final long CONFIRMATION_WINDOW_MS = 30_000;

    private GmCharacterCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("gm")
                        .requires(src -> src.hasPermissionLevel(3))
                        .then(CommandManager.literal("character")
                                .then(CommandManager.literal("kill")
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .then(CommandManager.argument("transfer", FloatArgumentType.floatArg(0f, 100f))
                                                        .executes(ctx -> killStep1(ctx)))))
                                .then(CommandManager.literal("killconfirm")
                                        .executes(ctx -> killStep2(ctx)))
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.literal("name")
                                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                                                .executes(ctx -> setField(ctx, "name")))))
                                        .then(CommandManager.literal("bio")
                                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                                        .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                                                .executes(ctx -> setField(ctx, "bio")))))
                                        .then(CommandManager.literal("species")
                                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                                        .then(CommandManager.argument("value", StringArgumentType.word())
                                                                .executes(ctx -> setField(ctx, "species")))))
                                        .then(CommandManager.literal("permadeath")
                                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                                        .then(CommandManager.argument("value", StringArgumentType.word())
                                                                .executes(ctx -> setField(ctx, "permadeath")))))))
                        .then(CommandManager.literal("injury")
                                .then(CommandManager.literal("modify")
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .then(CommandManager.argument("days", IntegerArgumentType.integer(-365, 365))
                                                        .executes(ctx -> injuryModify(ctx))))))
        );
    }

    // ── Kill step 1 ───────────────────────────────────────────────────────────

    private static int killStep1(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity gm     = ctx.getSource().getPlayerOrThrow();
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            float transferPct         = FloatArgumentType.getFloat(ctx, "transfer");

            PENDING_KILLS.entrySet().removeIf(
                    e -> System.currentTimeMillis() - e.getValue().createdAtMs > CONFIRMATION_WINDOW_MS);
            PENDING_KILLS.put(gm.getUuid(), new PendingKill(target.getUuid(), transferPct / 100f));

            gm.sendMessage(Text.literal("[Character Death] About to kill ")
                    .formatted(Formatting.RED)
                    .append(Text.literal(target.getName().getString()).formatted(Formatting.YELLOW))
                    .append(Text.literal(" — " + (int) transferPct + "% points transferred. ")
                            .formatted(Formatting.RED))
                    .append(Text.literal("Run /gm character killconfirm within 30s.")
                            .formatted(Formatting.GOLD)), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── Kill step 2 ───────────────────────────────────────────────────────────

    private static int killStep2(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity gm = ctx.getSource().getPlayerOrThrow();
            PendingKill pending   = PENDING_KILLS.remove(gm.getUuid());

            if (pending == null || System.currentTimeMillis() - pending.createdAtMs > CONFIRMATION_WINDOW_MS) {
                gm.sendMessage(Text.literal("[Character] No pending kill or confirmation expired.")
                        .formatted(Formatting.RED), false);
                return 0;
            }

            ServerPlayerEntity target = ctx.getSource().getServer()
                    .getPlayerManager().getPlayer(pending.targetUuid);

            SecondDawnRP.PROFILE_SERVICE.executeCharacterDeath(
                    pending.targetUuid,
                    pending.transferPercent,
                    target,
                    SecondDawnRP.CHARACTER_ARCHIVE);

            PlayerProfile profile = SecondDawnRP.PROFILE_SERVICE.getLoaded(pending.targetUuid);
            String name = profile != null && profile.getCharacterName() != null
                    ? profile.getCharacterName() : pending.targetUuid.toString();

            gm.sendMessage(Text.literal("[Character] ")
                    .formatted(Formatting.GREEN)
                    .append(Text.literal(name).formatted(Formatting.YELLOW))
                    .append(Text.literal(" has died. New blank character created.")
                            .formatted(Formatting.GREEN)), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── Set field ─────────────────────────────────────────────────────────────

    private static int setField(CommandContext<ServerCommandSource> ctx, String field) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            String value = ctx.getArgument(field.equals("name") || field.equals("bio")
                    ? "name" : "value", String.class);

            PlayerProfile profile = SecondDawnRP.PROFILE_SERVICE.getLoaded(target.getUuid());
            if (profile == null) {
                ctx.getSource().sendError(Text.literal("Player not online or no profile loaded."));
                return 0;
            }

            switch (field) {
                case "name"       -> profile.setCharacterName(value);
                case "bio"        -> profile.setBio(value);
                case "species"    -> profile.setSpecies(value);
                case "permadeath" -> profile.setPermadeathConsent(Boolean.parseBoolean(value));
            }

            // Mark dirty so it saves on next cycle
            SecondDawnRP.PROFILE_MANAGER.markDirty(target.getUuid());

            ctx.getSource().sendFeedback(
                    () -> Text.literal("[Character] " + field + " updated for "
                            + target.getName().getString()).formatted(Formatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── Injury modify ─────────────────────────────────────────────────────────

    private static int injuryModify(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            int days = IntegerArgumentType.getInteger(ctx, "days");
            SecondDawnRP.LONG_TERM_INJURY_SERVICE.adjustExpiry(target.getUuid(), days);
            String sign = days >= 0 ? "+" : "";
            ctx.getSource().sendFeedback(
                    () -> Text.literal("[Injury] Expiry adjusted " + sign + days + " day(s) for "
                            + target.getName().getString()).formatted(Formatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private record PendingKill(UUID targetUuid, float transferPercent, long createdAtMs) {
        PendingKill(UUID targetUuid, float transferPercent) {
            this(targetUuid, transferPercent, System.currentTimeMillis());
        }
    }
}