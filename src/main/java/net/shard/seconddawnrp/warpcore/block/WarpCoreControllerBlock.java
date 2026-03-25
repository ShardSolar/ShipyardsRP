package net.shard.seconddawnrp.warpcore.block;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.registry.ModItems;
import net.shard.seconddawnrp.warpcore.network.WarpCoreStatusS2CPacket;

import java.util.Optional;

/**
 * The Warp Core Controller block.
 *
 * <p>Contains a {@link WarpCoreControllerBlockEntity} which exposes an
 * {@link team.reborn.energy.api.EnergyStorage} to adjacent TR/EP cables.
 * The reactor fills the buffer each tick; cables pull from it.
 *
 * <p>Right-click behaviour:
 * <ul>
 *   <li>Empty hand / irrelevant item — open Warp Core Monitor screen
 *   <li>Holding fuel rods — load into reactor (main or off-hand)
 *   <li>Holding Warp Core Tool — defer to Item.use() (tool handles registration)
 *   <li>Holding Engineering Pad — defer to Item.use()
 * </ul>
 */
public class WarpCoreControllerBlock extends HorizontalFacingBlock implements BlockEntityProvider {

    public static final com.mojang.serialization.MapCodec<WarpCoreControllerBlock> CODEC =
            createCodec(WarpCoreControllerBlock::new);

    @Override
    public com.mojang.serialization.MapCodec<WarpCoreControllerBlock> getCodec() { return CODEC; }

    public WarpCoreControllerBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(Properties.HORIZONTAL_FACING,
                ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new WarpCoreControllerBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

        ItemStack held = serverPlayer.getMainHandStack();

        String worldKey = world.getRegistryKey().getValue().toString();
        long posLong = pos.asLong();
        Optional<net.shard.seconddawnrp.warpcore.data.WarpCoreEntry> entryOpt =
                SecondDawnRP.WARP_CORE_SERVICE.getByPosition(worldKey, posLong);

        // Defer to Item.use() for tool items
        if (held.isOf(ModItems.WARP_CORE_TOOL)) {
            return ActionResult.PASS;
        }

        // Engineering Pad right-clicked on controller — open pad focused on this core
        if (held.isOf(ModItems.ENGINEERING_PAD) && entryOpt.isPresent()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
                    net.shard.seconddawnrp.degradation.network.OpenEngineeringPadS2CPacket
                            .fromServiceWithCore(SecondDawnRP.DEGRADATION_SERVICE,
                                    entryOpt.get().getEntryId()));
            return ActionResult.SUCCESS;
        }

        if (held.isOf(ModItems.ENGINEERING_PAD)) {
            return ActionResult.PASS; // not a registered controller, let Item.use() handle
        }

        // Fuel rod loading — main hand
        if (held.isOf(ModItems.FUEL_ROD) && entryOpt.isPresent()) {
            int loaded = SecondDawnRP.WARP_CORE_SERVICE.loadFuel(
                    entryOpt.get().getEntryId(), held.getCount());
            held.decrement(loaded);
            serverPlayer.sendMessage(Text.literal("Loaded " + loaded + " fuel rods.")
                    .formatted(Formatting.GREEN), false);
            return ActionResult.SUCCESS;
        }

        // Fuel rod loading — off-hand
        ItemStack offhand = serverPlayer.getOffHandStack();
        if (offhand.isOf(ModItems.FUEL_ROD) && entryOpt.isPresent()) {
            int loaded = SecondDawnRP.WARP_CORE_SERVICE.loadFuel(
                    entryOpt.get().getEntryId(), offhand.getCount());
            offhand.decrement(loaded);
            serverPlayer.sendMessage(Text.literal("Loaded " + loaded + " fuel rods.")
                    .formatted(Formatting.GREEN), false);
            return ActionResult.SUCCESS;
        }

        // Empty hand or anything else — open monitor
        if (entryOpt.isPresent()) {
            ServerPlayNetworking.send(serverPlayer,
                    WarpCoreStatusS2CPacket.fromEntry(entryOpt.get(),
                            SecondDawnRP.WARP_CORE_SERVICE.getCoilHealth(entryOpt.get())));
        } else {
            serverPlayer.sendMessage(
                    Text.literal("Not registered. Sneak + right-click with the Warp Core Tool.")
                            .formatted(Formatting.GRAY), false);
        }
        return ActionResult.SUCCESS;
    }
}