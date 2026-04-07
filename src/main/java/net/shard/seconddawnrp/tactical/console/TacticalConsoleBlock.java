package net.shard.seconddawnrp.tactical.console;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking;

/**
 * Tactical Console Block — Terminal Designator type.
 * Right-click opens the full 4-panel Tactical GUI.
 * Requires officer-level permission (hasPermissionLevel(2) for MVP).
 */
public class TacticalConsoleBlock extends Block {

    public static final MapCodec<TacticalConsoleBlock> CODEC =
            MapCodec.unit(TacticalConsoleBlock::new);

    public TacticalConsoleBlock() {
        this(Settings.create().strength(3.5f).requiresTool());
    }

    public TacticalConsoleBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        // Permission check — full LuckPerms node in Phase 12.1
        if (!sp.hasPermissionLevel(2) && !isCommandDivision(sp)) {
            sp.sendMessage(Text.literal("[Tactical] Access restricted to Command division officers.")
                    .formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        // Find any active encounter to open
        if (SecondDawnRP.TACTICAL_SERVICE == null) {
            sp.sendMessage(Text.literal("[Tactical] Tactical system offline.").formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        var encounters = SecondDawnRP.TACTICAL_SERVICE.getEncounterService().getAllEncounters();
        if (encounters.isEmpty()) {
            sp.sendMessage(Text.literal("[Tactical] No active encounter. Standby mode.")
                    .formatted(Formatting.YELLOW), false);
            // Send standby open packet — client opens screen in standby state
            TacticalNetworking.sendOpenPacket(sp, null);
            return ActionResult.SUCCESS;
        }

        // Open with the first active encounter (in full build, player selects if multiple)
        var encounter = encounters.iterator().next();
        TacticalNetworking.sendOpenPacket(sp, encounter);
        return ActionResult.SUCCESS;
    }

    private boolean isCommandDivision(ServerPlayerEntity player) {
        var profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        if (profile == null || profile.getDivision() == null) return false;
        String div = profile.getDivision().name();
        return div.equals("COMMAND") || div.equals("TACTICAL") || div.equals("SECURITY");
    }
}