package com.orbisarcana.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.orbisarcana.item.ModelItem;

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
 * few seconds. Golem body and AI; its blows root the target in thorns. Never saved, drops nothing. Drawn by GeckoLib:
 * idle, walk and a two-handed slam while the golem attack animation runs.
 */
public class GuardianTreant extends IronGolem implements GeoEntity {
    private static final int THORN_SLOW_TICKS = 40;
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.walk"), ATTACK = RawAnimation.begin().thenPlay("animation.attack");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GuardianTreant(EntityType<? extends GuardianTreant> type, Level level) {
        super(type, level);
        setPlayerCreated(true);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("main", 3, test -> {
            if (getAttackAnimationTick() > 0) return test.setAndContinue(ATTACK);
            return test.setAndContinue(test.isMoving() ? WALK : ModelItem.IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
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
