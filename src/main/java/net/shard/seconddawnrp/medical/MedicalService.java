package net.shard.seconddawnrp.medical;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.character.LongTermInjury;
import net.shard.seconddawnrp.character.LongTermInjuryService;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.playerdata.Billet;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;

import java.util.*;

/**
 * Core service for the Phase 8 Medical system.
 */
public class MedicalService {

    private static final Gson GSON = new Gson();

    private final MedicalRepository repository;
    private final MedicalConditionRegistry conditionRegistry;
    private final PlayerProfileManager profileManager;
    private final MedicalTerminalService terminalService;
    private final LongTermInjuryService longTermInjuryService;
    private MinecraftServer server;

    public MedicalService(
            MedicalRepository repository,
            MedicalConditionRegistry conditionRegistry,
            PlayerProfileManager profileManager,
            MedicalTerminalService terminalService,
            LongTermInjuryService longTermInjuryService
    ) {
        this.repository            = repository;
        this.conditionRegistry     = conditionRegistry;
        this.profileManager        = profileManager;
        this.terminalService       = terminalService;
        this.longTermInjuryService = longTermInjuryService;
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── Treatment state ───────────────────────────────────────────────────────

    public record TreatmentState(
            Set<String> completed,
            long lastStepAt,
            long conditionAppliedAt
    ) {}

    // ── Condition application ─────────────────────────────────────────────────

    public ApplyResult applyCondition(
            UUID targetUuid,
            String conditionKey,
            String displayName,
            String description,
            String notes,
            String appliedByUuid
    ) {
        Optional<MedicalConditionTemplate> templateOpt = conditionRegistry.get(conditionKey);
        if (templateOpt.isEmpty()) return ApplyResult.UNKNOWN_CONDITION;

        MedicalConditionTemplate template = templateOpt.get();
        long now = System.currentTimeMillis();

        LongTermInjury condition = LongTermInjury.createCondition(
                targetUuid,
                template.severity().toTier(),
                conditionKey,
                displayName,
                description,
                template.hasAnyRequiresSurgery(),
                appliedByUuid,
                notes
        );

        TreatmentState initialState = new TreatmentState(new LinkedHashSet<>(), 0L, now);
        condition.setTreatmentStepsCompleted(serializeTreatmentState(initialState));

        repository.save(condition);
        longTermInjuryService.registerCondition(condition);

        PlayerProfile profile = profileManager.getLoadedProfile(targetUuid);
        if (profile != null) {
            profile.addMedicalConditionId(condition.getInjuryId());
            profileManager.markDirty(targetUuid);
        }

        if (server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(targetUuid);
            if (player != null) {
                String name = displayName != null ? displayName : template.displayName();
                player.sendMessage(Text.literal(
                                "[Medical] You have been diagnosed with: " + name
                                        + ". Please report to Sickbay.")
                        .formatted(Formatting.RED), false);
            }
            notifyMedicalOfficers(targetUuid, template.displayName());
        }

        return ApplyResult.SUCCESS;
    }

    // ── Treatment step administration ─────────────────────────────────────────

    public TreatmentStepResult attemptTreatmentStep(
            ServerPlayerEntity officer,
            ServerPlayerEntity target,
            ItemStack held
    ) {
        PlayerProfile officerProfile = profileManager.getLoadedProfile(officer.getUuid());
        if (officerProfile == null) return TreatmentStepResult.NO_AUTHORITY;
        if (!isMedicalOfficer(officerProfile)) return TreatmentStepResult.NO_AUTHORITY;

        PlayerProfile targetProfile = profileManager.getLoadedProfile(target.getUuid());
        if (targetProfile == null) return TreatmentStepResult.NO_PATIENT_PROFILE;

        List<String> activeIds = targetProfile.getActiveMedicalConditionIds();
        if (activeIds.isEmpty()) return TreatmentStepResult.NO_CONDITIONS;

        String heldItemId = Registries.ITEM.getId(held.getItem()).toString();

        for (String conditionId : activeIds) {
            Optional<LongTermInjury> condOpt = repository.loadById(conditionId);
            if (condOpt.isEmpty()) continue;
            LongTermInjury condition = condOpt.get();
            if (!condition.isActive()) continue;

            Optional<MedicalConditionTemplate> templateOpt =
                    conditionRegistry.get(condition.getConditionKey());
            if (templateOpt.isEmpty()) continue;

            MedicalConditionTemplate template = templateOpt.get();
            TreatmentState state = parseTreatmentState(
                    condition.getTreatmentStepsCompleted(), condition.getAppliedAtMs());

            for (MedicalConditionTemplate.TreatmentStep step : template.treatmentPlan()) {
                if (state.completed().contains(step.stepKey())) continue;
                if (!step.item().equals(heldItemId)) continue;

                if (step.requiresSurgery() && !officerProfile.hasBillet(Billet.SURGEON)) {
                    officer.sendMessage(Text.literal(
                                    "[Medical] This step requires the Surgeon certification.")
                            .formatted(Formatting.RED), false);
                    return TreatmentStepResult.REQUIRES_SURGERY;
                }

                if (held.getCount() < step.quantity()) {
                    officer.sendMessage(Text.literal(
                                    "[Medical] You need " + step.quantity() + "x "
                                            + step.item() + " for this step.")
                            .formatted(Formatting.YELLOW), false);
                    return TreatmentStepResult.INSUFFICIENT_ITEMS;
                }

                // ── Timing check ──────────────────────────────────────────────
                if (step.hasTiming()) {
                    MedicalConditionTemplate.TimingConstraint timing = step.timing();
                    long now = System.currentTimeMillis();

                    long referenceMs = switch (timing.triggerFrom()) {
                        case PREVIOUS_STEP -> state.lastStepAt() > 0
                                ? state.lastStepAt() : state.conditionAppliedAt();
                        case CONDITION_APPLIED -> state.conditionAppliedAt();
                    };

                    long elapsedMs = now - referenceMs;

                    if (timing.hasMin() && elapsedMs < timing.minSeconds() * 1000L) {
                        long remainSec = (timing.minSeconds() * 1000L - elapsedMs) / 1000;
                        officer.sendMessage(Text.literal(
                                        "[Medical] Too soon — wait " + formatSeconds(remainSec)
                                                + " before: " + step.label())
                                .formatted(Formatting.YELLOW), false);
                        return TreatmentStepResult.TIMING_NOT_YET;
                    }

                    if (timing.hasMax() && elapsedMs > timing.maxSeconds() * 1000L) {
                        String condName = conditionDisplayName(condition, template);
                        officer.sendMessage(Text.literal(
                                        "[Medical] Timing window expired for: " + step.label()
                                                + ". Treatment plan reset.")
                                .formatted(Formatting.RED), false);
                        target.sendMessage(Text.literal(
                                        "[Medical] A treatment step was missed. The procedure must restart.")
                                .formatted(Formatting.RED), false);

                        if (timing.failureEffect() != null)
                            applyVanillaEffect(target, timing.failureEffect());

                        TreatmentState reset = new TreatmentState(
                                new LinkedHashSet<>(), 0L, state.conditionAppliedAt());
                        condition.setTreatmentStepsCompleted(serializeTreatmentState(reset));
                        repository.updateSteps(conditionId, condition.getTreatmentStepsCompleted());

                        notifyGmsTimingFailure(condName, target, step.label());
                        return TreatmentStepResult.TIMING_FAILURE;
                    }
                }
                // ── End timing check ──────────────────────────────────────────

                held.decrement(step.quantity());

                state.completed().add(step.stepKey());
                TreatmentState newState = new TreatmentState(
                        state.completed(), System.currentTimeMillis(), state.conditionAppliedAt());
                String newJson = serializeTreatmentState(newState);
                condition.setTreatmentStepsCompleted(newJson);
                repository.updateSteps(conditionId, newJson);

                String condName = conditionDisplayName(condition, template);
                officer.sendMessage(Text.literal(
                                "[Medical] Step complete: " + step.label() + " (" + condName + ")")
                        .formatted(Formatting.GREEN), false);
                target.sendMessage(Text.literal(
                                "[Medical] Treatment step administered: " + step.label())
                        .formatted(Formatting.AQUA), false);

                boolean allDone = template.treatmentPlan().stream()
                        .allMatch(s -> newState.completed().contains(s.stepKey()));
                if (allDone) {
                    officer.sendMessage(Text.literal(
                                    "[Medical] All treatment steps complete for: " + condName
                                            + ". You may now mark this condition resolved in the Medical PADD.")
                            .formatted(Formatting.GREEN), false);
                    return TreatmentStepResult.ALL_STEPS_COMPLETE;
                }
                return TreatmentStepResult.STEP_COMPLETE;
            }
        }
        return TreatmentStepResult.NO_MATCHING_STEP;
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    public ResolveResult resolveCondition(
            UUID officerUuid,
            String conditionId,
            String resolutionNote
    ) {
        PlayerProfile officer = profileManager.getLoadedProfile(officerUuid);
        if (officer == null || !isMedicalOfficer(officer)) return ResolveResult.NO_AUTHORITY;

        Optional<LongTermInjury> condOpt = repository.loadById(conditionId);
        if (condOpt.isEmpty()) return ResolveResult.NOT_FOUND;
        LongTermInjury condition = condOpt.get();
        if (!condition.isActive()) return ResolveResult.ALREADY_RESOLVED;

        Optional<MedicalConditionTemplate> templateOpt =
                conditionRegistry.get(condition.getConditionKey());
        if (templateOpt.isPresent()) {
            MedicalConditionTemplate template = templateOpt.get();
            TreatmentState state = parseTreatmentState(
                    condition.getTreatmentStepsCompleted(), condition.getAppliedAtMs());
            boolean allDone = template.treatmentPlan().stream()
                    .allMatch(s -> state.completed().contains(s.stepKey()));
            if (!allDone) return ResolveResult.STEPS_INCOMPLETE;
        }

        repository.resolve(conditionId, officerUuid.toString(), resolutionNote);

        UUID patientUuid = condition.getPlayerUuid();
        PlayerProfile patient = profileManager.getLoadedProfile(patientUuid);
        if (patient != null) {
            patient.removeMedicalConditionId(conditionId);
            patient.uncacheMedicalCondition(conditionId);
            profileManager.markDirty(patientUuid);
        }

        longTermInjuryService.clearCondition(patientUuid, conditionId);

        if (server != null) {
            ServerPlayerEntity patientPlayer = server.getPlayerManager().getPlayer(patientUuid);
            if (patientPlayer != null) {
                String condName = conditionDisplayName(condition, templateOpt.orElse(null));
                patientPlayer.sendMessage(Text.literal(
                                "[Medical] Your condition (" + condName + ") has been cleared by "
                                        + officer.getDisplayName() + ".")
                        .formatted(Formatting.GREEN), false);
            }

            // Revive bridge — resolving critical_trauma revives the downed player
            if ("critical_trauma".equals(condition.getConditionKey())) {
                ServerPlayerEntity reviveTarget =
                        server.getPlayerManager().getPlayer(patientUuid);
                if (reviveTarget != null
                        && net.shard.seconddawnrp.SecondDawnRP.DOWNED_SERVICE != null) {
                    net.shard.seconddawnrp.SecondDawnRP.DOWNED_SERVICE.revivePlayer(reviveTarget);
                }
            }
        }
        return ResolveResult.SUCCESS;
    }

    // ── GM force-clear ────────────────────────────────────────────────────────

    public void forceResolve(String conditionId, String gmUuid) {
        Optional<LongTermInjury> condOpt = repository.loadById(conditionId);
        if (condOpt.isEmpty()) return;
        LongTermInjury condition = condOpt.get();
        repository.resolve(conditionId, gmUuid, "GM force-cleared");
        UUID patientUuid = condition.getPlayerUuid();
        PlayerProfile patient = profileManager.getLoadedProfile(patientUuid);
        if (patient != null) {
            patient.removeMedicalConditionId(conditionId);
            patient.uncacheMedicalCondition(conditionId);
            profileManager.markDirty(patientUuid);
        }
        longTermInjuryService.clearCondition(patientUuid, conditionId);
    }

    // ── Patient roster queries ────────────────────────────────────────────────

    public List<PatientSummary> getPatientRoster() {
        List<UUID> visitors = terminalService.getAllVisitors();
        List<PatientSummary> summaries = new ArrayList<>();

        for (UUID uuid : visitors) {
            PlayerProfile profile = profileManager.getLoadedProfile(uuid);
            List<LongTermInjury> active = repository.loadActiveForPlayer(uuid);
            boolean online = server != null
                    && server.getPlayerManager().getPlayer(uuid) != null;
            String characterName = profile != null ? profile.getDisplayName() : uuid.toString();
            String rankDisplay   = profile != null && profile.getRank() != null
                    ? profile.getRank().name() : "UNKNOWN";

            summaries.add(new PatientSummary(
                    uuid, characterName, rankDisplay, online,
                    active.size(),
                    active.stream().anyMatch(c -> {
                        Optional<MedicalConditionTemplate> t =
                                conditionRegistry.get(c.getConditionKey());
                        return t.map(mt -> mt.severity() == MedicalConditionTemplate.Severity.CRITICAL)
                                .orElse(false);
                    })
            ));
        }

        summaries.sort(Comparator
                .comparing(PatientSummary::online).reversed()
                .thenComparing(PatientSummary::hasCritical).reversed()
                .thenComparing(PatientSummary::characterName));
        return summaries;
    }

    public List<ConditionDetail> getActiveConditions(UUID patientUuid) {
        List<LongTermInjury> active = repository.loadActiveForPlayer(patientUuid);
        List<ConditionDetail> details = new ArrayList<>();
        for (LongTermInjury c : active) {
            Optional<MedicalConditionTemplate> templateOpt =
                    conditionRegistry.get(c.getConditionKey());
            TreatmentState state = parseTreatmentState(
                    c.getTreatmentStepsCompleted(), c.getAppliedAtMs());
            details.add(new ConditionDetail(c, templateOpt.orElse(null), state));
        }
        return details;
    }

    public List<LongTermInjury> getHistory(UUID patientUuid) {
        return repository.loadHistoryForPlayer(patientUuid);
    }

    // ── Timing window state query ─────────────────────────────────────────────

    public TimingInfo getStepTimingInfo(LongTermInjury condition,
                                        MedicalConditionTemplate template,
                                        MedicalConditionTemplate.TreatmentStep step) {
        if (!step.hasTiming()) return new TimingInfo(TimingWindowState.NONE, 0, 0);

        MedicalConditionTemplate.TimingConstraint timing = step.timing();
        TreatmentState state = parseTreatmentState(
                condition.getTreatmentStepsCompleted(), condition.getAppliedAtMs());

        long now = System.currentTimeMillis();
        long referenceMs = switch (timing.triggerFrom()) {
            case PREVIOUS_STEP -> state.lastStepAt() > 0
                    ? state.lastStepAt() : state.conditionAppliedAt();
            case CONDITION_APPLIED -> state.conditionAppliedAt();
        };
        long elapsedMs = now - referenceMs;

        if (timing.hasMin() && elapsedMs < timing.minSeconds() * 1000L) {
            long remainSec = (timing.minSeconds() * 1000L - elapsedMs) / 1000;
            return new TimingInfo(TimingWindowState.WAITING, remainSec, 0);
        }
        if (timing.hasMax()) {
            long remainSec = (timing.maxSeconds() * 1000L - elapsedMs) / 1000;
            if (remainSec <= 0) return new TimingInfo(TimingWindowState.EXPIRED, 0, 0);
            return new TimingInfo(TimingWindowState.OPEN, 0, remainSec);
        }
        return new TimingInfo(TimingWindowState.OPEN, 0, -1);
    }

    public enum TimingWindowState { NONE, WAITING, OPEN, EXPIRED }
    public record TimingInfo(TimingWindowState state, long secondsUntilOpen, long secondsUntilExpiry) {}

    // ── Authority checks ──────────────────────────────────────────────────────

    public boolean isMedicalOfficer(PlayerProfile profile) {
        if (profile == null) return false;
        return profile.getDivision() == Division.MEDICAL;
    }

    public boolean isSurgeon(PlayerProfile profile) {
        return profile != null && profile.hasBillet(Billet.SURGEON);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void notifyMedicalOfficers(UUID patientUuid, String conditionName) {
        if (server == null) return;
        PlayerProfile patient = profileManager.getLoadedProfile(patientUuid);
        String patientName = patient != null ? patient.getDisplayName() : patientUuid.toString();
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            PlayerProfile p = profileManager.getLoadedProfile(online.getUuid());
            if (p != null && isMedicalOfficer(p)) {
                online.sendMessage(Text.literal(
                                "[Medical] " + patientName + " has been diagnosed with: "
                                        + conditionName + ". Awaiting treatment.")
                        .formatted(Formatting.YELLOW), false);
            }
        }
    }

    private void notifyGmsTimingFailure(String condName, ServerPlayerEntity target,
                                        String stepLabel) {
        if (server == null) return;
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            if (online.hasPermissionLevel(3)) {
                online.sendMessage(Text.literal(
                                "[Medical] Timing failure on " + target.getName().getString()
                                        + " — " + condName + " / " + stepLabel + ". Plan reset.")
                        .formatted(Formatting.DARK_RED), false);
            }
        }
    }

    private static void applyVanillaEffect(ServerPlayerEntity player, String effectStr) {
        String[] parts = effectStr.split(":");
        if (parts.length < 2) return;
        int amplifier = parts.length > 2 ? parseInt(parts[2], 0) : 0;
        int duration  = parts.length > 3 ? parseInt(parts[3], 200) : 200;
        StatusEffect effect = Registries.STATUS_EFFECT
                .get(Identifier.of(parts[0], parts[1]));
        if (effect == null) return;
        player.addStatusEffect(new StatusEffectInstance(
                Registries.STATUS_EFFECT.getEntry(effect), duration, amplifier, false, true));
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static String conditionDisplayName(LongTermInjury condition,
                                               MedicalConditionTemplate template) {
        if (condition.getDisplayNameOverride() != null) return condition.getDisplayNameOverride();
        if (template != null) return template.displayName();
        return condition.getConditionKey() != null
                ? condition.getConditionKey() : condition.getInjuryId();
    }

    private static String formatSeconds(long totalSeconds) {
        long min = totalSeconds / 60;
        long sec = totalSeconds % 60;
        return min > 0 ? min + "m " + sec + "s" : sec + "s";
    }

    // ── Treatment state serialization ─────────────────────────────────────────

    public static TreatmentState parseTreatmentState(String json, long fallbackAppliedAt) {
        if (json == null || json.isBlank())
            return new TreatmentState(new LinkedHashSet<>(), 0L, fallbackAppliedAt);
        try {
            if (json.trim().startsWith("{")) {
                JsonObject obj = GSON.fromJson(json, JsonObject.class);
                Set<String> completed = new LinkedHashSet<>();
                if (obj.has("completed"))
                    for (JsonElement el : obj.getAsJsonArray("completed"))
                        completed.add(el.getAsString());
                long lastStepAt = obj.has("lastStepAt")
                        ? obj.get("lastStepAt").getAsLong() : 0L;
                long conditionAppliedAt = obj.has("conditionAppliedAt")
                        ? obj.get("conditionAppliedAt").getAsLong() : fallbackAppliedAt;
                return new TreatmentState(completed, lastStepAt, conditionAppliedAt);
            }
            if (json.trim().startsWith("[")) {
                Set<String> completed = new LinkedHashSet<>();
                for (JsonElement el : GSON.fromJson(json, JsonArray.class))
                    completed.add(el.getAsString());
                return new TreatmentState(completed, 0L, fallbackAppliedAt);
            }
        } catch (Exception ignored) {}
        return new TreatmentState(new LinkedHashSet<>(), 0L, fallbackAppliedAt);
    }

    public static String serializeTreatmentState(TreatmentState state) {
        JsonObject obj = new JsonObject();
        JsonArray completed = new JsonArray();
        state.completed().forEach(completed::add);
        obj.add("completed", completed);
        obj.addProperty("lastStepAt", state.lastStepAt());
        obj.addProperty("conditionAppliedAt", state.conditionAppliedAt());
        return GSON.toJson(obj);
    }

    // ── Result enums ──────────────────────────────────────────────────────────

    public enum ApplyResult { SUCCESS, UNKNOWN_CONDITION }

    public enum TreatmentStepResult {
        STEP_COMPLETE, ALL_STEPS_COMPLETE, NO_MATCHING_STEP,
        NO_AUTHORITY, NO_CONDITIONS, NO_PATIENT_PROFILE,
        REQUIRES_SURGERY, INSUFFICIENT_ITEMS,
        TIMING_NOT_YET, TIMING_FAILURE
    }

    public enum ResolveResult {
        SUCCESS, NO_AUTHORITY, NOT_FOUND, ALREADY_RESOLVED, STEPS_INCOMPLETE
    }

    // ── Data carriers ─────────────────────────────────────────────────────────

    public record PatientSummary(
            UUID uuid, String characterName, String rankDisplay,
            boolean online, int activeConditionCount, boolean hasCritical
    ) {}

    public record ConditionDetail(
            LongTermInjury condition,
            MedicalConditionTemplate template,
            TreatmentState state
    ) {
        public boolean isReadyToResolve() {
            if (template == null) return true;
            return template.treatmentPlan().stream()
                    .allMatch(s -> state.completed().contains(s.stepKey()));
        }

        public List<MedicalConditionTemplate.TreatmentStep> pendingSteps() {
            if (template == null) return List.of();
            return template.treatmentPlan().stream()
                    .filter(s -> !state.completed().contains(s.stepKey()))
                    .toList();
        }

        public String displayName() {
            if (condition.getDisplayNameOverride() != null)
                return condition.getDisplayNameOverride();
            if (template != null) return template.displayName();
            return condition.getConditionKey() != null
                    ? condition.getConditionKey() : condition.getInjuryId();
        }

        public String severityColour() {
            if (template != null) return template.severity().colour();
            if (condition.getTier() == null) return "§7";
            return switch (condition.getTier()) {
                case MINOR    -> "§e";
                case MODERATE -> "§6";
                case SEVERE   -> "§c";
            };
        }

        public Set<String> completedSteps() { return state.completed(); }
    }
}