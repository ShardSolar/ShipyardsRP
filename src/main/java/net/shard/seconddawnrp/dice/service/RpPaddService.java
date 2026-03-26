package net.shard.seconddawnrp.dice.service;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.dice.data.RollResult;
import net.shard.seconddawnrp.dice.data.SessionLogEntry;
import net.shard.seconddawnrp.dice.item.RpPaddItem;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active RP PADD recording sessions.
 *
 * <p>A session is "active" while a player has started recording via
 * {@code /rp record start}. Each roll and /rp action in scope is
 * appended to the session log.
 *
 * <p>Sessions end when the player runs {@code /rp record stop},
 * which finalizes the log on the PADD item itself (via NBT).
 */
public class RpPaddService {

    /**
     * Per-player active session.
     */
    public static class ActiveSession {
        public final UUID ownerUuid;
        public final long startedAtMs;
        public final int radiusBlocks;               // 0 = owner only
        public final Set<UUID> additionalPlayers;    // named players regardless of position

        private final List<SessionLogEntry> log = new ArrayList<>();

        public ActiveSession(UUID ownerUuid, int radiusBlocks, Set<UUID> additionalPlayers) {
            this.ownerUuid         = ownerUuid;
            this.startedAtMs       = System.currentTimeMillis();
            this.radiusBlocks      = radiusBlocks;
            this.additionalPlayers = additionalPlayers;
        }

        public void append(SessionLogEntry entry) { log.add(entry); }
        public List<SessionLogEntry> getLog()     { return Collections.unmodifiableList(log); }
        public long offsetMs()                    { return System.currentTimeMillis() - startedAtMs; }
    }

    /** owner UUID → active session */
    private final Map<UUID, ActiveSession> activeSessions = new ConcurrentHashMap<>();

    // ── Session lifecycle ─────────────────────────────────────────────────────

    public void startSession(UUID ownerUuid, int radiusBlocks, Set<UUID> additionalPlayers) {
        activeSessions.put(ownerUuid,
                new ActiveSession(ownerUuid, radiusBlocks, additionalPlayers));
    }

    /**
     * Stop the session, return the log entries for NBT serialization onto the PADD.
     * Returns empty list if no active session.
     */
    public List<SessionLogEntry> stopSession(UUID ownerUuid) {
        ActiveSession session = activeSessions.remove(ownerUuid);
        if (session == null) return List.of();
        return session.getLog();
    }

    public boolean hasActiveSession(UUID ownerUuid) {
        return activeSessions.containsKey(ownerUuid);
    }

    public Optional<ActiveSession> getSession(UUID ownerUuid) {
        return Optional.ofNullable(activeSessions.get(ownerUuid));
    }

    // ── Capture events ────────────────────────────────────────────────────────

    /**
     * Called by {@link RollService} when a player rolls.
     * Appends to every session that should capture this player's roll.
     */
    public void captureRoll(ServerPlayerEntity player, RollResult result) {
        for (ActiveSession session : activeSessions.values()) {
            if (shouldCapture(session, player)) {
                session.append(SessionLogEntry.roll(session.offsetMs(), result.getCharacterName(), result));
            }
        }
    }

    /**
     * Called by {@link RollService} when a player uses /rp.
     */
    public void captureRp(ServerPlayerEntity player, String action) {
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        String name = (profile != null && profile.getCharacterName() != null)
                ? profile.getCharacterName() : player.getName().getString();

        for (ActiveSession session : activeSessions.values()) {
            if (shouldCapture(session, player)) {
                session.append(SessionLogEntry.rp(session.offsetMs(), name, action));
            }
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private boolean shouldCapture(ActiveSession session, ServerPlayerEntity player) {
        // Always capture the session owner
        if (session.ownerUuid.equals(player.getUuid())) return true;

        // Always capture explicitly named players
        if (session.additionalPlayers.contains(player.getUuid())) return true;

        // Capture if player is within radius of the session owner
        if (session.radiusBlocks > 0) {
            ServerPlayerEntity owner = player.getServer() != null
                    ? player.getServer().getPlayerManager().getPlayer(session.ownerUuid) : null;
            if (owner != null && player.getBlockPos().isWithinDistance(
                    owner.getBlockPos(), session.radiusBlocks)) {
                return true;
            }
        }

        return false;
    }
}