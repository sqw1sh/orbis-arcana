package com.orbisarcana.spell;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Freezing (§14.5, Ice Echo "Frost"): a frozen target barely moves for a while; bosses only get a strong slow.
 * Used by the Frost Echo, Frost Pattern and Ice talents.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class Frost {
    private static final int FREEZE_AMPLIFIER = 9;
    private static final int BOSS_AMPLIFIER = 3;
    public static final BlockParticleOption ICE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());

    /** entity -> game tick when the freeze ends. */
    private static final Map<UUID, Long> FROZEN = new HashMap<>();

    public static void freeze(LivingEntity target, int ticks, @Nullable Entity source) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive()) return;
        boolean boss = target.getType().builtInRegistryHolder().is(Tags.EntityTypes.BOSSES);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, boss ? BOSS_AMPLIFIER : FREEZE_AMPLIFIER, false, false), source);
        if (boss) return;
        long now = level.getGameTime();
        if (FROZEN.size() > 256) FROZEN.values().removeIf(until -> until < now);
        FROZEN.merge(target.getUUID(), now + ticks, Math::max);
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + ticks / 2)); // the frosty look
        target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
        level.sendParticles(ICE, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 25,
                target.getBbWidth() / 2, target.getBbHeight() / 3, target.getBbWidth() / 2, 0.05);
    }

    public static boolean isFrozen(LivingEntity target) {
        Long until = FROZEN.get(target.getUUID());
        return until != null && target.level().getGameTime() < until;
    }

    /** Ends a freeze early (shattering). */
    public static void unfreeze(LivingEntity target) {
        if (FROZEN.remove(target.getUUID()) == null) return;
        MobEffectInstance slow = target.getEffect(MobEffects.SLOWNESS);
        if (slow != null && slow.getAmplifier() >= FREEZE_AMPLIFIER) target.removeEffect(MobEffects.SLOWNESS);
        target.setTicksFrozen(0);
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        FROZEN.clear();
    }

    private Frost() {}
}
