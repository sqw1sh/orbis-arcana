package com.orbisarcana.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.util.GeckoLibUtil;
import com.orbisarcana.item.ModelItem;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * Shadow Step decoy (§14.9, milestone 5): a shadow of the caster that mobs keep hunting. Blows pass through it, yet it
 * is not invulnerable, because mobs drop targets they cannot hurt. Stands still, fades after {@link #life} ticks,
 * never saved. Drawn by GeckoLib: a hooded shadow that floats in place.
 */
public class ShadowDecoy extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int life = 100;

    public ShadowDecoy(EntityType<? extends ShadowDecoy> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("idle", test -> test.setAndContinue(ModelItem.IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public void setLife(int ticks) {
        this.life = ticks;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        if (tickCount % 5 == 0) server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1, getZ(), 2, 0.25, 0.5, 0.25, 0.01);
        if (tickCount >= life) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
            discard();
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        level.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 6, 0.2, 0.4, 0.2, 0.02);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {}
}
