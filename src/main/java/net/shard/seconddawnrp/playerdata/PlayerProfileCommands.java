package net.shard.seconddawnrp.playerdata;

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
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.division.Rank;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class PlayerProfileCommands {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("profile")
                .requires(source -> true)

                .executes(PlayerProfileCommands::showSelf)

                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(PlayerProfileCommands::showOther))

                .then(CommandManager.literal("setdivision")
                        .then(CommandManager.argument("division", StringArgumentType.word())
                                .executes(ctx -> {
                                    ensureReady();
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_SERVICE.getLoaded(player.getUuid());
                                    if (!SecondDawnRP.PERMISSION_SERVICE.canSetDivision(player, actorProfile)) {
                                        ctx.getSource().sendError(Text.literal("No permission."));
                                        return 0;
                                    }

                                    Division division = Division.valueOf(
                                            StringArgumentType.getString(ctx, "division").toUpperCase());
                                    SecondDawnRP.PROFILE_SERVICE.setDivision(player, division);
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("Division set to " + division), false);
                                    return 1;
                                })))

                .then(CommandManager.literal("setrank")
                        .then(CommandManager.argument("rank", StringArgumentType.word())
                                .executes(ctx -> {
                                    ensureReady();
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_SERVICE.getLoaded(player.getUuid());
                                    if (!SecondDawnRP.PERMISSION_SERVICE.canSetRank(player, actorProfile)) {
                                        ctx.getSource().sendError(Text.literal("No permission."));
                                        return 0;
                                    }

                                    Rank rank = Rank.valueOf(
                                            StringArgumentType.getString(ctx, "rank").toUpperCase());
                                    SecondDawnRP.PROFILE_SERVICE.setRank(player, rank);
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("Rank set to " + rank), false);
                                    return 1;
                                })))

                .then(CommandManager.literal("addbillet")
                        .then(CommandManager.argument("billet", StringArgumentType.word())
                                .executes(ctx -> {
                                    ensureReady();
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_SERVICE.getLoaded(player.getUuid());
                                    if (!SecondDawnRP.PERMISSION_SERVICE.canAssignBillet(player, actorProfile)) {
                                        ctx.getSource().sendError(Text.literal("No permission."));
                                        return 0;
                                    }

                                    Billet billet = Billet.valueOf(
                                            StringArgumentType.getString(ctx, "billet").toUpperCase());
                                    SecondDawnRP.PROFILE_SERVICE.addBillet(player, billet);
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("Added billet " + billet), false);
                                    return 1;
                                })))

                .then(CommandManager.literal("removebillet")
                        .then(CommandManager.argument("billet", StringArgumentType.word())
                                .executes(ctx -> {
                                    ensureReady();
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_SERVICE.getLoaded(player.getUuid());
                                    if (!SecondDawnRP.PERMISSION_SERVICE.canRevokeBillet(player, actorProfile)) {
                                        ctx.getSource().sendError(Text.literal("No permission."));
                                        return 0;
                                    }

                                    Billet billet = Billet.valueOf(
                                            StringArgumentType.getString(ctx, "billet").toUpperCase());
                                    SecondDawnRP.PROFILE_SERVICE.removeBillet(player, billet);
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("Removed billet " + billet), false);
                                    return 1;
                                })))

                .then(CommandManager.literal("addcert")
                        .then(CommandManager.argument("cert", StringArgumentType.word())
                                .executes(ctx -> {
                                    ensureReady();
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_SERVICE.getLoaded(player.getUuid());
                                    if (!SecondDawnRP.PERMISSION_SERVICE.canGrantCertification(player, actorProfile)) {
                                        ctx.getSource().sendError(Text.literal("No permission."));
                                        return 0;
                                    }

                                    Certification cert = Certification.valueOf(
                                            StringArgumentType.getString(ctx, "cert").toUpperCase());
                                    SecondDawnRP.PROFILE_SERVICE.addCertification(player, cert);
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("Added certification " + cert), false);
                                    return 1;
                                })))

                .then(CommandManager.literal("removecert")
                        .then(CommandManager.argument("cert", StringArgumentType.word())
                                .executes(ctx -> {
                                    ensureReady();
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_SERVICE.getLoaded(player.getUuid());
                                    if (!SecondDawnRP.PERMISSION_SERVICE.canRevokeCertification(player, actorProfile)) {
                                        ctx.getSource().sendError(Text.literal("No permission."));
                                        return 0;
                                    }

                                    Certification cert = Certification.valueOf(
                                            StringArgumentType.getString(ctx, "cert").toUpperCase());
                                    SecondDawnRP.PROFILE_SERVICE.removeCertification(player, cert);
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("Removed certification " + cert), false);
                                    return 1;
                                })))

                .then(CommandManager.literal("addpoints")
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    ensureReady();
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_SERVICE.getLoaded(player.getUuid());
                                    if (!SecondDawnRP.PERMISSION_SERVICE.canAddRankPoints(player, actorProfile)) {
                                        ctx.getSource().sendError(Text.literal("No permission."));
                                        return 0;
                                    }

                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    SecondDawnRP.PROFILE_SERVICE.addRankPoints(player, amount);
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("Added " + amount + " rank points"), false);
                                    return 1;
                                })))
        );
    }

    private static int showSelf(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            PlayerProfile profile = SecondDawnRP.PROFILE_SERVICE.getLoaded(player.getUuid());
            if (profile == null) profile = SecondDawnRP.PROFILE_SERVICE.getOrLoad(player);
            sendDisplay(ctx.getSource(), profile, false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int showOther(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity actor = ctx.getSource().getPlayerOrThrow();
            PlayerProfile actorProfile = SecondDawnRP.PROFILE_SERVICE.getLoaded(actor.getUuid());
            if (!SecondDawnRP.PERMISSION_SERVICE.canViewOtherProfile(actor, actorProfile)) {
                ctx.getSource().sendError(Text.literal("No permission."));
                return 0;
            }

            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            PlayerProfile profile = SecondDawnRP.PROFILE_SERVICE.getLoaded(target.getUuid());
            if (profile == null) {
                ctx.getSource().sendError(Text.literal(
                        target.getName().getString() + " has no loaded profile."));
                return 0;
            }
            sendDisplay(ctx.getSource(), profile, true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static void sendDisplay(ServerCommandSource source, PlayerProfile p, boolean gmView) {
        source.sendFeedback(() -> Text.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .formatted(Formatting.DARK_GRAY), false);

        boolean deceased = p.getCharacterStatus() == CharacterStatus.DECEASED;
        source.sendFeedback(() -> Text.literal("Crew Profile")
                .formatted(Formatting.GOLD)
                .append(deceased
                        ? Text.literal(" [DECEASED]").formatted(Formatting.DARK_RED)
                        : Text.literal("").formatted(Formatting.RESET)), false);

        source.sendFeedback(() -> row("Name",
                p.getCharacterName() != null
                        ? p.getCharacterName() : "(not set — visit Creation Terminal)"), false);
        source.sendFeedback(() -> row("Species",
                p.getSpecies() != null ? p.getSpecies() : "(not set)"), false);

        String bioText = p.getBio() != null && !p.getBio().isBlank() ? p.getBio() : "(no biography)";
        source.sendFeedback(() -> row("Bio",
                bioText.length() > 80 ? bioText.substring(0, 77) + "…" : bioText), false);

        source.sendFeedback(() -> Text.literal("  ·  ").formatted(Formatting.DARK_GRAY), false);

        source.sendFeedback(() -> row("Division",
                p.getDivision() != null ? p.getDivision().name() : "UNASSIGNED"), false);
        source.sendFeedback(() -> row("Rank",
                p.getRank() != null ? p.getRank().name() : "NONE"), false);
        source.sendFeedback(() -> row("Path",
                p.getProgressionPath() != null ? p.getProgressionPath().name() : "ENLISTED"), false);
        source.sendFeedback(() -> row("Points",
                p.getRankPoints() + " (Service Record: " + p.getServiceRecord() + ")"), false);
        source.sendFeedback(() -> row("Duty",
                p.getDutyStatus() != null ? p.getDutyStatus().name() : "OFF_DUTY"), false);

        if (!p.getBillets().isEmpty()) {
            source.sendFeedback(() -> row("Billets",
                    p.getBillets().stream().map(Enum::name)
                            .reduce((a, b) -> a + ", " + b).orElse("")), false);
        }
        if (!p.getCertifications().isEmpty()) {
            source.sendFeedback(() -> row("Certs",
                    p.getCertifications().stream().map(Enum::name)
                            .reduce((a, b) -> a + ", " + b).orElse("")), false);
        }

        source.sendFeedback(() -> Text.literal("  ·  ").formatted(Formatting.DARK_GRAY), false);

        source.sendFeedback(() -> row("Languages",
                p.getKnownLanguages().isEmpty() ? "none"
                        : String.join(", ", p.getKnownLanguages())), false);

        int conditionCount = p.getActiveMedicalConditionIds().size();
        source.sendFeedback(() -> row("Medical",
                conditionCount == 0 ? "No active conditions"
                        : conditionCount + " active condition(s) — see Tricorder"), false);

        if (p.getProgressionTransfer() > 0) {
            source.sendFeedback(() -> row("Transferred Points",
                    String.valueOf(p.getProgressionTransfer())), false);
        }

        if (gmView) {
            source.sendFeedback(() -> Text.literal("— GM —").formatted(Formatting.DARK_GRAY), false);
            source.sendFeedback(() -> row("Minecraft Name", p.getServiceName()), false);
            source.sendFeedback(() -> row("Player UUID", p.getPlayerId().toString()), false);
            source.sendFeedback(() -> row("Character ID",
                    p.getCharacterId() != null ? p.getCharacterId() : "none"), false);
            source.sendFeedback(() -> row("Permadeath", String.valueOf(p.isPermadeathConsent())), false);
            source.sendFeedback(() -> row("Univ. Translator",
                    String.valueOf(p.hasUniversalTranslator())), false);
            if (p.getCharacterCreatedAt() > 0) {
                source.sendFeedback(() -> row("Char. Created",
                        DATE_FMT.format(Instant.ofEpochMilli(p.getCharacterCreatedAt()))), false);
            }
            if (p.getDeceasedAt() != null) {
                source.sendFeedback(() -> row("Died",
                        DATE_FMT.format(Instant.ofEpochMilli(p.getDeceasedAt()))), false);
            }
            if (!p.getActiveMedicalConditionIds().isEmpty()) {
                source.sendFeedback(() -> row("Condition IDs",
                        String.join(", ", p.getActiveMedicalConditionIds())), false);
            }
        }

        source.sendFeedback(() -> Text.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .formatted(Formatting.DARK_GRAY), false);
    }

    private static Text row(String label, String value) {
        return Text.literal(label + ": ").formatted(Formatting.GOLD)
                .append(Text.literal(value).formatted(Formatting.WHITE));
    }

    private static void ensureReady() {
        if (SecondDawnRP.PROFILE_SERVICE == null) {
            throw new IllegalStateException("Profile services not initialized.");
        }
    }
}