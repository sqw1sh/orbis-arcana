package com.orbisarcana.block;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Resonance Altar (§11.3): bind to a school, unbind, switch with a ritual. Right-click opens the altar
 * screen on the client; every action is validated by the server ({@code school.AltarService}).
 */
public class ResonanceAltarBlock extends Block {
    /** Set by the client entrypoint; the common block must not reference client classes. */
    public static Consumer<BlockPos> clientScreenOpener = pos -> {};

    public ResonanceAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) clientScreenOpener.accept(pos);
        return InteractionResult.SUCCESS;
    }
}
