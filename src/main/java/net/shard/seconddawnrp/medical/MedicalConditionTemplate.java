package net.shard.seconddawnrp.medical;

import java.util.List;
import java.util.Optional;

/**
 * Immutable definition of a medical condition loaded from JSON.
 *
 * Phase 8.4:
 * - Condition supports activeEffects
 * - Condition supports milkInteraction suppression rules
 */
public record MedicalConditionTemplate(
        String key,
        String displayName,
        Severity severity,
        boolean requiresSurgery,
        String description,
        List<ConditionEffect> activeEffects,
        MilkInteraction milkInteraction,
        List<TreatmentStep> treatmentPlan
) {

    public enum Severity {
        ACUTE,
        CHRONIC,
        CRITICAL;

        public String colour() {
            return switch (this) {
                case ACUTE -> "§e";
                case CHRONIC -> "§6";
                case CRITICAL -> "§c";
            };
        }

        public String label() {
            return switch (this) {
                case ACUTE -> "ACUTE";
                case CHRONIC -> "CHRONIC";
                case CRITICAL -> "CRITICAL";
            };
        }

        public net.shard.seconddawnrp.character.LongTermInjuryTier toTier() {
            return switch (this) {
                case ACUTE -> net.shard.seconddawnrp.character.LongTermInjuryTier.MINOR;
                case CHRONIC -> net.shard.seconddawnrp.character.LongTermInjuryTier.MODERATE;
                case CRITICAL -> net.shard.seconddawnrp.character.LongTermInjuryTier.SEVERE;
            };
        }
    }

    /**
     * Condition-owned gameplay effect.
     */
    public record ConditionEffect(
            String effect,
            int amplifier,
            int durationTicks
    ) {}

    /**
     * Controls how milk interacts with this condition.
     *
     * @param allowed                true if milk can temporarily suppress effects
     * @param suppressionSeconds     how long effects stay suppressed
     * @param reapplyCooldownSeconds minimum time before milk can suppress again
     * @param clearOnUse             if true, current effects are removed immediately
     */
    public record MilkInteraction(
            boolean allowed,
            int suppressionSeconds,
            int reapplyCooldownSeconds,
            boolean clearOnUse
    ) {
        public boolean hasSuppression() {
            return allowed && suppressionSeconds > 0;
        }

        public boolean hasCooldown() {
            return allowed && reapplyCooldownSeconds > 0;
        }
    }

    public enum TimingTrigger {
        PREVIOUS_STEP,
        CONDITION_APPLIED
    }

    public record TimingConstraint(
            TimingTrigger triggerFrom,
            Integer minSeconds,
            Integer maxSeconds,
            boolean showTimer,
            String failureEffect
    ) {
        public boolean hasMin() { return minSeconds != null && minSeconds > 0; }
        public boolean hasMax() { return maxSeconds != null && maxSeconds > 0; }
    }

    public record TreatmentStep(
            String stepKey,
            String label,
            String item,
            int quantity,
            boolean requiresSurgery,
            TimingConstraint timing
    ) {
        public TreatmentStep(String stepKey, String label, String item,
                             int quantity, boolean requiresSurgery) {
            this(stepKey, label, item, quantity, requiresSurgery, null);
        }

        public boolean hasTiming() { return timing != null; }
    }

    public boolean hasAnyRequiresSurgery() {
        return treatmentPlan.stream().anyMatch(TreatmentStep::requiresSurgery);
    }

    public boolean hasActiveEffects() {
        return activeEffects != null && !activeEffects.isEmpty();
    }

    public boolean allowsMilkSuppression() {
        return milkInteraction != null
                && milkInteraction.allowed()
                && milkInteraction.hasSuppression();
    }

    public Optional<TreatmentStep> getStep(String stepKey) {
        return treatmentPlan.stream()
                .filter(s -> s.stepKey().equals(stepKey))
                .findFirst();
    }

    public int getStepIndex(String stepKey) {
        for (int i = 0; i < treatmentPlan.size(); i++) {
            if (treatmentPlan.get(i).stepKey().equals(stepKey)) return i;
        }
        return -1;
    }

    public Optional<TreatmentStep> getPreviousStep(String stepKey) {
        int idx = getStepIndex(stepKey);
        if (idx <= 0) return Optional.empty();
        return Optional.of(treatmentPlan.get(idx - 1));
    }
}