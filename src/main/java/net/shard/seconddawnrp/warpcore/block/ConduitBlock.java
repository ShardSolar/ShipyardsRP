package net.shard.seconddawnrp.warpcore.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * Conduit block — rotatable pipe that aligns to the axis of placement.
 * Extends PillarBlock to get the axis property and rotation handling for free.
 */
public class ConduitBlock extends PillarBlock {

    public ConduitBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(AXIS, Direction.Axis.Y));
    }
}