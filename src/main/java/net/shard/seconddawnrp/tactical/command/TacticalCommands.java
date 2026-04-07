package net.shard.seconddawnrp.tactical.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.tactical.data.HardpointEntry;
import net.shard.seconddawnrp.tactical.service.EncounterService;
import net.shard.seconddawnrp.tactical.service.TacticalService;

/**
 * All Tactical commands in one file.
 *
 * GM commands:
 *   /gm encounter create|addship|start|pause|resume|end|list
 *   /gm ship jump|warp|sublight
 *
 * Admin commands:
 *   /admin shipyard set
 *   /admin ship register|unregister|list
 *   /admin hardpoint register|remove|list
 */
public class TacticalCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                TacticalService tacticalService) {
        EncounterService es = tacticalService.getEncounterService();

        // ── /gm encounter ──────────────────────────────────────────────────────

        dispatcher.register(CommandManager.literal("gm")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("encounter")

                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            String result = es.createEncounter(id);
                                            feedback(ctx.getSource(), result, Formatting.GREEN);
                                            return 1;
                                        })))

                        .then(CommandManager.literal("addship")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .then(CommandManager.argument("class", StringArgumentType.word())
                                                        .then(CommandManager.argument("faction", StringArgumentType.word())
                                                                .then(CommandManager.argument("mode", StringArgumentType.word())
                                                                        .executes(ctx -> {
                                                                            String result = es.addShip(
                                                                                    StringArgumentType.getString(ctx, "encounterId"),
                                                                                    StringArgumentType.getString(ctx, "shipId"),
                                                                                    StringArgumentType.getString(ctx, "class"),
                                                                                    StringArgumentType.getString(ctx, "faction"),
                                                                                    StringArgumentType.getString(ctx, "mode"));
                                                                            feedback(ctx.getSource(), result, Formatting.GREEN);
                                                                            return 1;
                                                                        })))))))

                        .then(CommandManager.literal("start")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String result = es.startEncounter(
                                                    StringArgumentType.getString(ctx, "id"));
                                            feedback(ctx.getSource(), result, Formatting.GREEN);
                                            return 1;
                                        })))

                        .then(CommandManager.literal("pause")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String result = es.pauseEncounter(
                                                    StringArgumentType.getString(ctx, "id"));
                                            feedback(ctx.getSource(), result, Formatting.YELLOW);
                                            return 1;
                                        })))

                        .then(CommandManager.literal("resume")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String result = es.resumeEncounter(
                                                    StringArgumentType.getString(ctx, "id"));
                                            feedback(ctx.getSource(), result, Formatting.GREEN);
                                            return 1;
                                        })))

                        .then(CommandManager.literal("end")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String result = es.endEncounter(
                                                            StringArgumentType.getString(ctx, "id"),
                                                            StringArgumentType.getString(ctx, "reason"));
                                                    feedback(ctx.getSource(), result, Formatting.YELLOW);
                                                    return 1;
                                                }))))

                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    var encounters = es.getAllEncounters();
                                    if (encounters.isEmpty()) {
                                        feedback(ctx.getSource(), "No active encounters.", Formatting.GRAY);
                                        return 0;
                                    }
                                    feedback(ctx.getSource(), "── Active Encounters ──", Formatting.AQUA);
                                    for (var e : encounters) {
                                        feedback(ctx.getSource(),
                                                "  " + e.getEncounterId() + " [" + e.getStatus().name()
                                                        + "] — " + e.getShipCount() + " ships",
                                                Formatting.WHITE);
                                    }
                                    return encounters.size();
                                }))
                )

                // ── /gm ship ───────────────────────────────────────────────────────

                .then(CommandManager.literal("ship")

                        .then(CommandManager.literal("jump")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String result = es.jumpShip(
                                                            StringArgumentType.getString(ctx, "encounterId"),
                                                            StringArgumentType.getString(ctx, "shipId"));
                                                    feedback(ctx.getSource(), result, Formatting.AQUA);
                                                    return 1;
                                                }))))

                        .then(CommandManager.literal("warp")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .then(CommandManager.argument("factor", IntegerArgumentType.integer(1, 9))
                                                        .executes(ctx -> {
                                                            String result = tacticalService.engageWarp(
                                                                    StringArgumentType.getString(ctx, "encounterId"),
                                                                    StringArgumentType.getString(ctx, "shipId"),
                                                                    IntegerArgumentType.getInteger(ctx, "factor"));
                                                            feedback(ctx.getSource(), result, Formatting.GREEN);
                                                            return 1;
                                                        })))))

                        .then(CommandManager.literal("sublight")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String eid = StringArgumentType.getString(ctx, "encounterId");
                                                    String sid = StringArgumentType.getString(ctx, "shipId");
                                                    String result = es.getEncounter(eid)
                                                            .flatMap(e -> e.getShip(sid))
                                                            .map(ship -> tacticalService.getWarpService().dropToSublight(ship))
                                                            .orElse("Ship or encounter not found.");
                                                    feedback(ctx.getSource(), result, Formatting.YELLOW);
                                                    return 1;
                                                }))))

                        .then(CommandManager.literal("status")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String eid = StringArgumentType.getString(ctx, "encounterId");
                                                    String sid = StringArgumentType.getString(ctx, "shipId");
                                                    es.getEncounter(eid)
                                                            .flatMap(e -> e.getShip(sid))
                                                            .ifPresentOrElse(ship -> {
                                                                ctx.getSource().sendFeedback(() -> Text.literal(
                                                                                "[Tactical] " + ship.getRegistryName()
                                                                                        + " [" + ship.getCombatId() + "]"
                                                                                        + "\n  Hull: " + ship.getHullIntegrity() + "/" + ship.getHullMax()
                                                                                        + " (" + ship.getHullState().name() + ")"
                                                                                        + "\n  Shields: F=" + ship.getShield(net.shard.seconddawnrp.tactical.data.ShipState.ShieldFacing.FORE)
                                                                                        + " A=" + ship.getShield(net.shard.seconddawnrp.tactical.data.ShipState.ShieldFacing.AFT)
                                                                                        + " P=" + ship.getShield(net.shard.seconddawnrp.tactical.data.ShipState.ShieldFacing.PORT)
                                                                                        + " S=" + ship.getShield(net.shard.seconddawnrp.tactical.data.ShipState.ShieldFacing.STARBOARD)
                                                                                        + "\n  Power: " + ship.getPowerBudget()
                                                                                        + " | Warp: " + ship.getWarpSpeed()
                                                                                        + " | Torpedoes: " + ship.getTorpedoCount())
                                                                        .formatted(Formatting.AQUA), false);
                                                            }, () -> feedback(ctx.getSource(), "Ship not found.", Formatting.RED));
                                                    return 1;
                                                }))))
                )
        );

        // ── /admin ship + hardpoint + shipyard ────────────────────────────────

        dispatcher.register(CommandManager.literal("admin")
                .requires(src -> src.hasPermissionLevel(4))

                .then(CommandManager.literal("shipyard")
                        .then(CommandManager.literal("set")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) return 0;
                                    String worldKey = player.getWorld().getRegistryKey().getValue().toString();
                                    es.setShipyard(worldKey, player.getX(), player.getY(), player.getZ());
                                    feedback(ctx.getSource(), "Shipyard spawn set at current position.", Formatting.GREEN);
                                    return 1;
                                })))

                .then(CommandManager.literal("ship")

                        .then(CommandManager.literal("register")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .then(CommandManager.argument("name", StringArgumentType.string())
                                                .then(CommandManager.argument("class", StringArgumentType.word())
                                                        .then(CommandManager.argument("faction", StringArgumentType.word())
                                                                .executes(ctx -> {
                                                                    String result = es.registerShip(
                                                                            StringArgumentType.getString(ctx, "shipId"),
                                                                            StringArgumentType.getString(ctx, "name"),
                                                                            StringArgumentType.getString(ctx, "class"),
                                                                            StringArgumentType.getString(ctx, "faction"));
                                                                    feedback(ctx.getSource(), result, Formatting.GREEN);
                                                                    return 1;
                                                                }))))))

                        .then(CommandManager.literal("unregister")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String result = es.unregisterShip(
                                                    StringArgumentType.getString(ctx, "shipId"));
                                            feedback(ctx.getSource(), result, Formatting.YELLOW);
                                            return 1;
                                        })))

                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    var registry = es.getShipRegistry();
                                    if (registry.isEmpty()) {
                                        feedback(ctx.getSource(), "No ships registered.", Formatting.GRAY);
                                        return 0;
                                    }
                                    feedback(ctx.getSource(), "── Ship Registry ──", Formatting.AQUA);
                                    registry.values().forEach(e -> feedback(ctx.getSource(),
                                            "  " + e.getShipId() + " | " + e.getRegistryName()
                                                    + " | " + e.getShipClass() + " | " + e.getFaction(),
                                            Formatting.WHITE));
                                    return registry.size();
                                })))

                .then(CommandManager.literal("hardpoint")

                        .then(CommandManager.literal("register")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .then(CommandManager.argument("arc", StringArgumentType.word())
                                                .then(CommandManager.argument("type", StringArgumentType.word())
                                                        .executes(ctx -> {
                                                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                                                            if (player == null) return 0;
                                                            BlockPos pos = player.getBlockPos();
                                                            String shipId = StringArgumentType.getString(ctx, "shipId");
                                                            String arcStr = StringArgumentType.getString(ctx, "arc").toUpperCase();
                                                            String typeStr = StringArgumentType.getString(ctx, "type").toUpperCase();
                                                            try {
                                                                HardpointEntry.Arc arc = HardpointEntry.Arc.valueOf(arcStr);
                                                                HardpointEntry.WeaponType wType = HardpointEntry.WeaponType.valueOf(typeStr);
                                                                String result = es.registerHardpoint(shipId, pos, arc, wType);
                                                                feedback(ctx.getSource(), result, Formatting.GREEN);
                                                            } catch (IllegalArgumentException e) {
                                                                feedback(ctx.getSource(),
                                                                        "Invalid arc (FORE/AFT/PORT/STARBOARD) or type (PHASER_ARRAY/TORPEDO_TUBE)",
                                                                        Formatting.RED);
                                                            }
                                                            return 1;
                                                        })))))

                        .then(CommandManager.literal("list")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String shipId = StringArgumentType.getString(ctx, "shipId");
                                            var hps = es.getHardpoints(shipId);
                                            if (hps.isEmpty()) {
                                                feedback(ctx.getSource(), "No hardpoints on " + shipId, Formatting.GRAY);
                                                return 0;
                                            }
                                            feedback(ctx.getSource(), "── Hardpoints on " + shipId + " ──", Formatting.AQUA);
                                            hps.forEach(h -> feedback(ctx.getSource(),
                                                    "  " + h.getHardpointId() + " | " + h.getWeaponType()
                                                            + " | " + h.getArc() + " | HP: " + h.getHealth(),
                                                    Formatting.WHITE));
                                            return hps.size();
                                        })))
                )
        );
    }

    private static void feedback(ServerCommandSource src, String msg, Formatting color) {
        src.sendFeedback(() -> Text.literal(msg).formatted(color), false);
    }
}