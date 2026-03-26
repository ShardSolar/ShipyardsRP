package net.shard.seconddawnrp.dice.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.dice.data.*;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all dice rolling state for the server session.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Execute {@code /roll} — compute result, hold it, notify player</li>
 *   <li>Manage GM broadcast mode (public vs private)</li>
 *   <li>Manage scene DC</li>
 *   <li>Manage session-only scenario templates</li>
 *   <li>Manage group roll sessions</li>
 *   <li>Feed results to active RP PADD sessions</li>
 * </ul>
 */
public class RollService {

    private final RollModifierConfig modifierConfig;
    private final Random rng = new Random();

    /** Held results waiting for GM broadcast. Key = player UUID. */
    private final Map<UUID, RollResult> heldResults = new ConcurrentHashMap<>();

    /** Active group roll sessions. Key = GM UUID. */
    private final Map<UUID, GroupRollSession> groupSessions = new ConcurrentHashMap<>();

    /** Session-only scenario templates. Key = name (lowercase). */
    private final Map<String, RollScenarioTemplate> scenarios = new HashMap<>();

    /** Current DC for the scene. Null = no DC set. */
    private volatile Integer currentDc = null;

    /** Per-player DC overrides. */
    private final Map<UUID, Integer> playerDcOverrides = new ConcurrentHashMap<>();

    /**
     * If true, all rolls auto-broadcast without per-roll GM approval.
     * Toggled by /gm rolls public / private.
     */
    private volatile boolean publicRollMode = false;

    private MinecraftServer server;

    public RollService(RollModifierConfig modifierConfig) {
        this.modifierConfig = modifierConfig;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── /roll ─────────────────────────────────────────────────────────────────

    /**
     * Execute a roll for the given player.
     * Result is held server-side. Player sees their number immediately.
     * Others see nothing until GM broadcasts.
     *
     * @param player the rolling player
     * @param divisionBonus contextual bonus — 0 unless a scenario is active
     */
    public RollResult roll(ServerPlayerEntity player, int divisionBonus) {
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());

        String characterName = (profile != null && profile.getCharacterName() != null)
                ? profile.getCharacterName() : player.getName().getString();

        int naturalRoll = rng.nextInt(20) + 1;

        // Rank bonus
        int rankBonus = profile != null
                ? modifierConfig.getRankBonus(profile.getRank()) : 0;

        // Cert bonuses — check each cert in the config against player's certifications
        Map<String, Integer> certBonuses = new LinkedHashMap<>();
        if (profile != null) {
            modifierConfig.getCertBonuses().forEach((certId, bonus) -> {
                // Cert IDs in config match cert enum names or string IDs
                boolean hasCert = profile.getCertifications().stream()
                        .anyMatch(c -> c.name().equalsIgnoreCase(certId)
                                || c.name().toLowerCase().replace("_", ".")
                                .equals(certId.toLowerCase()));
                if (hasCert) certBonuses.put(certId, bonus);
            });
        }

        // Demerit penalty — Phase 9.5 will provide real demerit data
        // For now, no penalty (demerits don't have roll flags yet)
        int demeritPenalty = 0;

        // DC — per-player override takes precedence over scene DC
        Integer dc = playerDcOverrides.getOrDefault(player.getUuid(), currentDc);

        RollResult result = new RollResult(
                player.getUuid(), characterName,
                naturalRoll, rankBonus, certBonuses, demeritPenalty,
                divisionBonus, dc
        );

        // Store held result (overwrites any previous held roll)
        heldResults.put(player.getUuid(), result);

        // Notify the rolling player immediately
        player.sendMessage(Text.literal(result.toPlayerString())
                .formatted(Formatting.AQUA), false);

        // Flag natural 20/1 to GMs immediately regardless of broadcast mode
        if (result.isNatural20() || result.isNatural1()) {
            notifyGms(result);
        }

        // Auto-broadcast if public mode
        if (publicRollMode) {
            broadcast(result, player.getServerWorld().getPlayers());
        }

        // Feed into group roll session if active
        groupSessions.values().stream()
                .filter(s -> s.expects(player.getUuid()) && !s.hasResult(player.getUuid()))
                .findFirst()
                .ifPresent(session -> {
                    session.addResult(player.getUuid(), result);
                    checkGroupSessionComplete(session);
                });

        // Feed into active RP PADD sessions
        if (SecondDawnRP.RP_PADD_SERVICE != null) {
            SecondDawnRP.RP_PADD_SERVICE.captureRoll(player, result);
        }

        return result;
    }

    // ── /rp ──────────────────────────────────────────────────────────────────

    /**
     * Broadcast an RP action narration to all players.
     * Always visible — not held like rolls.
     * Captured by active RP PADD sessions.
     */
    public void rp(ServerPlayerEntity player, String action) {
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        String name = (profile != null && profile.getCharacterName() != null)
                ? profile.getCharacterName() : player.getName().getString();

        // Bold gold prefix, then italic white action text — visually distinct from chat
        Text msg = Text.literal("** ").formatted(Formatting.BOLD, Formatting.GOLD)
                .append(Text.literal(name + " ").formatted(Formatting.BOLD, Formatting.YELLOW))
                .append(Text.literal(action).formatted(Formatting.ITALIC, Formatting.WHITE))
                .append(Text.literal(" **").formatted(Formatting.BOLD, Formatting.GOLD));

        if (server != null) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(msg, false);
            }
        }

        // Feed into active RP PADD sessions
        if (SecondDawnRP.RP_PADD_SERVICE != null) {
            SecondDawnRP.RP_PADD_SERVICE.captureRp(player, action);
        }
    }

    // ── GM broadcast ──────────────────────────────────────────────────────────

    /**
     * GM manually broadcasts a held result for a specific player.
     * Returns false if no held result exists.
     */
    public boolean gmBroadcast(UUID targetUuid) {
        RollResult result = heldResults.remove(targetUuid);
        if (result == null) return false;
        if (server != null) broadcast(result, server.getPlayerManager().getPlayerList());
        return true;
    }

    public void setPublicMode(boolean pub) { this.publicRollMode = pub; }
    public boolean isPublicMode()          { return publicRollMode; }

    // ── DC ────────────────────────────────────────────────────────────────────

    public void setDc(int dc) {
        this.currentDc = dc;
        playerDcOverrides.clear(); // scene DC clears all per-player overrides
    }

    public void setPlayerDc(UUID playerUuid, int dc) {
        playerDcOverrides.put(playerUuid, dc);
    }

    public void clearDc() {
        currentDc = null;
        playerDcOverrides.clear();
    }

    public Integer getCurrentDc()           { return currentDc; }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    public void createScenario(String name, int dc, net.shard.seconddawnrp.division.Division division,
                               int divisionBonus) {
        scenarios.put(name.toLowerCase(), new RollScenarioTemplate(name, dc, division, divisionBonus));
    }

    /**
     * Call a scenario for a player — sets DC, computes division bonus,
     * prompts player to /roll.
     */
    public boolean callScenario(String name, ServerPlayerEntity target) {
        // Try exact key first, then case-insensitive scan
        RollScenarioTemplate template = scenarios.get(name.toLowerCase());
        if (template == null) {
            template = scenarios.values().stream()
                    .filter(s -> s.getName().equalsIgnoreCase(name))
                    .findFirst().orElse(null);
        }
        if (template == null) return false;

        // Set per-player DC
        setPlayerDc(target.getUuid(), template.getDc());

        // Notify target
        target.sendMessage(Text.literal("[Scenario] " + template.getName()
                        + " — DC " + template.getDc() + ". Roll now with /roll.")
                .formatted(Formatting.GOLD), false);

        return true;
    }

    public Map<String, RollScenarioTemplate> getScenarios() { return scenarios; }

    // ── Group rolls ───────────────────────────────────────────────────────────

    /**
     * Start a group roll session.
     * Each listed player is prompted to /roll.
     */
    public GroupRollSession startGroupRoll(UUID gmUuid, List<ServerPlayerEntity> players) {
        GroupRollSession session = new GroupRollSession(gmUuid,
                players.stream().map(ServerPlayerEntity::getUuid).toList());
        groupSessions.put(gmUuid, session);

        // Prompt each player
        for (ServerPlayerEntity p : players) {
            p.sendMessage(Text.literal("[Group Roll] You have been included in a group roll. "
                            + "Type /roll now. You have 60 seconds.")
                    .formatted(Formatting.GOLD), false);
        }

        return session;
    }

    public Optional<GroupRollSession> getGroupSession(UUID gmUuid) {
        return Optional.ofNullable(groupSessions.get(gmUuid));
    }

    public void removeGroupSession(UUID gmUuid) {
        groupSessions.remove(gmUuid);
    }

    /** Tick — expire stale group sessions. Call from server tick. */
    public void tick(MinecraftServer server, int tick) {
        if (tick % 20 != 0) return; // check once per second
        groupSessions.entrySet().removeIf(e -> {
            if (e.getValue().isExpired()) {
                ServerPlayerEntity gm = server.getPlayerManager().getPlayer(e.getKey());
                if (gm != null) {
                    gm.sendMessage(Text.literal("[Group Roll] Session expired. "
                                    + e.getValue().getResults().size() + "/"
                                    + e.getValue().getExpectedPlayers().size() + " players rolled.")
                            .formatted(Formatting.YELLOW), false);
                    sendGroupResults(gm, e.getValue());
                }
                return true;
            }
            return false;
        });
    }

    // ── Held results ──────────────────────────────────────────────────────────

    public Optional<RollResult> getHeld(UUID playerUuid) {
        return Optional.ofNullable(heldResults.get(playerUuid));
    }

    public Map<UUID, RollResult> getAllHeld() { return Collections.unmodifiableMap(heldResults); }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void broadcast(RollResult result, List<ServerPlayerEntity> targets) {
        Text msg = buildBroadcastText(result);
        for (ServerPlayerEntity p : targets) p.sendMessage(msg, false);
    }

    private Text buildBroadcastText(RollResult result) {
        if (result.isNatural20()) {
            return Text.literal("[ROLL] " + result.getCharacterName()
                            + " rolled a NATURAL 20 — critical success!")
                    .formatted(Formatting.GREEN);
        }
        if (result.isNatural1()) {
            return Text.literal("[ROLL] " + result.getCharacterName()
                            + " rolled a NATURAL 1 — critical fail!")
                    .formatted(Formatting.RED);
        }

        Text base = Text.literal("[ROLL] " + result.getCharacterName()
                        + " rolled ").formatted(Formatting.AQUA)
                .append(Text.literal(String.valueOf(result.getTotal()))
                        .formatted(Formatting.WHITE));

        if (result.getDcAtTime() != null) {
            boolean pass = result.getTotal() >= result.getDcAtTime();
            base = base.copy()
                    .append(Text.literal(" vs DC " + result.getDcAtTime() + " — ")
                            .formatted(Formatting.AQUA))
                    .append(Text.literal(pass ? "PASS" : "FAIL")
                            .formatted(pass ? Formatting.GREEN : Formatting.RED));
        }
        return base;
    }

    private void notifyGms(RollResult result) {
        if (server == null) return;
        String flag = result.isNatural20() ? "NATURAL 20" : "NATURAL 1";
        Text msg = Text.literal("[GM] " + result.getCharacterName() + " rolled a " + flag + "!")
                .formatted(Formatting.GOLD);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.hasPermissionLevel(3)) p.sendMessage(msg, false);
        }
    }

    private void checkGroupSessionComplete(GroupRollSession session) {
        if (!session.isComplete()) return;
        ServerPlayerEntity gm = server != null
                ? server.getPlayerManager().getPlayer(session.getGmUuid()) : null;
        if (gm != null) {
            gm.sendMessage(Text.literal("[Group Roll] All players have rolled.")
                    .formatted(Formatting.GREEN), false);
            sendGroupResults(gm, session);
        }
        groupSessions.remove(session.getGmUuid());
    }

    private void sendGroupResults(ServerPlayerEntity gm, GroupRollSession session) {
        gm.sendMessage(Text.literal("── Group Roll Results ──").formatted(Formatting.GOLD), false);
        session.getResults().forEach((uuid, result) -> {
            gm.sendMessage(Text.literal("  " + result.getCharacterName()
                            + ": " + result.getTotal()
                            + (result.isNatural20() ? " [NAT 20]" : "")
                            + (result.isNatural1()  ? " [NAT 1]"  : ""))
                    .formatted(Formatting.WHITE), false);
        });
        gm.sendMessage(Text.literal("Highest: " + session.highest()
                        + "  Average: " + session.average()
                        + "  Sum: " + session.sum())
                .formatted(Formatting.AQUA), false);

        // Flag nat 20/1
        session.getNatural20s().forEach(r ->
                gm.sendMessage(Text.literal("[NAT 20] " + r.getCharacterName())
                        .formatted(Formatting.GREEN), false));
        session.getNatural1s().forEach(r ->
                gm.sendMessage(Text.literal("[NAT 1] " + r.getCharacterName())
                        .formatted(Formatting.RED), false));
    }
}