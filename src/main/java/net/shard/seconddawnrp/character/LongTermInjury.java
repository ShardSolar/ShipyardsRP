package net.shard.seconddawnrp.character;

import java.util.UUID;

/**
 * A single medical condition record attached to a player character.
 *
 * Phase 8.4:
 * - Added temporary effect suppression fields for milk interaction
 */
public class LongTermInjury {

    private final String injuryId;
    private final UUID playerUuid;
    private final LongTermInjuryTier tier;
    private final long appliedAtMs;

    private long expiresAtMs;
    private int sessionsCompleted;
    private long lastTreatmentMs;
    private boolean active;

    private final String conditionKey;
    private String displayNameOverride;
    private String descriptionOverride;
    private final boolean requiresSurgery;
    private String treatmentStepsCompleted;
    private String resolvedBy;
    private String resolutionNote;
    private final boolean isDeathCause;
    private final String appliedBy;
    private String notes;

    // Phase 8.4: temporary suppression state
    private long effectsSuppressedUntilMs;
    private long lastMilkUseMs;

    public LongTermInjury(
            String injuryId,
            UUID playerUuid,
            LongTermInjuryTier tier,
            long appliedAtMs,
            long expiresAtMs,
            int sessionsCompleted,
            long lastTreatmentMs,
            boolean active,
            String conditionKey,
            String displayNameOverride,
            String descriptionOverride,
            boolean requiresSurgery,
            String treatmentStepsCompleted,
            String resolvedBy,
            String resolutionNote,
            boolean isDeathCause,
            String appliedBy,
            String notes,
            long effectsSuppressedUntilMs,
            long lastMilkUseMs
    ) {
        this.injuryId = injuryId;
        this.playerUuid = playerUuid;
        this.tier = tier;
        this.appliedAtMs = appliedAtMs;
        this.expiresAtMs = expiresAtMs;
        this.sessionsCompleted = sessionsCompleted;
        this.lastTreatmentMs = lastTreatmentMs;
        this.active = active;
        this.conditionKey = conditionKey;
        this.displayNameOverride = displayNameOverride;
        this.descriptionOverride = descriptionOverride;
        this.requiresSurgery = requiresSurgery;
        this.treatmentStepsCompleted = treatmentStepsCompleted != null
                ? treatmentStepsCompleted : "[]";
        this.resolvedBy = resolvedBy;
        this.resolutionNote = resolutionNote;
        this.isDeathCause = isDeathCause;
        this.appliedBy = appliedBy;
        this.notes = notes;
        this.effectsSuppressedUntilMs = effectsSuppressedUntilMs;
        this.lastMilkUseMs = lastMilkUseMs;
    }

    public LongTermInjury(
            String injuryId,
            UUID playerUuid,
            LongTermInjuryTier tier,
            long appliedAtMs,
            long expiresAtMs,
            int sessionsCompleted,
            long lastTreatmentMs,
            boolean active,
            String conditionKey,
            String displayNameOverride,
            String descriptionOverride,
            boolean requiresSurgery,
            String treatmentStepsCompleted,
            String resolvedBy,
            String resolutionNote,
            boolean isDeathCause,
            String appliedBy,
            String notes
    ) {
        this(
                injuryId, playerUuid, tier, appliedAtMs, expiresAtMs,
                sessionsCompleted, lastTreatmentMs, active,
                conditionKey, displayNameOverride, descriptionOverride,
                requiresSurgery, treatmentStepsCompleted,
                resolvedBy, resolutionNote, isDeathCause, appliedBy, notes,
                0L, 0L
        );
    }

    public LongTermInjury(
            String injuryId,
            UUID playerUuid,
            LongTermInjuryTier tier,
            long appliedAtMs,
            long expiresAtMs,
            int sessionsCompleted,
            long lastTreatmentMs,
            boolean active
    ) {
        this(
                injuryId, playerUuid, tier, appliedAtMs, expiresAtMs,
                sessionsCompleted, lastTreatmentMs, active,
                null, null, null, false, "[]", null, null, false, null, null,
                0L, 0L
        );
    }

    public static LongTermInjury createNow(UUID playerUuid, LongTermInjuryTier tier) {
        long now = System.currentTimeMillis();
        return new LongTermInjury(
                java.util.UUID.randomUUID().toString(),
                playerUuid,
                tier,
                now,
                now + tier.defaultDurationMs,
                0,
                0L,
                true
        );
    }

    public static LongTermInjury createCondition(
            UUID playerUuid,
            LongTermInjuryTier tier,
            String conditionKey,
            String displayName,
            String description,
            boolean requiresSurg,
            String appliedBy,
            String notes
    ) {
        long now = System.currentTimeMillis();
        return new LongTermInjury(
                java.util.UUID.randomUUID().toString(),
                playerUuid,
                tier,
                now,
                now + tier.defaultDurationMs,
                0,
                0L,
                true,
                conditionKey,
                displayName,
                description,
                requiresSurg,
                "[]",
                null,
                null,
                false,
                appliedBy,
                notes,
                0L,
                0L
        );
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    public void setExpiresAtMs(long expiresAtMs) { this.expiresAtMs = expiresAtMs; }
    public void setSessionsCompleted(int sessions) { this.sessionsCompleted = sessions; }
    public void setLastTreatmentMs(long ts) { this.lastTreatmentMs = ts; }
    public void setActive(boolean active) { this.active = active; }
    public void setDisplayNameOverride(String name) { this.displayNameOverride = name; }
    public void setDescriptionOverride(String desc) { this.descriptionOverride = desc; }
    public void setTreatmentStepsCompleted(String stepsJson) { this.treatmentStepsCompleted = stepsJson; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public void setResolutionNote(String note) { this.resolutionNote = note; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setEffectsSuppressedUntilMs(long ts) { this.effectsSuppressedUntilMs = ts; }
    public void setLastMilkUseMs(long ts) { this.lastMilkUseMs = ts; }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getInjuryId() { return injuryId; }
    public UUID getPlayerUuid() { return playerUuid; }
    public LongTermInjuryTier getTier() { return tier; }
    public long getAppliedAtMs() { return appliedAtMs; }
    public long getExpiresAtMs() { return expiresAtMs; }
    public int getSessionsCompleted() { return sessionsCompleted; }
    public long getLastTreatmentMs() { return lastTreatmentMs; }
    public boolean isActive() { return active; }

    public String getConditionKey() { return conditionKey; }
    public String getDisplayNameOverride() { return displayNameOverride; }
    public String getDescriptionOverride() { return descriptionOverride; }
    public boolean isRequiresSurgery() { return requiresSurgery; }
    public String getTreatmentStepsCompleted() { return treatmentStepsCompleted; }
    public String getResolvedBy() { return resolvedBy; }
    public String getResolutionNote() { return resolutionNote; }
    public boolean isDeathCause() { return isDeathCause; }
    public String getAppliedBy() { return appliedBy; }
    public String getNotes() { return notes; }
    public long getEffectsSuppressedUntilMs() { return effectsSuppressedUntilMs; }
    public long getLastMilkUseMs() { return lastMilkUseMs; }

    public boolean isRegistryBacked() {
        return conditionKey != null && !conditionKey.isBlank();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMs;
    }

    public boolean isTreatmentCooldownOver() {
        return System.currentTimeMillis() >= lastTreatmentMs + 24L * 60 * 60 * 1000;
    }

    public boolean areEffectsSuppressed() {
        return System.currentTimeMillis() < effectsSuppressedUntilMs;
    }
}