package net.shard.seconddawnrp.medical.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.medical.MedicalConditionTemplate;
import net.shard.seconddawnrp.medical.MedicalPadConditionData;
import net.shard.seconddawnrp.medical.MedicalPadPatientData;
import net.shard.seconddawnrp.medical.MedicalPadStepData;
import net.shard.seconddawnrp.medical.MedicalService;
import net.shard.seconddawnrp.playerdata.Billet;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.ArrayList;
import java.util.List;

public class MedicalPadNetworking {

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(
                OpenMedicalPadS2CPacket.ID, OpenMedicalPadS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(
                net.shard.seconddawnrp.medical.network.MedicalPadActionC2SPacket.ID, net.shard.seconddawnrp.medical.network.MedicalPadActionC2SPacket.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(
                net.shard.seconddawnrp.medical.network.MedicalPadActionC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleAction(context.player(), payload))
        );
    }

    // ── Open ─────────────────────────────────────────────────────────────────

    public static void openMedicalPad(ServerPlayerEntity player) {
        MedicalPadOpenData data = buildOpenData(player);
        ServerPlayNetworking.send(player, new OpenMedicalPadS2CPacket(data));
    }

    // ── Action handler ────────────────────────────────────────────────────────

    private static void handleAction(ServerPlayerEntity actor,
                                     net.shard.seconddawnrp.medical.network.MedicalPadActionC2SPacket packet) {
        switch (packet.action()) {
            case "resolve" -> {
                SecondDawnRP.MEDICAL_SERVICE.resolveCondition(
                        actor.getUuid(),
                        packet.conditionId(),
                        packet.stringArg().isBlank() ? null : packet.stringArg()
                );
                openMedicalPad(actor);
            }
            default -> { }
        }
    }

    // ── Data builder ──────────────────────────────────────────────────────────

    public static MedicalPadOpenData buildOpenData(ServerPlayerEntity player) {
        PlayerProfile profile =
                SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        boolean isSurgeon = profile != null && profile.hasBillet(Billet.SURGEON);

        List<MedicalService.PatientSummary> roster =
                SecondDawnRP.MEDICAL_SERVICE.getPatientRoster();

        List<MedicalPadPatientData> patients = new ArrayList<>();
        for (MedicalService.PatientSummary summary : roster) {
            List<MedicalService.ConditionDetail> details =
                    SecondDawnRP.MEDICAL_SERVICE.getActiveConditions(summary.uuid());

            List<MedicalPadConditionData> conditionData = new ArrayList<>();
            for (MedicalService.ConditionDetail detail : details) {
                List<MedicalPadStepData> steps = new ArrayList<>();

                if (detail.template() != null) {
                    for (MedicalConditionTemplate.TreatmentStep step
                            : detail.template().treatmentPlan()) {
                        boolean done = detail.completedSteps().contains(step.stepKey());

                        // Compute window state for PADD colour coding (no countdown shown)
                        MedicalPadStepData.TimingWindowState windowState =
                                MedicalPadStepData.TimingWindowState.NONE;
                        if (!done && step.hasTiming()) {
                            MedicalService.TimingInfo info =
                                    SecondDawnRP.MEDICAL_SERVICE.getStepTimingInfo(
                                            detail.condition(), detail.template(), step);
                            windowState = switch (info.state()) {
                                case WAITING -> MedicalPadStepData.TimingWindowState.WAITING;
                                case OPEN    -> MedicalPadStepData.TimingWindowState.OPEN;
                                case EXPIRED -> MedicalPadStepData.TimingWindowState.EXPIRED;
                                case NONE    -> MedicalPadStepData.TimingWindowState.NONE;
                            };
                        }

                        steps.add(new MedicalPadStepData(
                                step.stepKey(),
                                step.label(),
                                step.item(),
                                step.quantity(),
                                step.requiresSurgery(),
                                done,
                                windowState
                        ));
                    }
                }

                conditionData.add(new MedicalPadConditionData(
                        detail.condition().getInjuryId(),
                        detail.displayName(),
                        detail.severityColour(),
                        detail.template() != null
                                ? detail.template().severity().label() : "UNKNOWN",
                        detail.condition().isRequiresSurgery(),
                        detail.isReadyToResolve(),
                        steps
                ));
            }

            patients.add(new MedicalPadPatientData(
                    summary.uuid().toString(),
                    summary.characterName(),
                    summary.rankDisplay(),
                    summary.online(),
                    conditionData
            ));
        }

        return new MedicalPadOpenData(patients, isSurgeon);
    }
}