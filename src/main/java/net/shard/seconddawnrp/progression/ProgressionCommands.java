package net.shard.seconddawnrp.progression;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.division.Rank;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.UUID;

public class ProgressionCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        registerCadetCommands(dispatcher);
        registerOfficerCommands(dispatcher);
        registerAdminSlotsCommands(dispatcher);
        registerAdminPositionCommands(dispatcher);
    }

    private static void registerCadetCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("cadet")

                .then(CommandManager.literal("enrol")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> {
                                    ServerPlayerEntity actor = ctx.getSource().getPlayerOrThrow();
                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(actor.getUuid());
                                    if (!SecondDawnRP.PERMISSION_SERVICE.canCadetEnrol(actor, actorProfile)) {
                                        actor.sendMessage(Text.literal("[Cadet] No permission."), false);
                                        return 0;
                                    }

                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                    boolean ok = SecondDawnRP.CADET_SERVICE.enrol(target.getUuid(), actor);
                                    actor.sendMessage(Text.literal(
                                            ok ? "[Cadet] Enrolled " + target.getName().getString()
                                                    : "[Cadet] Enrolment failed — check console."), false);
                                    return ok ? 1 : 0;
                                })))

                .then(CommandManager.literal("promote")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> {
                                    ServerPlayerEntity actor = ctx.getSource().getPlayerOrThrow();
                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(actor.getUuid());
                                    if (!SecondDawnRP.PERMISSION_SERVICE.canCadetPromote(actor, actorProfile)) {
                                        actor.sendMessage(Text.literal("[Cadet] No permission."), false);
                                        return 0;
                                    }

                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                    String result = SecondDawnRP.CADET_SERVICE.promote(actor, target.getUuid());
                                    actor.sendMessage(Text.literal("[Cadet] " + result), false);
                                    return 1;
                                })))

                .then(CommandManager.literal("graduate")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("startrank", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (Rank r : new Rank[]{
                                                    Rank.ENSIGN, Rank.LIEUTENANT_JG,
                                                    Rank.LIEUTENANT, Rank.LIEUTENANT_COMMANDER,
                                                    Rank.COMMANDER}) {
                                                builder.suggest(r.getId());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayerEntity actor = ctx.getSource().getPlayerOrThrow();
                                            PlayerProfile actorProfile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(actor.getUuid());
                                            if (!SecondDawnRP.PERMISSION_SERVICE.canCadetGraduate(actor, actorProfile)) {
                                                actor.sendMessage(Text.literal("[Cadet] No permission."), false);
                                                return 0;
                                            }

                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                            String rankStr = StringArgumentType.getString(ctx, "startrank");
                                            Rank startRank = parseRankById(rankStr);
                                            if (startRank == null) {
                                                actor.sendMessage(Text.literal("[Cadet] Unknown rank: " + rankStr), false);
                                                return 0;
                                            }

                                            String result = SecondDawnRP.CADET_SERVICE.proposeGraduation(
                                                    actor, target.getUuid(), startRank);
                                            actor.sendMessage(Text.literal("[Cadet] " + result), false);
                                            return 1;
                                        }))))

                .then(CommandManager.literal("approve")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> {
                                    ServerPlayerEntity actor = ctx.getSource().getPlayerOrThrow();
                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(actor.getUuid());
                                    if (!SecondDawnRP.PERMISSION_SERVICE.canCadetApprove(actor, actorProfile)) {
                                        actor.sendMessage(Text.literal("[Cadet] No permission."), false);
                                        return 0;
                                    }

                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                    String result = SecondDawnRP.CADET_SERVICE.approveGraduation(actor, target.getUuid());
                                    actor.sendMessage(Text.literal("[Cadet] " + result), false);
                                    return 1;
                                })))

                .then(CommandManager.literal("status")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> {
                                    ServerPlayerEntity actor = ctx.getSource().getPlayerOrThrow();
                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                    printCadetStatus(actor, target.getUuid());
                                    return 1;
                                })))
        );
    }

    private static void registerOfficerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("officer")
                .then(CommandManager.literal("commend")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("points", IntegerArgumentType.integer(1, 100))
                                        .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    ServerPlayerEntity actor = ctx.getSource().getPlayerOrThrow();
                                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(actor.getUuid());
                                                    if (!SecondDawnRP.PERMISSION_SERVICE.canIssueCommendation(actor, actorProfile)) {
                                                        actor.sendMessage(Text.literal("[Officer] No permission."), false);
                                                        return 0;
                                                    }

                                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                                    int points = IntegerArgumentType.getInteger(ctx, "points");
                                                    String reason = StringArgumentType.getString(ctx, "reason");
                                                    String result = SecondDawnRP.COMMENDATION_SERVICE.commend(
                                                            actor, target.getUuid(), points, reason);
                                                    actor.sendMessage(Text.literal("[Officer] " + result), false);
                                                    return 1;
                                                })))))
        );
    }

    private static void registerAdminSlotsCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("admin")
                .then(CommandManager.literal("slots")

                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    ServerPlayerEntity actor = ctx.getSource().getPlayerOrThrow();
                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(actor.getUuid());
                                    if (!SecondDawnRP.PERMISSION_SERVICE.canManageOfficerSlots(actor, actorProfile)) {
                                        actor.sendMessage(Text.literal("[Slots] No permission."), false);
                                        return 0;
                                    }
                                    printSlotList(actor);
                                    return 1;
                                }))

                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("rank", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (Rank r : new Rank[]{
                                                    Rank.ENSIGN, Rank.LIEUTENANT_JG,
                                                    Rank.LIEUTENANT, Rank.LIEUTENANT_COMMANDER,
                                                    Rank.COMMANDER}) {
                                                builder.suggest(r.getId());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(CommandManager.argument("count", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> {
                                                    ServerPlayerEntity actor = ctx.getSource().getPlayerOrThrow();
                                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(actor.getUuid());
                                                    if (!SecondDawnRP.PERMISSION_SERVICE.canManageOfficerSlots(actor, actorProfile)) {
                                                        actor.sendMessage(Text.literal("[Slots] No permission."), false);
                                                        return 0;
                                                    }

                                                    String rankStr = StringArgumentType.getString(ctx, "rank");
                                                    int count = IntegerArgumentType.getInteger(ctx, "count");
                                                    Rank rank = parseRankById(rankStr);
                                                    if (rank == null) {
                                                        actor.sendMessage(Text.literal("[Slots] Unknown rank: " + rankStr), false);
                                                        return 0;
                                                    }
                                                    SecondDawnRP.OFFICER_SLOT_SERVICE.setSlots(rank, count, actor);
                                                    return 1;
                                                }))))

                        .then(CommandManager.literal("queue")
                                .then(CommandManager.argument("rank", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (Rank r : Rank.values()) builder.suggest(r.getId());
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            ServerPlayerEntity actor = ctx.getSource().getPlayerOrThrow();
                                            PlayerProfile actorProfile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(actor.getUuid());
                                            if (!SecondDawnRP.PERMISSION_SERVICE.canManageOfficerSlots(actor, actorProfile)) {
                                                actor.sendMessage(Text.literal("[Slots] No permission."), false);
                                                return 0;
                                            }

                                            String rankStr = StringArgumentType.getString(ctx, "rank");
                                            Rank rank = parseRankById(rankStr);
                                            if (rank == null) {
                                                actor.sendMessage(Text.literal("[Slots] Unknown rank: " + rankStr), false);
                                                return 0;
                                            }
                                            printQueue(actor, rank);
                                            return 1;
                                        }))))

                .then(CommandManager.literal("position")

                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .then(CommandManager.argument("position", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("FIRST_OFFICER");
                                                    builder.suggest("SECOND_OFFICER");
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    ServerPlayerEntity actor = ctx.getSource().getPlayerOrThrow();
                                                    PlayerProfile actorProfile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(actor.getUuid());
                                                    if (!SecondDawnRP.PERMISSION_SERVICE.canAssignShipPosition(actor, actorProfile)) {
                                                        actor.sendMessage(Text.literal("[Position] No permission."), false);
                                                        return 0;
                                                    }

                                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                                    String posStr = StringArgumentType.getString(ctx, "position");
                                                    ShipPosition position;
                                                    try {
                                                        position = ShipPosition.valueOf(posStr.toUpperCase());
                                                    } catch (IllegalArgumentException e) {
                                                        actor.sendMessage(Text.literal("[Position] Unknown position: " + posStr), false);
                                                        return 0;
                                                    }
                                                    String result = SecondDawnRP.SHIP_POSITION_SERVICE.assign(
                                                            actor, target.getUuid(), position);
                                                    actor.sendMessage(Text.literal(result), false);
                                                    return 1;
                                                }))))

                        .then(CommandManager.literal("clear")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            ServerPlayerEntity actor = ctx.getSource().getPlayerOrThrow();
                                            PlayerProfile actorProfile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(actor.getUuid());
                                            if (!SecondDawnRP.PERMISSION_SERVICE.canAssignShipPosition(actor, actorProfile)) {
                                                actor.sendMessage(Text.literal("[Position] No permission."), false);
                                                return 0;
                                            }

                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                            String result = SecondDawnRP.SHIP_POSITION_SERVICE.clear(
                                                    actor, target.getUuid());
                                            actor.sendMessage(Text.literal(result), false);
                                            return 1;
                                        }))))
        );
    }

    private static void registerAdminPositionCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        // no-op
    }

    private static void printCadetStatus(ServerPlayerEntity actor, UUID cadetUuid) {
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(cadetUuid);
        if (profile == null) {
            actor.sendMessage(Text.literal("[Cadet] Profile not loaded."), false);
            return;
        }
        actor.sendMessage(Text.literal("=== Cadet Status: " + profile.getDisplayName() + " ==="), false);
        actor.sendMessage(Text.literal("Rank: " + profile.getRank().getId()), false);
        actor.sendMessage(Text.literal("Division: " + profile.getDivision()), false);
        actor.sendMessage(Text.literal("Points: " + profile.getRankPoints()), false);
        actor.sendMessage(Text.literal("Service Record: " + profile.getServiceRecord()), false);

        boolean hasPending = SecondDawnRP.CADET_SERVICE.hasPendingGraduation(cadetUuid);
        if (hasPending) {
            Rank proposed = SecondDawnRP.CADET_SERVICE.getPendingGraduationRank(cadetUuid);
            actor.sendMessage(Text.literal(
                    "Pending Graduation: -> " + proposed.getId() + " (awaiting approval)"), false);
        }
    }

    private static void printSlotList(ServerPlayerEntity actor) {
        actor.sendMessage(Text.literal("=== Officer Slot Counts ==="), false);
        for (Rank rank : new Rank[]{
                Rank.ENSIGN, Rank.LIEUTENANT_JG, Rank.LIEUTENANT,
                Rank.LIEUTENANT_COMMANDER, Rank.COMMANDER, Rank.CAPTAIN}) {
            int cap = SecondDawnRP.OFFICER_SLOT_SERVICE.getSlots(rank);
            int active = SecondDawnRP.OFFICER_SLOT_SERVICE.countActive(rank);
            actor.sendMessage(Text.literal(rank.getId() + ": " + active + "/" + cap), false);
        }
    }

    private static void printQueue(ServerPlayerEntity actor, Rank rank) {
        var queue = SecondDawnRP.OFFICER_SLOT_SERVICE.getQueue(rank);
        if (queue.isEmpty()) {
            actor.sendMessage(Text.literal("[Slots] No players queued for " + rank.getId() + "."), false);
            return;
        }
        actor.sendMessage(Text.literal("=== Promotion Queue: " + rank.getId() + " ==="), false);
        int pos = 1;
        for (SlotQueueEntry entry : queue) {
            actor.sendMessage(Text.literal(
                    pos++ + ". " + entry.getPlayerUuid()
                            + " | SR: " + entry.getServiceRecord()), false);
        }
    }

    private static Rank parseRankById(String id) {
        for (Rank r : Rank.values()) {
            if (r.getId().equals(id) || r.name().equalsIgnoreCase(id)) return r;
        }
        return null;
    }
}