package com.orbisarcana.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Temporary ice of the Ice Bastion (§14.5). Placed with a scheduled tick that removes it, so a wall never
 * outlives its spell, even across a restart. Drops nothing.
 */
public class FrostWallBlock extends Block {
    public FrostWallBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }
}
