package net.shard.seconddawnrp.character;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

/**
 * Physical block for the Character Creation Terminal.
 * Right-clicking opens the Character Creation GUI.
 */
public class CharacterCreationTerminalBlock extends BlockWithEntity {

    public static final MapCodec<CharacterCreationTerminalBlock> CODEC =
            MapCodec.unit(CharacterCreationTerminalBlock::new);

    public CharacterCreationTerminalBlock() { this(Settings.create()); }

    public CharacterCreationTerminalBlock(Settings settings) { super(settings); }

    @Override
    public MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CharacterCreationTerminalBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        // Load profile via PROFILE_SERVICE (merged — no CHARACTER_SERVICE)
        PlayerProfile profile = SecondDawnRP.PROFILE_SERVICE.getLoaded(sp.getUuid());
        if (profile == null) profile = SecondDawnRP.PROFILE_SERVICE.getOrLoad(sp);

        OpenCharacterCreationS2CPacket packet =
                OpenCharacterCreationS2CPacket.build(SecondDawnRP.SPECIES_REGISTRY, profile);
        ServerPlayNetworking.send(sp, packet);

        return ActionResult.SUCCESS;
    }

    // ── Block entity ──────────────────────────────────────────────────────────

    public static class CharacterCreationTerminalBlockEntity extends BlockEntity {

        public static BlockEntityType<CharacterCreationTerminalBlockEntity> TYPE;

        public CharacterCreationTerminalBlockEntity(BlockPos pos, BlockState state) {
            super(TYPE, pos, state);
        }
    }
}