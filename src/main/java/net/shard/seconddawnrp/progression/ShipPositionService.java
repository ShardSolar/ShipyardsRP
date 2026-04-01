package net.shard.seconddawnrp.progression;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.division.Rank;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;

import java.util.UUID;

public class ShipPositionService {

    private final PlayerProfileManager profileManager;
    private MinecraftServer server;

    public ShipPositionService(PlayerProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public String assign(ServerPlayerEntity actor, UUID targetUuid,
                         ShipPosition position) {
        PlayerProfile actorProfile = profileManager.getLoadedProfile(actor.getUuid());
        if (!SecondDawnRP.PERMISSION_SERVICE.canAssignShipPosition(actor, actorProfile)) {
            return "You do not have permission to assign ship positions.";
        }

        if (position == ShipPosition.NONE) {
            return "Use clear to remove a position.";
        }

        PlayerProfile target = profileManager.getLoadedProfile(targetUuid);
        if (target == null) return "Target player is not online.";

        if (target.getRank() == null
                || target.getRank().getAuthorityLevel()
                < Rank.LIEUTENANT_COMMANDER.getAuthorityLevel()) {
            return "Ship positions require Lieutenant Commander rank or above.";
        }

        clearPosition(position);

        target.setShipPosition(position);
        SecondDawnRP.PROFILE_MANAGER.markDirty(targetUuid);

        ServerPlayerEntity targetPlayer = server != null
                ? server.getPlayerManager().getPlayer(targetUuid) : null;
        if (targetPlayer != null) {
            SecondDawnRP.PROFILE_SERVICE.syncAll(targetPlayer);
        }

        String posName = position.name().replace("_", " ");
        String msg = "[Position] " + target.getDisplayName()
                + " has been designated as " + posName
                + " by " + actor.getName().getString() + ".";

        if (server != null) {
            Text broadcast = Text.literal(msg).formatted(Formatting.GOLD);
            server.getPlayerManager().getPlayerList()
                    .forEach(p -> p.sendMessage(broadcast, false));
        }

        return msg;
    }

    public String clear(ServerPlayerEntity actor, UUID targetUuid) {
        PlayerProfile actorProfile = profileManager.getLoadedProfile(actor.getUuid());
        if (!SecondDawnRP.PERMISSION_SERVICE.canAssignShipPosition(actor, actorProfile)) {
            return "You do not have permission to clear ship positions.";
        }

        PlayerProfile target = profileManager.getLoadedProfile(targetUuid);
        if (target == null) return "Target player is not online.";

        ShipPosition previous = target.getShipPosition();
        if (previous == ShipPosition.NONE) {
            return target.getDisplayName() + " holds no ship position.";
        }

        target.setShipPosition(ShipPosition.NONE);
        SecondDawnRP.PROFILE_MANAGER.markDirty(targetUuid);

        ServerPlayerEntity targetPlayer = server != null
                ? server.getPlayerManager().getPlayer(targetUuid) : null;
        if (targetPlayer != null) {
            SecondDawnRP.PROFILE_SERVICE.syncAll(targetPlayer);
        }

        return target.getDisplayName() + " is no longer "
                + previous.name().replace("_", " ") + ".";
    }

    public boolean hasShipwideConfirmAuthority(ServerPlayerEntity player) {
        PlayerProfile profile = profileManager.getLoadedProfile(player.getUuid());
        if (SecondDawnRP.PERMISSION_SERVICE.canAssignShipPosition(player, profile)) {
            return true;
        }
        if (profile == null) return false;
        if (profile.getRank() == Rank.CAPTAIN) return true;

        ShipPosition pos = profile.getShipPosition();
        return pos == ShipPosition.FIRST_OFFICER || pos == ShipPosition.SECOND_OFFICER;
    }

    private void clearPosition(ShipPosition position) {
        profileManager.getLoadedProfiles().stream()
                .filter(p -> p.getShipPosition() == position)
                .forEach(p -> {
                    p.setShipPosition(ShipPosition.NONE);
                    SecondDawnRP.PROFILE_MANAGER.markDirty(p.getPlayerId());
                });
    }
}