package net.shard.seconddawnrp.medical;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.character.LongTermInjury;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;
import net.shard.seconddawnrp.terminal.TerminalDesignatorService;

import java.util.*;

/**
 * Handles MEDICAL_TERMINAL interactions.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Register players as medical terminal visitors (gates PADD patient roster)</li>
 *   <li>Show the patient self-view when a player right-clicks a MEDICAL_TERMINAL</li>
 *   <li>Provide visitor list queries for MedicalService and the Medical PADD</li>
 * </ul>
 *
 * <p>The terminal interact hook is wired into {@link TerminalDesignatorService}
 * via the existing {@code MEDICAL_TERMINAL} type stub. When
 * {@code TerminalDesignatorService.handleInteract()} dispatches to
 * {@code MEDICAL_TERMINAL}, it calls {@link #handleTerminalInteract(ServerPlayerEntity)}.
 */
public class MedicalTerminalService {

    private final SqlMedicalTerminalRepository repository;
    private final PlayerProfileManager profileManager;
    private final MedicalRepository medicalRepository;
    private final MedicalConditionRegistry conditionRegistry;

    /**
     * In-memory visitor set for fast "is this player a visitor?" checks
     * without hitting the DB on every PADD open. Populated on server start
     * from the DB.
     */
    private final Set<UUID> visitorCache = new HashSet<>();

    public MedicalTerminalService(
            SqlMedicalTerminalRepository repository,
            PlayerProfileManager profileManager,
            MedicalRepository medicalRepository,
            MedicalConditionRegistry conditionRegistry
    ) {
        this.repository        = repository;
        this.profileManager    = profileManager;
        this.medicalRepository = medicalRepository;
        this.conditionRegistry = conditionRegistry;
    }

    // ── Server start ──────────────────────────────────────────────────────────

    /** Loads visitor cache from DB on server start. */
    public void reload() {
        visitorCache.clear();
        visitorCache.addAll(repository.loadAllVisitors());
        System.out.println("[SecondDawnRP] Medical terminal visitor cache loaded ("
                + visitorCache.size() + " players).");
    }

    // ── Terminal interact ─────────────────────────────────────────────────────

    /**
     * Called by TerminalDesignatorService when a player right-clicks a
     * MEDICAL_TERMINAL block.
     *
     * <p>Registers the player as a visitor (first-time or repeat) and sends
     * them a self-view of their active conditions in chat.
     *
     * @return true — always consumes the interaction
     */
    public boolean handleTerminalInteract(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        long now  = System.currentTimeMillis();

        // Register visit
        boolean firstVisit = !visitorCache.contains(uuid);
        repository.upsertVisitor(uuid, now);
        visitorCache.add(uuid);

        if (firstVisit) {
            player.sendMessage(Text.literal(
                            "═══════════════════════════════════")
                    .formatted(Formatting.DARK_AQUA), false);
            player.sendMessage(Text.literal(
                            "  MEDICAL TERMINAL — PATIENT REGISTRATION")
                    .formatted(Formatting.AQUA, Formatting.BOLD), false);
            player.sendMessage(Text.literal(
                            "  You are now registered in the ship's medical system.")
                    .formatted(Formatting.WHITE), false);
            player.sendMessage(Text.literal(
                            "═══════════════════════════════════")
                    .formatted(Formatting.DARK_AQUA), false);
        }

        // Show patient self-view
        sendPatientView(player);
        return true;
    }

    public void registerVisit(net.minecraft.server.network.ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        long now = System.currentTimeMillis();
        repository.upsertVisitor(uuid, now);
        visitorCache.add(uuid);
    }

    // ── Patient self-view ─────────────────────────────────────────────────────

    /**
     * Sends a formatted condition summary to the player in chat.
     * Read-only — no treatment information shown.
     */
    public void sendPatientView(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        List<LongTermInjury> active = medicalRepository.loadActiveForPlayer(uuid);

        player.sendMessage(Text.literal(
                        "─── Medical Status ─────────────────")
                .formatted(Formatting.DARK_AQUA), false);

        if (active.isEmpty()) {
            player.sendMessage(Text.literal("  No active medical conditions.")
                    .formatted(Formatting.GREEN), false);
        } else {
            for (LongTermInjury condition : active) {
                Optional<MedicalConditionTemplate> templateOpt =
                        conditionRegistry.get(condition.getConditionKey());

                String name = condition.getDisplayNameOverride() != null
                        ? condition.getDisplayNameOverride()
                        : templateOpt.map(MedicalConditionTemplate::displayName)
                        .orElse(condition.getConditionKey() != null
                                ? condition.getConditionKey()
                                : "Unknown Condition");

                String colour = templateOpt
                        .map(t -> t.severity().colour())
                        .orElse("§7");

                // Count completed / total steps
                String stepProgress = "";
                if (templateOpt.isPresent()) {
                    Set<String> completed = parseCompletedSteps(
                            condition.getTreatmentStepsCompleted());
                    int total = templateOpt.get().treatmentPlan().size();
                    int done  = (int) templateOpt.get().treatmentPlan().stream()
                            .filter(s -> completed.contains(s.stepKey()))
                            .count();
                    stepProgress = " §7(" + done + "/" + total + " steps)§r";
                }

                player.sendMessage(Text.literal(
                                "  " + colour + "● §r" + name + stepProgress)
                        .formatted(Formatting.WHITE), false);
            }
        }

        player.sendMessage(Text.literal(
                        "────────────────────────────────────")
                .formatted(Formatting.DARK_AQUA), false);
    }

    // ── Visitor queries ───────────────────────────────────────────────────────

    public boolean isVisitor(UUID playerUuid) {
        return visitorCache.contains(playerUuid);
    }

    /** Returns all visitor UUIDs for MedicalService patient roster building. */
    public List<UUID> getAllVisitors() {
        return List.copyOf(visitorCache);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static Set<String> parseCompletedSteps(String json) {
        Set<String> result = new LinkedHashSet<>();
        if (json == null || json.isBlank() || json.equals("[]")) return result;
        try {
            com.google.gson.JsonArray arr =
                    new com.google.gson.Gson().fromJson(json, com.google.gson.JsonArray.class);
            for (com.google.gson.JsonElement el : arr) result.add(el.getAsString());
        } catch (Exception ignored) {}
        return result;
    }
}