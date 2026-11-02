package com.orbisarcana.block;

import java.util.ArrayList;
import java.util.List;

import com.orbisarcana.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Glyph ring of the Ruined Observatory puzzle (§12): right-click turns it to the next of 4 glyphs. A {@code key} ring
 * cannot be turned and shows the answer. When every ring near a key shows the key's glyph, the observatory seals
 * around break and its guardians wake.
 */
public class GlyphRingBlock extends Block {
    public static final IntegerProperty GLYPH = IntegerProperty.create("glyph", 0, 3);
    public static final BooleanProperty KEY = BooleanProperty.create("key");
    public static final int REACH = 10;
    private static final int GUARDIANS = 3;

    public GlyphRingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GLYPH, 0).setValue(KEY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GLYPH, KEY);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(KEY)) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            server.setBlockAndUpdate(pos, state.setValue(GLYPH, (state.getValue(GLYPH) + 1) % 4));
            server.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.6f, 1.6f);
            if (trySolve(server, pos)) player.sendOverlayMessage(Component.translatable("message.orbisarcana.observatory.solved"));
        }
        return InteractionResult.SUCCESS;
    }

    /** Breaks the seals if every ring near the key matches it. Returns whether the puzzle is solved now. */
    public static boolean trySolve(ServerLevel level, BlockPos near) {
        List<BlockPos> rings = new ArrayList<>();
        BlockPos key = null;
        for (BlockPos p : BlockPos.betweenClosed(near.offset(-REACH, -REACH, -REACH), near.offset(REACH, REACH, REACH))) {
            BlockState s = level.getBlockState(p);
            if (!s.is(ModBlocks.GLYPH_RING.get())) continue;
            if (s.getValue(KEY)) key = p.immutable();
            else rings.add(p.immutable());
        }
        if (key == null || rings.isEmpty()) return false;
        int answer = level.getBlockState(key).getValue(GLYPH);
        for (BlockPos r : rings) {
            if (level.getBlockState(r).getValue(GLYPH) != answer) return false;
        }
        int broken = 0;
        for (BlockPos p : BlockPos.betweenClosed(key.offset(-REACH, -REACH, -REACH), key.offset(REACH, REACH, REACH))) {
            if (level.getBlockState(p).is(ModBlocks.ARCANE_SEAL.get())) {
                level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
                broken++;
            }
        }
        if (broken == 0) return false;
        level.playSound(null, key, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2f, 0.8f);
        wakeGuardians(level, key);
        return true;
    }

    /** "Magic guardians" of v1 are vanilla mobs with a boost (§12). */
    private static void wakeGuardians(ServerLevel level, BlockPos at) {
        for (int i = 0; i < GUARDIANS; i++) {
            Mob mob = (i == GUARDIANS - 1 ? EntityTypes.WITCH : EntityTypes.SKELETON).create(level, EntitySpawnReason.EVENT);
            if (mob == null) continue;
            double a = Math.PI * 2 * i / GUARDIANS;
            mob.snapTo(at.getX() + 0.5 + Math.cos(a) * 2, at.getY() + 1, at.getZ() + 0.5 + Math.sin(a) * 2, (float) Math.toDegrees(a), 0);
            mob.setCustomName(Component.translatable("entity.orbisarcana.observatory_guardian"));
            var health = mob.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) health.setBaseValue(health.getBaseValue() * 2);
            mob.setHealth(mob.getMaxHealth());
            if (mob.getType() == EntityTypes.SKELETON) mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60 * 10, 0));
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
        }
    }
}
