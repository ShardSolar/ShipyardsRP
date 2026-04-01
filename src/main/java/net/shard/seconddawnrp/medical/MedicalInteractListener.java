package net.shard.seconddawnrp.medical;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.registry.ModItems;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intercepts right-clicks on players for:
 * 1. Gurney attachment
 * 2. Medical treatment step item administration
 */
public class MedicalInteractListener {

    private static final Map<UUID, Long> INTERACT_COOLDOWN = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 800L;

    public void register() {
        UseEntityCallback.EVENT.register(this::onUseEntity);
    }

    private ActionResult onUseEntity(PlayerEntity player, World world,
                                     Hand hand,
                                     net.minecraft.entity.Entity entity,
                                     net.minecraft.util.hit.EntityHitResult hitResult) {
        if (world.isClient()) return ActionResult.PASS;
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity officer)) return ActionResult.PASS;
        if (!(entity instanceof ServerPlayerEntity target)) return ActionResult.PASS;
        if (officer.getUuid().equals(target.getUuid())) return ActionResult.PASS;

        ItemStack held = officer.getMainHandStack();
        if (held.isEmpty()) return ActionResult.PASS;

        var officerProfile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(officer.getUuid());
        if (!SecondDawnRP.PERMISSION_SERVICE.canUseMedicalActions(officer, officerProfile)) {
            return ActionResult.PASS;
        }

        long now = System.currentTimeMillis();
        Long last = INTERACT_COOLDOWN.get(officer.getUuid());
        if (last != null && now - last < COOLDOWN_MS) {
            return held.getItem() == ModItems.GURNEY
                    ? ActionResult.SUCCESS
                    : ActionResult.PASS;
        }
        INTERACT_COOLDOWN.put(officer.getUuid(), now);

        if (held.getItem() == ModItems.GURNEY) {
            if (SecondDawnRP.GURNEY_SERVICE != null) {
                boolean attached = SecondDawnRP.GURNEY_SERVICE.attach(officer, target);
                if (attached) {
                    GurneyItem.LAST_ATTACH_MS.put(officer.getUuid(), now);
                }
            }
            return ActionResult.SUCCESS;
        }

        MedicalService.TreatmentStepResult result =
                SecondDawnRP.MEDICAL_SERVICE.attemptTreatmentStep(officer, target, held);

        return switch (result) {
            case STEP_COMPLETE,
                 ALL_STEPS_COMPLETE,
                 REQUIRES_SURGERY,
                 INSUFFICIENT_ITEMS,
                 TIMING_FAILURE,
                 TIMING_NOT_YET -> ActionResult.SUCCESS;
            case NO_MATCHING_STEP,
                 NO_CONDITIONS,
                 NO_PATIENT_PROFILE,
                 NO_AUTHORITY -> ActionResult.PASS;
        };
    }
}