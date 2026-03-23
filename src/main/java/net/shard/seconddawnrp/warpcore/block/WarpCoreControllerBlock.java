package net.shard.seconddawnrp.warpcore.block;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.warpcore.network.WarpCoreStatusS2CPacket;

/**
 * The Warp Core Controller block.
 *
 * <p>This is the only warp core block with logic — all other warp core
 * blocks are purely decorative. The controller:
 * <ul>
 *   <li>Has a {@code facing} property so the screen face always faces the player on placement
 *   <li>Opens the Warp Core Monitor screen when right-clicked
 * </ul>
 *
 * <p>The actual reactor logic lives in {@link net.shard.seconddawnrp.warpcore.service.WarpCoreService}.
 * The block just triggers the screen open packet.
 */
public class WarpCoreControllerBlock extends Block {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public WarpCoreControllerBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH));
    }

    // ── Facing placement ──────────────────────────────────────────────────────

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // Face toward the player who placed it
        return getDefaultState().with(FACING,
                ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    // ── Right-click — open monitor screen ─────────────────────────────────────

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

        // Send current status snapshot to client — client opens the screen
        ServerPlayNetworking.send(serverPlayer,
                WarpCoreStatusS2CPacket.fromService(SecondDawnRP.WARP_CORE_SERVICE));

        return ActionResult.SUCCESS;
    }
}