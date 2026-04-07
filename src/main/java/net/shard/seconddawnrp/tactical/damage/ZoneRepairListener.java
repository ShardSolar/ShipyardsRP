package net.shard.seconddawnrp.tactical.damage;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tactical.data.ShipState;
import net.shard.seconddawnrp.tactical.service.EncounterService;

/**
 * Listens for player right-click interactions on damaged zone blocks.
 * When an Engineering player interacts with a block that is part of a damaged zone,
 * they can initiate repair using materials from their inventory.
 *
 * Required item for repair: configured per ship class (MVP: any planks/stone brick).
 */
public class ZoneRepairListener {

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();

            // Find if this block is part of a damaged zone on any active ship
            if (SecondDawnRP.TACTICAL_SERVICE == null) return ActionResult.PASS;
            EncounterService es = SecondDawnRP.TACTICAL_SERVICE.getEncounterService();

            for (var encounter : es.getAllEncounters()) {
                for (ShipState ship : encounter.getAllShips()) {
                    String zoneId = findZoneForBlock(ship.getShipId(), pos);
                    if (zoneId == null) continue;

                    // Check if zone is actually damaged
                    if (!SecondDawnRP.TACTICAL_SERVICE
                            .getHullDamageService().isZoneDamaged(ship.getShipId(), zoneId)) {
                        continue;
                    }

                    // Engineering division check (or GM bypass)
                    if (!sp.hasPermissionLevel(2) && !isEngineering(sp)) {
                        sp.sendMessage(Text.literal(
                                        "[Tactical] Engineering division required to repair zones.")
                                .formatted(Formatting.RED), false);
                        return ActionResult.FAIL;
                    }

                    // Check repair materials (MVP: requires 4 stone bricks in hand)
                    var stack = sp.getMainHandStack();
                    if (stack.getItem() != net.minecraft.item.Items.STONE_BRICKS
                            || stack.getCount() < 4) {
                        sp.sendMessage(Text.literal(
                                        "[Tactical] Repair requires 4 Stone Bricks. Zone: " + zoneId)
                                .formatted(Formatting.YELLOW), false);
                        return ActionResult.SUCCESS; // consume click, don't interact
                    }

                    // Consume materials and repair
                    stack.decrement(4);

                    // Restore physical blocks
                    DamageModelMapper.restoreZone(ship.getShipId(), zoneId, (ServerWorld) world);

                    // Clear zone damage state
                    SecondDawnRP.TACTICAL_SERVICE
                            .getHullDamageService().repairZone(ship, zoneId);

                    encounter.log("[REPAIR] Zone " + zoneId + " repaired by "
                            + sp.getName().getString());

                    sp.sendMessage(Text.literal(
                                    "[Tactical] Zone " + zoneId + " repaired successfully.")
                            .formatted(Formatting.GREEN), false);

                    return ActionResult.SUCCESS;
                }
            }

            return ActionResult.PASS;
        });
    }

    private static String findZoneForBlock(String shipId, BlockPos pos) {
        long encoded = pos.asLong();
        for (String zoneId : DamageModelMapper.getZonesForShip(shipId)) {
            for (BlockPos rp : DamageModelMapper.getRealBlocks(shipId, zoneId)) {
                if (rp.asLong() == encoded) return zoneId;
            }
        }
        return null;
    }

    private static boolean isEngineering(ServerPlayerEntity player) {
        var profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        if (profile == null || profile.getDivision() == null) return false;
        String div = profile.getDivision().name();
        return div.equals("OPERATIONS") || div.equals("ENGINEERING");
    }
}