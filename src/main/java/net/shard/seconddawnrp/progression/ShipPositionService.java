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

/**
 * Manages ship-wide command positions (First Officer, Second Officer).
 *
 * Positions are independent of rank — they are role designations assigned by
 * Captain or admin. Both require LT_COMMANDER rank minimum.
 *
 * First and Second Officers can confirm any certification on the ship
 * regardless of division. Losing a position does not affect rank or certs.
 *
 * One First Officer and one Second Officer may be active at a time.
 * Assigning a new person to a position automatically removes it from the
 * current holder.
 */
public class ShipPositionService {

    private final PlayerProfileManager profileManager;
    private MinecraftServer server;

    public ShipPositionService(PlayerProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    /**
     * Assign a ship position to a player.
     * Removes the position from whoever currently holds it.
     *
     * @return result message
     */
    public String assign(ServerPlayerEntity actor, UUID targetUuid,
                         ShipPosition position) {
        if (!canAssign(actor)) {
            return "Only the Captain or an admin (op level 4) can assign ship positions.";
        }
        if (position == ShipPosition.NONE) {
            return "Use /admin position clear [player] to remove a position.";
        }

        PlayerProfile target = profileManager.getLoadedProfile(targetUuid);
        if (target == null) return "Target player is not online.";

        // Rank gate — LT_COMMANDER minimum
        if (target.getRank() == null
                || target.getRank().getAuthorityLevel()
                < Rank.LIEUTENANT_COMMANDER.getAuthorityLevel()) {
            return "Ship positions require Lieutenant Commander rank or above.";
        }

        // Remove from current holder
        clearPosition(position);

        target.setShipPosition(position);
        SecondDawnRP.PROFILE_MANAGER.markDirty(targetUuid);

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

    /**
     * Remove a ship position from a player.
     */
    public String clear(ServerPlayerEntity actor, UUID targetUuid) {
        if (!canAssign(actor)) {
            return "Only the Captain or an admin can clear ship positions.";
        }
        PlayerProfile target = profileManager.getLoadedProfile(targetUuid);
        if (target == null) return "Target player is not online.";

        ShipPosition previous = target.getShipPosition();
        if (previous == ShipPosition.NONE) {
            return target.getDisplayName() + " holds no ship position.";
        }

        target.setShipPosition(ShipPosition.NONE);
        SecondDawnRP.PROFILE_MANAGER.markDirty(targetUuid);

        return target.getDisplayName() + " is no longer " + previous.name().replace("_", " ") + ".";
    }

    /**
     * Returns true if the player holds First or Second Officer position,
     * or is Captain, or has op level 2. Used by cert confirmation checks.
     */
    public boolean hasShipwideConfirmAuthority(ServerPlayerEntity player) {
        if (player.hasPermissionLevel(2)) return true;
        PlayerProfile profile = profileManager.getLoadedProfile(player.getUuid());
        if (profile == null) return false;
        if (profile.getRank() == Rank.CAPTAIN) return true;
        ShipPosition pos = profile.getShipPosition();
        return pos == ShipPosition.FIRST_OFFICER || pos == ShipPosition.SECOND_OFFICER;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void clearPosition(ShipPosition position) {
        profileManager.getLoadedProfiles().stream()
                .filter(p -> p.getShipPosition() == position)
                .forEach(p -> {
                    p.setShipPosition(ShipPosition.NONE);
                    SecondDawnRP.PROFILE_MANAGER.markDirty(p.getPlayerId());
                });
    }

    private boolean canAssign(ServerPlayerEntity actor) {
        if (actor.hasPermissionLevel(4)) return true;
        PlayerProfile profile = profileManager.getLoadedProfile(actor.getUuid());
        return profile != null && profile.getRank() == Rank.CAPTAIN;
    }
}