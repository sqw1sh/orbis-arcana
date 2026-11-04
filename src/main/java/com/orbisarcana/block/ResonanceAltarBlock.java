package com.orbisarcana.block;

import java.util.function.Consumer;

import com.orbisarcana.item.SpellScrollItem;
import com.orbisarcana.progression.ScrollService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Resonance Altar (§11.3): bind to a school, unbind, switch with a ritual. Right-click opens the altar
 * screen on the client, right-click with a scroll takes a duplicate apart into ink; every action is validated by the server ({@code school.AltarService}).
 * Drawn by its block entity (GeckoLib model).
 */
public class ResonanceAltarBlock extends Block implements EntityBlock {
    /** Set by the client entrypoint; the common block must not reference client classes. */
    public static Consumer<BlockPos> clientScreenOpener = pos -> {};

    public ResonanceAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResonanceAltarBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** A scroll in hand: take a duplicate apart into Aether Ink (§6.3). Sneaking keeps the scroll's own use (dissolving). */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof SpellScrollItem)) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (player instanceof ServerPlayer sp) ScrollService.dismantle(sp, stack);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) clientScreenOpener.accept(pos);
        return InteractionResult.SUCCESS;
    }
}
