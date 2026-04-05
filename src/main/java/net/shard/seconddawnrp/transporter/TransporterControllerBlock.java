package net.shard.seconddawnrp.transporter;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.playerdata.Certification;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

/**
 * Transporter Controller Block — right-click opens the transporter controller screen.
 * Access gate: Operations division + TRANSPORTER_OPERATOR certification.
 * GMs (permission level 2+) bypass the gate.
 */
public class TransporterControllerBlock extends Block {

    public static final MapCodec<TransporterControllerBlock> CODEC =
            MapCodec.unit(TransporterControllerBlock::new);

    public TransporterControllerBlock(Settings settings) {
        super(settings);
    }

    public TransporterControllerBlock() {
        this(Settings.create().strength(3.5f).requiresTool());
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

        // GM bypass
        if (serverPlayer.hasPermissionLevel(2)) {
            TransporterControllerNetworking.sendOpenPacket(serverPlayer, pos);
            return ActionResult.SUCCESS;
        }

        // Check Operations division + Transporter Operator cert
        PlayerProfile profile = net.shard.seconddawnrp.SecondDawnRP.PROFILE_MANAGER
                .getLoadedProfile(serverPlayer.getUuid());

        boolean isOperations = profile != null
                && profile.getDivision() != null
                && profile.getDivision().name().equals("OPERATIONS");
        boolean hasCert = profile != null
                && profile.hasCertification(Certification.TRANSPORTER_OPERATOR);

        if (!isOperations || !hasCert) {
            serverPlayer.sendMessage(Text.literal(
                            "[Transporter] Access restricted. "
                                    + "Requires Operations division and Transporter Operator certification.")
                    .formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        TransporterControllerNetworking.sendOpenPacket(serverPlayer, pos);
        return ActionResult.SUCCESS;
    }
}