package com.orbisarcana.entity;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.registry.ModEntities;
import com.orbisarcana.school.Schools;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Gravity-less magic projectile. Visuals are server particles for now; a proper renderer comes with S3.
 * Not saved: spell projectiles are short-lived and discarded on unload.
 */
public class SpellProjectile extends ThrowableProjectile {
    private Identifier school = Schools.FIRE;
    private float damage;
    private int igniteTicks;
    private float explodeRadius;
    private int groundFireTicks;
    private int maxAge = 100;
    /** Spell and cast that fired this projectile, for mastery XP. */
    private @Nullable Identifier spell;
    private int castId = -1;

    public SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) {
        super(type, level);
    }

    public static SpellProjectile create(Level level, LivingEntity owner, Identifier school, float damage,
                                         int igniteTicks, float explodeRadius, int groundFireTicks, int maxAge) {
        SpellProjectile p = new SpellProjectile(ModEntities.SPELL_PROJECTILE.get(), level);
        p.setOwner(owner);
        p.setPos(owner.getX(), owner.getEyeY() - 0.15, owner.getZ());
        p.school = school;
        p.damage = damage;
        p.igniteTicks = igniteTicks;
        p.explodeRadius = explodeRadius;
        p.groundFireTicks = groundFireTicks;
        p.maxAge = maxAge;
        return p;
    }

    public SpellProjectile attribute(Identifier spell, int castId) {
        this.spell = spell;
        this.castId = castId;
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            server.sendParticles(particle(), getX(), getY(), getZ(), 3, 0.08, 0.08, 0.08, 0.01);
            if (tickCount > maxAge) discard();
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel server)) return;
        Entity direct = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        if (direct != null) hurt(server, direct);
        if (explodeRadius > 0) explode(server, direct);
        // TODO(S5): ground_fire_ticks as a burning zone without fire blocks (world damage is opt-in, §1.4)
        server.sendParticles(particle(), getX(), getY(), getZ(), 12, 0.2, 0.2, 0.2, 0.05);
        discard();
    }

    private void hurt(ServerLevel server, Entity target) {
        Entity owner = getOwner();
        if (target == owner) return;
        float before = target instanceof LivingEntity living ? living.getHealth() : 0;
        target.hurtServer(server, ModDamageTypes.source(this, school, this, owner), damage);
        if (igniteTicks > 0) target.igniteForTicks(igniteTicks);
        if (owner instanceof ServerPlayer player && spell != null && target instanceof LivingEntity living && living.getHealth() < before) {
            MasteryService.onHit(player, spell, castId, living, !living.isAlive());
        }
    }

    /** Area damage around the impact; the directly hit entity was already damaged. */
    private void explode(ServerLevel server, @Nullable Entity direct) {
        AABB box = getBoundingBox().inflate(explodeRadius);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != getOwner() && e != direct && e.distanceTo(this) <= explodeRadius)) {
            hurt(server, e);
        }
        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
    }

    private ParticleOptions particle() {
        return school.equals(Schools.FIRE) ? ParticleTypes.FLAME : ParticleTypes.END_ROD;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        OrbisArcana.LOGGER.debug("Discarding saved spell projectile {}", getUUID());
        discard();
    }
}
