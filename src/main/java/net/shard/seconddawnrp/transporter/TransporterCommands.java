package net.shard.seconddawnrp.transporter;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.playerdata.Certification;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;

/**
 * /transporter ready — toggle opt-in to transporter targeting (session-only)
 * /beamup            — request pickup from colony dimension
 */
public class TransporterCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                TransporterService transporterService,
                                PlayerProfileManager profileManager) {

        dispatcher.register(CommandManager.literal("transporter")
                .then(CommandManager.literal("ready")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) return 0;

                            boolean nowReady = transporterService.toggleReady(player.getUuid());
                            if (nowReady) {
                                player.sendMessage(Text.literal(
                                                "[Transporter] You are now visible to transporter operators.")
                                        .formatted(Formatting.GREEN), false);
                            } else {
                                player.sendMessage(Text.literal(
                                                "[Transporter] You are no longer visible to transporter operators.")
                                        .formatted(Formatting.YELLOW), false);
                            }
                            return 1;
                        })));

        dispatcher.register(CommandManager.literal("beamup")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;

                    String dimPath = player.getWorld().getRegistryKey().getValue().getPath();
                    if (dimPath.equals("overworld")) {
                        player.sendMessage(Text.literal(
                                        "[Transporter] You are already on the ship.")
                                .formatted(Formatting.YELLOW), false);
                        return 0;
                    }

                    BeamUpRequest req = transporterService.submitBeamUpRequest(player);

                    player.sendMessage(Text.literal(
                                    "[Transporter] Beam-up request sent. Stand by for a response.")
                            .formatted(Formatting.AQUA), false);

                    notifyOperators(player, req, profileManager);
                    return 1;
                }));
    }

    private static void notifyOperators(ServerPlayerEntity requester,
                                        BeamUpRequest req,
                                        PlayerProfileManager profileManager) {
        if (requester.getServer() == null) return;

        for (ServerPlayerEntity online :
                requester.getServer().getPlayerManager().getPlayerList()) {
            if (online.getUuid().equals(requester.getUuid())) continue;

            PlayerProfile profile = profileManager.getLoadedProfile(online.getUuid());
            boolean isOperator = profile != null
                    && profile.getDivision() != null
                    && profile.getDivision().name().equals("OPERATIONS")
                    && profile.hasCertification(Certification.TRANSPORTER_OPERATOR);
            boolean isGm = online.hasPermissionLevel(2);

            if (isOperator || isGm) {
                online.sendMessage(Text.literal(
                                "[Transporter] ⚠ Beam-up request from §e"
                                        + requester.getName().getString()
                                        + " §7in §e" + req.getSourceDimension()
                                        + "§7. Check the transporter controller.")
                        .formatted(Formatting.YELLOW), false);
            }
        }
    }
}