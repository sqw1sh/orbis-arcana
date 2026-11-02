package com.orbisarcana.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.Level;

/**
 * Guardian Treant of the Thorn Seed (§14.7, milestone 15): a golem of bark and moss that fights for its caster for a
 * few seconds. Golem body and AI; its blows root the target in thorns. Never saved, drops nothing.
 */
public class GuardianTreant extends IronGolem {
    private static final int THORN_SLOW_TICKS = 40;

    public GuardianTreant(EntityType<? extends GuardianTreant> type, Level level) {
        super(type, level);
        setPlayerCreated(true);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, THORN_SLOW_TICKS, 1), this);
        return hit;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOD_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOOD_BREAK;
    }
}
