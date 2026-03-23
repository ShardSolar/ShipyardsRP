package net.shard.seconddawnrp.tasksystem.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.gmevent.network.*;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.tasksystem.data.OpsTaskStatus;
import net.shard.seconddawnrp.tasksystem.data.TaskAssignmentSource;
import net.shard.seconddawnrp.tasksystem.data.TaskTemplate;
import net.shard.seconddawnrp.tasksystem.pad.AdminTaskViewModel;

import java.util.List;

public class ModNetworking {

    public static void registerC2SPackets() {
        PayloadTypeRegistry.playC2S().register(
                CreateTaskC2SPacket.ID,
                CreateTaskC2SPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                AssignTaskC2SPacket.ID,
                AssignTaskC2SPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                ReviewTaskActionC2SPacket.ID,
                ReviewTaskActionC2SPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                SubmitManualConfirmC2SPacket.ID,
                SubmitManualConfirmC2SPacket.CODEC
        );

        PayloadTypeRegistry.playS2C().register(
                OpsPadRefreshS2CPacket.ID,
                OpsPadRefreshS2CPacket.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                CreateTaskC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleCreateTask(context.player(), payload))
        );

        ServerPlayNetworking.registerGlobalReceiver(
                AssignTaskC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleAssignTask(context.player(), payload))
        );

        ServerPlayNetworking.registerGlobalReceiver(
                ReviewTaskActionC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleReviewAction(context.player(), payload))
        );

        ServerPlayNetworking.registerGlobalReceiver(
                SubmitManualConfirmC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleSubmitManualConfirm(context.player(), payload))
        );

        PayloadTypeRegistry.playC2S().register(
                EditTaskC2SPacket.ID,
                EditTaskC2SPacket.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                EditTaskC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleEditTask(context.player(), payload))
        );

        PayloadTypeRegistry.playC2S().register(
                AcceptTerminalTaskC2SPacket.ID,
                AcceptTerminalTaskC2SPacket.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                AcceptTerminalTaskC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleAcceptTerminalTask(context.player(), payload))
        );

        PayloadTypeRegistry.playC2S().register(SaveTemplateC2SPacket.ID, SaveTemplateC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(ActivateSpawnBlockC2SPacket.ID, ActivateSpawnBlockC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(PushToPoolC2SPacket.ID, PushToPoolC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(FireSpawnC2SPacket.ID, FireSpawnC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DespawnAllC2SPacket.ID, DespawnAllC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(GmToolRefreshS2CPacket.ID, GmToolRefreshS2CPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SaveTemplateC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() -> handleSaveTemplate(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ActivateSpawnBlockC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() -> handleActivateSpawnBlock(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(PushToPoolC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() -> handlePushToPool(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(FireSpawnC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() -> handleFireSpawn(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(DespawnAllC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() -> handleDespawnAll(context.player(), payload)));
    }

    private static void handleCreateTask(ServerPlayerEntity player, CreateTaskC2SPacket packet) {
        PlayerProfile actorProfile = getActorProfile(player);
        if (actorProfile == null) {
            player.sendMessage(Text.literal("Your profile is not loaded."), false);
            return;
        }

        if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canCreateTasks(player, actorProfile)) {
            player.sendMessage(Text.literal("You do not have permission to create tasks."), false);
            return;
        }

        // Auto-generate task ID from display name — ignores packet.taskId() entirely
        String autoId = packet.displayName()
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (autoId.isBlank()) {
            player.sendMessage(Text.literal("Task creation failed. Display name is required."), false);
            return;
        }

        var createdEntry = SecondDawnRP.TASK_SERVICE.createPoolTask(
                autoId,
                packet.displayName(),
                packet.description(),
                packet.getDivision(),
                packet.getObjectiveType(),
                packet.targetId(),
                packet.requiredAmount(),
                packet.rewardPoints(),
                packet.officerConfirmationRequired(),
                player.getUuid()
        );

        if (createdEntry != null) {
            player.sendMessage(
                    Text.literal("Task created: " + createdEntry.getDisplayName()
                            + " [" + createdEntry.getTaskId() + "]"),
                    false
            );
            sendOpsPadRefresh(player);
        } else {
            player.sendMessage(Text.literal("Task creation failed. Check all fields."), false);
        }
    }

    private static void handleAssignTask(ServerPlayerEntity player, AssignTaskC2SPacket packet) {
        PlayerProfile actorProfile = getActorProfile(player);
        if (actorProfile == null) {
            player.sendMessage(Text.literal("Your profile is not loaded."), false);
            return;
        }

        String mode = packet.assignModeName();

        boolean success = switch (mode) {
            case "PUBLIC" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canPublishTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to publish tasks."), false);
                    yield false;
                }
                yield SecondDawnRP.TASK_SERVICE.publishPoolTask(packet.taskId());
            }

            case "DIVISION" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canAssignTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to assign tasks."), false);
                    yield false;
                }

                try {
                    Division division = Division.valueOf(packet.divisionName());
                    yield SecondDawnRP.TASK_SERVICE.assignPoolTaskToDivisionPool(packet.taskId(), division);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(Text.literal("Assignment failed. Invalid division."), false);
                    yield false;
                }
            }

            case "PLAYER" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canAssignTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to assign tasks."), false);
                    yield false;
                }

                String name = packet.playerName().trim();
                if (name.isBlank()) {
                    player.sendMessage(Text.literal("Enter a player name."), false);
                    yield false;
                }

                ServerPlayerEntity targetPlayer = player.getServer()
                        .getPlayerManager()
                        .getPlayer(name);

                if (targetPlayer == null) {
                    player.sendMessage(Text.literal("Player not found or not online."), false);
                    yield false;
                }

                PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(targetPlayer.getUuid());
                if (profile == null) {
                    player.sendMessage(Text.literal("Player profile is not loaded."), false);
                    yield false;
                }

                yield SecondDawnRP.TASK_SERVICE.assignPoolTaskToPlayer(
                        packet.taskId(),
                        profile,
                        player.getUuid()
                );
            }

            default -> {
                player.sendMessage(Text.literal("Assignment failed. Invalid mode."), false);
                yield false;
            }
        };

        if (success) {
            player.sendMessage(Text.literal("Task assignment updated."), false);
            sendOpsPadRefresh(player);
        } else {
            player.sendMessage(Text.literal("Assignment failed."), false);
        }
    }

    private static void handleReviewAction(ServerPlayerEntity player, ReviewTaskActionC2SPacket packet) {
        PlayerProfile actorProfile = getActorProfile(player);
        if (actorProfile == null) {
            player.sendMessage(Text.literal("Your profile is not loaded."), false);
            return;
        }

        String taskId = packet.taskId();
        String action = packet.actionName();

        boolean success = false;

        switch (action) {
            case "APPROVE" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canApproveTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to approve tasks."), false);
                    return;
                }

                PlayerProfile targetProfile = findAssignedProfile(taskId);
                if (targetProfile == null) {
                    player.sendMessage(Text.literal("No assigned player profile found for this task."), false);
                    return;
                }
                success = SecondDawnRP.TASK_SERVICE.approveTask(targetProfile, taskId);
            }

            case "RETURN" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canReturnTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to return tasks."), false);
                    return;
                }
                success = SecondDawnRP.TASK_SERVICE.returnTaskToInProgress(
                        taskId,
                        "Returned by operations review."
                );
            }

            case "FAIL" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canFailTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to fail tasks."), false);
                    return;
                }
                success = SecondDawnRP.TASK_SERVICE.failTask(
                        taskId,
                        "Marked failed by operations review."
                );
            }

            case "CANCEL" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canCancelTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to cancel tasks."), false);
                    return;
                }
                success = SecondDawnRP.TASK_SERVICE.cancelPoolTask(taskId);
            }

            default -> {
                player.sendMessage(Text.literal("Invalid review action."), false);
                return;
            }
        }

        if (success) {
            player.sendMessage(Text.literal("Task updated: " + action), false);
            sendOpsPadRefresh(player);
        } else {
            player.sendMessage(Text.literal("Task action failed."), false);
        }

    }

    private static void handleSubmitManualConfirm(ServerPlayerEntity player, SubmitManualConfirmC2SPacket packet) {
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        if (profile == null) {
            player.sendMessage(Text.literal("Profile not loaded."), false);
            return;
        }

        boolean success = SecondDawnRP.TASK_SERVICE.submitManualConfirmTaskForReview(profile, packet.taskId());

        if (success) {
            player.sendMessage(Text.literal("Manual confirmation task submitted for review."), false);
        } else {
            player.sendMessage(Text.literal("Selected task could not be submitted."), false);
        }
    }

    private static void sendOpsPadRefresh(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, buildOpsPadRefreshPacket());
    }

    private static OpsPadRefreshS2CPacket buildOpsPadRefreshPacket() {
        List<OpsPadRefreshS2CPacket.TaskEntry> entries = SecondDawnRP.TASK_SERVICE.buildAdminTaskViews()
                .stream()
                .map(ModNetworking::toTaskEntry)
                .toList();

        return new OpsPadRefreshS2CPacket(entries);
    }

    private static OpsPadRefreshS2CPacket.TaskEntry toTaskEntry(AdminTaskViewModel view) {
        return new OpsPadRefreshS2CPacket.TaskEntry(
                view.getTaskId(),
                view.getTitle(),
                view.getStatus(),
                view.getAssigneeLabel(),
                view.getDivisionLabel(),
                view.getProgressLabel(),
                view.getDetailLines()
        );
    }

    private static PlayerProfile findAssignedProfile(String taskId) {
        for (var entry : SecondDawnRP.TASK_SERVICE.getPoolEntries()) {
            if (!entry.getTaskId().equals(taskId)) {
                continue;
            }

            if (entry.getAssignedPlayerUuid() == null) {
                return null;
            }

            return SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(entry.getAssignedPlayerUuid());
        }

        return null;
    }

    private static PlayerProfile getActorProfile(ServerPlayerEntity player) {
        return SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
    }

    private static void handleEditTask(ServerPlayerEntity player, EditTaskC2SPacket packet) {
        PlayerProfile actorProfile = getActorProfile(player);
        if (actorProfile == null) {
            player.sendMessage(Text.literal("Your profile is not loaded."), false);
            return;
        }

        if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canEditTasks(player, actorProfile)) {
            player.sendMessage(Text.literal("You do not have permission to edit tasks."), false);
            return;
        }

        Division division;
        try {
            division = packet.getDivision();
        } catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("Edit failed. Invalid division."), false);
            return;
        }

        boolean success = SecondDawnRP.TASK_SERVICE.editPoolTask(
                packet.taskId(),
                packet.displayName(),
                packet.description(),
                division,
                packet.requiredAmount(),
                packet.rewardPoints(),
                packet.officerConfirmationRequired()
        );

        if (success) {
            player.sendMessage(Text.literal("Task updated: " + packet.displayName()), false);
            sendOpsPadRefresh(player);
        } else {
            player.sendMessage(Text.literal("Task edit failed."), false);
        }
    }

    private static void handleAcceptTerminalTask(ServerPlayerEntity player, AcceptTerminalTaskC2SPacket packet) {
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        if (profile == null) {
            player.sendMessage(Text.literal("Profile not loaded."), false);
            return;
        }

        String taskId = packet.taskId();

        // Validate still available
        var poolEntry = SecondDawnRP.TASK_SERVICE.getPoolEntries().stream()
                .filter(e -> e.getTaskId().equals(taskId))
                .findFirst().orElse(null);

        if (poolEntry == null
                || (poolEntry.getStatus() != OpsTaskStatus.PUBLIC
                && poolEntry.getStatus() != OpsTaskStatus.UNASSIGNED)) {
            player.sendMessage(Text.literal("[Terminal] Task is no longer available."), false);
            return;
        }

        if (SecondDawnRP.TASK_SERVICE.hasActiveTask(profile, taskId)) {
            player.sendMessage(Text.literal("[Terminal] You already have this task."), false);
            return;
        }

        boolean assigned = SecondDawnRP.TASK_SERVICE.assignTask(
                profile, taskId, player.getUuid(), TaskAssignmentSource.SELF
        );

        if (!assigned) {
            player.sendMessage(Text.literal("[Terminal] Could not accept task."), false);
            return;
        }

        SecondDawnRP.TASK_SERVICE.linkPoolTaskToPlayer(taskId, profile);

        TaskTemplate template = SecondDawnRP.TASK_SERVICE.resolveTaskTemplate(taskId);
        String name = template != null ? template.getDisplayName() : taskId;
        player.sendMessage(Text.literal("[Terminal] Task accepted: " + name), false);
    }

    private static void handleSaveTemplate(ServerPlayerEntity player, SaveTemplateC2SPacket packet) {
        PlayerProfile profile = getActorProfile(player);
        if (profile == null || !SecondDawnRP.GM_PERMISSION_SERVICE.canManageTemplates(player, profile)) {
            player.sendMessage(Text.literal("[GM] No permission."), false); return;
        }
        var template = new net.shard.seconddawnrp.gmevent.data.EncounterTemplate(
                packet.id(), packet.displayName(), packet.mobTypeId(),
                packet.maxHealth(), packet.armor(), packet.totalSpawnCount(),
                packet.maxActiveAtOnce(), packet.spawnRadiusBlocks(), packet.spawnIntervalTicks(),
                packet.getBehaviour(), packet.statusEffects(), List.of(), List.of()
        );
        SecondDawnRP.GM_EVENT_SERVICE.saveTemplate(template);
        player.sendMessage(Text.literal("[GM] Template saved: " + packet.displayName()), false);
        sendGmToolRefresh(player);
    }

    private static void handleActivateSpawnBlock(ServerPlayerEntity player,
                                                 ActivateSpawnBlockC2SPacket packet) {
        PlayerProfile profile = getActorProfile(player);
        if (profile == null || !SecondDawnRP.GM_PERMISSION_SERVICE
                .canTriggerEvents(player, profile)) {
            player.sendMessage(Text.literal("[GM] No permission."), false);
            return;
        }

        var world = player.getServerWorld();
        var pos = new net.minecraft.util.math.BlockPos(packet.x(), packet.y(), packet.z());

        // Use the template ID from the GUI if provided
        String templateId = packet.templateId();
        if (templateId != null && !templateId.isBlank()) {
            // Update the spawn block registration to use the GUI-selected template
            SecondDawnRP.GM_EVENT_SERVICE.registerSpawnBlock(
                    world, pos, templateId,
                    packet.linkedTaskId());
        }

        var entry = SecondDawnRP.GM_EVENT_SERVICE.findSpawnBlock(world, pos).orElse(null);
        if (entry == null) {
            player.sendMessage(Text.literal(
                    "[GM] No spawn block registered at that position."), false);
            return;
        }

        if (packet.linkedTaskId() != null && !packet.linkedTaskId().isBlank()) {
            entry.setLinkedTaskId(packet.linkedTaskId());
        }

        boolean ok = SecondDawnRP.GM_EVENT_SERVICE.triggerSpawnBlock(world, pos);
        player.sendMessage(Text.literal(
                ok ? "[GM] Event triggered: " + entry.getTemplateId()
                        + " at " + pos.toShortString()
                        : "[GM] Trigger failed — check template is valid."), false);
    }

    private static void handlePushToPool(ServerPlayerEntity player, PushToPoolC2SPacket packet) {
        PlayerProfile profile = getActorProfile(player);
        if (profile == null || !SecondDawnRP.TASK_PERMISSION_SERVICE.canCreateTasks(player, profile)) {
            player.sendMessage(Text.literal("[GM] No permission."), false); return;
        }
        try {
            var division = net.shard.seconddawnrp.division.Division.valueOf(packet.divisionName());
            var entry = SecondDawnRP.TASK_SERVICE.createPoolTask(
                    "gm_" + packet.templateId() + "_" + System.currentTimeMillis() % 10000,
                    packet.taskDisplayName(), packet.taskDescription(),
                    division,
                    net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType.MANUAL_CONFIRM,
                    "GM Event: " + packet.templateId(), 1, 50, true, player.getUuid()
            );
            player.sendMessage(Text.literal(entry != null
                    ? "[GM] Task pushed to pool: " + entry.getTaskId()
                    : "[GM] Push failed."), false);
            if (entry != null) sendOpsPadRefresh(player);
        } catch (Exception e) {
            player.sendMessage(Text.literal("[GM] Push failed: " + e.getMessage()), false);
        }
    }

    private static void handleFireSpawn(ServerPlayerEntity player, FireSpawnC2SPacket packet) {
        PlayerProfile profile = getActorProfile(player);
        if (profile == null || !SecondDawnRP.GM_PERMISSION_SERVICE.canTriggerEvents(player, profile)) {
            player.sendMessage(Text.literal("[GM] No permission."), false); return;
        }
        var world = player.getServerWorld();
        var pos = new net.minecraft.util.math.BlockPos(packet.x(), packet.y(), packet.z());
        var event = SecondDawnRP.GM_EVENT_SERVICE.triggerEvent(world, pos, packet.templateId(), null);
        player.sendMessage(Text.literal(event.isPresent()
                ? "[GM] Event fired: " + event.get().getEventId()
                : "[GM] Unknown template: " + packet.templateId()), false);
    }

    private static void handleDespawnAll(ServerPlayerEntity player, DespawnAllC2SPacket packet) {
        PlayerProfile profile = getActorProfile(player);
        if (profile == null || !SecondDawnRP.GM_PERMISSION_SERVICE.canStopEvents(player, profile)) {
            player.sendMessage(Text.literal("[GM] No permission."), false); return;
        }
        SecondDawnRP.GM_EVENT_SERVICE.stopAllEvents();
        player.sendMessage(Text.literal("[GM] All events despawned."), false);
    }

    private static void sendGmToolRefresh(ServerPlayerEntity player) {
        var entries = SecondDawnRP.GM_EVENT_SERVICE.getTemplates().stream()
                .map(t -> new GmToolRefreshS2CPacket.TemplateEntry(
                        t.getId(), t.getDisplayName(), t.getMobTypeId(),
                        t.getMaxHealth(), t.getArmor(), t.getTotalSpawnCount(),
                        t.getMaxActiveAtOnce(), t.getSpawnRadiusBlocks(),
                        t.getSpawnIntervalTicks(), t.getSpawnBehaviour().name(),
                        t.getStatusEffects()))
                .toList();
        ServerPlayNetworking.send(player, new GmToolRefreshS2CPacket(entries));
    }

}