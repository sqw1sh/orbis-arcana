package com.orbisarcana.entity;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.spell.behavior.BurningGround;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.registry.ModEntities;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.Vfx;

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
import net.minecraft.world.phys.Vec3;

/**
 * Gravity-less magic projectile. Visuals are the mod's VFX: a trail attached on spawn and an impact burst.
 * Not saved: spell projectiles are short-lived and discarded on unload.
 */
public class SpellProjectile extends ThrowableProjectile {
    private static final Identifier TRAIL = Vfx.id("fire_trail"), BURST = Vfx.id("fire_burst"), SPARKS = Vfx.id("fire_sparks"),
            SMOKE = Vfx.id("smoke_puff");
    private Identifier school = Schools.FIRE;
    private float damage;
    private int igniteTicks;
    private float explodeRadius;
    private int groundFireTicks;
    private int maxAge = 100;
    private static final double GROUND_FIRE_RADIUS = 1.5;
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
            if (tickCount == 1) Vfx.attach(this, TRAIL, tint(), 1f);
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
        if (groundFireTicks > 0 && getOwner() instanceof ServerPlayer player && spell != null) {
            Vec3 at = direct != null ? direct.position() : hit.getLocation();
            BurningGround.spawn(server, at, GROUND_FIRE_RADIUS, groundFireTicks, player, school, spell, castId);
        }
        impact(server, explodeRadius > 0 ? 1.6f : 1f);
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
            EchoService.onSpellHit(player, school, living, castId);
        }
    }

    /** Area damage around the impact; the directly hit entity was already damaged. */
    private void explode(ServerLevel server, @Nullable Entity direct) {
        AABB box = getBoundingBox().inflate(explodeRadius);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != getOwner() && e != direct && e.distanceTo(this) <= explodeRadius)) {
            hurt(server, e);
        }
    }

    /** Burst of flames, sparks flying off, a puff of smoke (VFX, roadmap V4) and the school's impact sound (V6). */
    private void impact(ServerLevel server, float scale) {
        Vec3 at = position();
        Vec3 back = getDeltaMovement().lengthSqr() > 1.0E-6 ? getDeltaMovement().normalize().scale(-1) : Vec3.ZERO;
        Vfx.play(server, BURST, at, back, tint(), scale);
        Vfx.play(server, SPARKS, at, back, tint(), scale);
        Vfx.play(server, SMOKE, at, Vec3.ZERO, 0, scale);
        server.playSound(null, at.x, at.y, at.z, com.orbisarcana.registry.ModSounds.impact(school), net.minecraft.sounds.SoundSource.PLAYERS,
                0.8f + 0.2f * scale, 0.95f + random.nextFloat() * 0.1f);
    }

    /** Fire keeps the effects' own colours; other schools tint them. */
    private int tint() {
        return school.equals(Schools.FIRE) ? 0 : Schools.color(school);
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
