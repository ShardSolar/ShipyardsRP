package net.shard.seconddawnrp.dice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.dice.item.RpPaddItem;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.List;

/**
 * Physical Submission Box block.
 * Right-clicking with a signed RP PADD submits it to the review queue.
 */
public class SubmissionBoxBlock extends BlockWithEntity {

    public static final MapCodec<SubmissionBoxBlock> CODEC =
            MapCodec.unit(SubmissionBoxBlock::new);

    public SubmissionBoxBlock() { this(Settings.create()); }
    public SubmissionBoxBlock(Settings settings) { super(settings); }

    @Override
    public MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SubmissionBoxBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        ItemStack held = sp.getMainHandStack();

        if (!(held.getItem() instanceof RpPaddItem)) {
            sp.sendMessage(Text.literal("[Submission Box] Hold a signed RP PADD to submit it.")
                    .formatted(Formatting.GRAY), false);
            return ActionResult.SUCCESS;
        }

        if (!RpPaddItem.isSigned(held)) {
            sp.sendMessage(Text.literal(
                            "[Submission Box] This PADD is not signed yet. "
                                    + "Open your PADD and click Sign first.")
                    .formatted(Formatting.YELLOW), false);
            return ActionResult.SUCCESS;
        }

        List<String> log = RpPaddItem.readLog(held);
        if (log.isEmpty()) {
            sp.sendMessage(Text.literal("[Submission Box] This PADD has no entries.")
                    .formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        // Get character name for the submission label
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(sp.getUuid());
        String characterName = (profile != null && profile.getCharacterName() != null)
                ? profile.getCharacterName() : sp.getName().getString();

        // Save to database via submission service
        SecondDawnRP.RP_PADD_SUBMISSION_SERVICE.submit(sp.getUuid(), characterName, log);

        // Consume the PADD
        held.decrement(1);

        sp.sendMessage(Text.literal("[Submission Box] PADD submitted ("
                        + log.size() + " entries). An officer will review it.")
                .formatted(Formatting.GREEN), false);

        return ActionResult.SUCCESS;
    }

    // ── Block entity ──────────────────────────────────────────────────────────

    public static class SubmissionBoxBlockEntity extends BlockEntity {

        public static BlockEntityType<SubmissionBoxBlockEntity> TYPE;

        public SubmissionBoxBlockEntity(BlockPos pos, BlockState state) {
            super(TYPE, pos, state);
        }
    }
}