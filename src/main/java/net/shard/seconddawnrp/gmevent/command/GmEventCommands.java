package net.shard.seconddawnrp.gmevent.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.ActiveEvent;
import net.shard.seconddawnrp.gmevent.data.EncounterTemplate;
import net.shard.seconddawnrp.gmevent.data.SpawnBehaviour;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class GmEventCommands {

    private GmEventCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("gmevent")

                // /gmevent spawn <templateId>
                .then(literal("spawn")
                        .then(argument("templateId", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayerEntity player = getRequiredPlayer(context.getSource());
                                    if (player == null) {
                                        context.getSource().sendError(Text.literal("Only players can use this."));
                                        return 0;
                                    }
                                    if (!hasGmPermission(player)) {
                                        context.getSource().sendError(Text.literal("[GM] No permission."));
                                        return 0;
                                    }

                                    String templateId = StringArgumentType.getString(context, "templateId");
                                    ServerWorld world = player.getServerWorld();
                                    BlockPos pos = player.getBlockPos();

                                    var event = SecondDawnRP.GM_EVENT_SERVICE
                                            .triggerEvent(world, pos, templateId, null);

                                    if (event.isPresent()) {
                                        context.getSource().sendMessage(Text.literal(
                                                "[GM] Event started: " + event.get().getEventId()));
                                        return 1;
                                    } else {
                                        context.getSource().sendError(Text.literal(
                                                "[GM] Unknown template: " + templateId));
                                        return 0;
                                    }
                                })))

                // /gmevent stop <eventId>
                .then(literal("stop")
                        .then(argument("eventId", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayerEntity player = getRequiredPlayer(context.getSource());
                                    if (!hasGmPermission(player)) {
                                        context.getSource().sendError(Text.literal("[GM] No permission."));
                                        return 0;
                                    }
                                    String eventId = StringArgumentType.getString(context, "eventId");
                                    boolean stopped = SecondDawnRP.GM_EVENT_SERVICE.stopEvent(eventId);
                                    if (stopped) {
                                        context.getSource().sendMessage(Text.literal("[GM] Event stopped: " + eventId));
                                        return 1;
                                    } else {
                                        context.getSource().sendError(Text.literal("[GM] No active event: " + eventId));
                                        return 0;
                                    }
                                })))

                // /gmevent stopall
                .then(literal("stopall")
                        .executes(context -> {
                            ServerPlayerEntity player = getRequiredPlayer(context.getSource());
                            if (!hasGmPermission(player)) {
                                context.getSource().sendError(Text.literal("[GM] No permission."));
                                return 0;
                            }
                            SecondDawnRP.GM_EVENT_SERVICE.stopAllEvents();
                            context.getSource().sendMessage(Text.literal("[GM] All events stopped."));
                            return 1;
                        }))

                // /gmevent list
                .then(literal("list")
                        .executes(context -> {
                            ServerPlayerEntity player = getRequiredPlayer(context.getSource());
                            if (!hasGmPermission(player)) {
                                context.getSource().sendError(Text.literal("[GM] No permission."));
                                return 0;
                            }
                            List<ActiveEvent> events = SecondDawnRP.GM_EVENT_SERVICE.getActiveEvents();
                            if (events.isEmpty()) {
                                context.getSource().sendMessage(Text.literal("[GM] No active events."));
                                return 1;
                            }
                            context.getSource().sendMessage(Text.literal("=== Active Events ==="));
                            for (ActiveEvent e : events) {
                                context.getSource().sendMessage(Text.literal(
                                        " [" + e.getEventId() + "] template=" + e.getTemplateId()
                                                + " spawned=" + e.getTotalSpawned()
                                                + " killed=" + e.getTotalKilled()
                                                + " active=" + e.getActiveCount()
                                ));
                            }
                            return 1;
                        }))

                // /gmevent link <eventId> <taskId>
                .then(literal("link")
                        .then(argument("eventId", StringArgumentType.word())
                                .then(argument("taskId", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayerEntity player = getRequiredPlayer(context.getSource());
                                            if (!hasGmPermission(player)) {
                                                context.getSource().sendError(Text.literal("[GM] No permission."));
                                                return 0;
                                            }
                                            context.getSource().sendMessage(Text.literal(
                                                    "[GM] Task linking must be set at spawn time or via spawn block config."));
                                            return 1;
                                        }))))

                // /gmevent templates
                .then(literal("templates")
                        .executes(context -> {
                            ServerPlayerEntity player = getRequiredPlayer(context.getSource());
                            if (!hasGmPermission(player)) {
                                context.getSource().sendError(Text.literal("[GM] No permission."));
                                return 0;
                            }
                            List<EncounterTemplate> templates = SecondDawnRP.GM_EVENT_SERVICE.getTemplates();
                            if (templates.isEmpty()) {
                                context.getSource().sendMessage(Text.literal("[GM] No templates loaded."));
                                return 1;
                            }
                            context.getSource().sendMessage(Text.literal("=== Encounter Templates ==="));
                            for (EncounterTemplate t : templates) {
                                context.getSource().sendMessage(Text.literal(
                                        " [" + t.getId() + "] " + t.getDisplayName()
                                                + " | mob=" + t.getMobTypeId()
                                                + " | count=" + t.getTotalSpawnCount()
                                                + " | behaviour=" + t.getSpawnBehaviour().name()
                                ));
                            }
                            return 1;
                        }))

                // /gmevent spawn <templateId> link <taskId>
                .then(literal("spawnlinked")
                        .then(argument("templateId", StringArgumentType.word())
                                .then(argument("taskId", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayerEntity player = getRequiredPlayer(context.getSource());
                                            if (player == null || !hasGmPermission(player)) {
                                                context.getSource().sendError(Text.literal("[GM] No permission."));
                                                return 0;
                                            }
                                            String templateId = StringArgumentType.getString(context, "templateId");
                                            String taskId = StringArgumentType.getString(context, "taskId");
                                            ServerWorld world = player.getServerWorld();
                                            BlockPos pos = player.getBlockPos();

                                            var event = SecondDawnRP.GM_EVENT_SERVICE
                                                    .triggerEvent(world, pos, templateId, taskId);

                                            if (event.isPresent()) {
                                                context.getSource().sendMessage(Text.literal(
                                                        "[GM] Event started: " + event.get().getEventId()
                                                                + " linked to task: " + taskId));
                                                return 1;
                                            } else {
                                                context.getSource().sendError(Text.literal(
                                                        "[GM] Unknown template: " + templateId));
                                                return 0;
                                            }
                                        }))))
        );
    }

    private static boolean hasGmPermission(ServerPlayerEntity player) {
        if (player == null) return false;
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        if (profile == null) return false;
        return SecondDawnRP.GM_PERMISSION_SERVICE.canTriggerEvents(player, profile);
    }

    private static ServerPlayerEntity getRequiredPlayer(ServerCommandSource source) {
        try { return source.getPlayer(); }
        catch (Exception e) { return null; }
    }
}