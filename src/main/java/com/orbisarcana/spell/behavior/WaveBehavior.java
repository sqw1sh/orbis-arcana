package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.Targeting;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.spell.Fx;
import com.orbisarcana.spell.Vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code wave}: stone spikes running along the ground one block per tick (Stone Spear, Fault Line, §14.2).
 * Starts at the caster (DIRECTIONAL) or at the ground point (GROUND) and runs in the look direction.
 * Params: length, damage, width; waves + wave_delay (extra waves) + extra_wave_share (their share of the damage, 1 when
 * absent); launch; stun_end_radius;
 * seek_enemy (turns towards the nearest enemy); cage_end_ticks (holds enemies at the end).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
final class WaveBehavior {
    private static final double SPEED = 1.0;
    /** Signature effects (tools/vfx-effects.cjs): stone spikes thrusting up, dust thrown along the ground. */
    private static final Identifier SPIKES = Vfx.id("stone_spikes"), DUST = Vfx.id("earth_dust");
    private static final double SEEK_RANGE = 8.0;
    private static final double SEEK_TURN = 0.25;
    private static final int STUN_TICKS = 20;
    private static final double LAUNCH = 0.6;
    private static final List<Wave> WAVES = new ArrayList<>();

    private static final class Wave {
        final ServerLevel level;
        final UUID caster;
        final Identifier spell, school;
        final int castId;
        final double length, width, launch, stunRadius;
        final float damage;
        final boolean seek;
        final int cageTicks;
        final long startTick;
        final Set<UUID> hit = new HashSet<>();
        Vec3 pos, dir;
        double travelled;

        Wave(CastContext ctx, Vec3 origin, Vec3 dir, long startTick, double damageShare) {
            this.level = ctx.level();
            this.caster = ctx.caster().getUUID();
            this.spell = ctx.spellId();
            this.school = ctx.spell().school();
            this.castId = ctx.castId();
            this.length = ctx.param("length");
            this.width = Math.max(0.6, ctx.param("width"));
            this.damage = (float) (ctx.scaled("damage") * damageShare);
            this.launch = ctx.param("launch") > 0 ? LAUNCH : 0;
            this.stunRadius = ctx.param("stun_end_radius");
            this.seek = ctx.param("seek_enemy") > 0;
            this.cageTicks = ctx.ticks("cage_end_ticks");
            this.startTick = startTick;
            this.pos = origin;
            this.dir = dir;
        }
    }

    static void release(CastContext ctx) {
        Vec3 look = ctx.caster().getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        Vec3 dir = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 origin = ctx.spell().targeting() == Targeting.GROUND && ctx.groundPoint() != null
                ? ctx.groundPoint()
                : ctx.caster().position().add(dir);
        int waves = Math.max(1, ctx.ticks("waves"));
        long now = ctx.level().getGameTime();
        // Every wave can hit the same target, so the extra ones deal only a share (a line of spikes, not 5 spears)
        double extraShare = ctx.param("extra_wave_share") > 0 ? ctx.param("extra_wave_share") : 1.0;
        for (int i = 0; i < waves; i++) {
            WAVES.add(new Wave(ctx, origin, dir, now + (long) i * ctx.ticks("wave_delay"), i == 0 ? 1.0 : extraShare));
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || WAVES.isEmpty()) return;
        long now = level.getGameTime();
        WAVES.removeIf(w -> {
            if (w.level != level) return false;
            if (now < w.startTick) return false;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(w.caster);
            step(w, caster);
            if (w.travelled >= w.length) {
                end(w, caster);
                return true;
            }
            return false;
        });
    }

    private static void step(Wave w, ServerPlayer caster) {
        if (w.seek) {
            w.level.getEntitiesOfClass(LivingEntity.class, new AABB(w.pos, w.pos).inflate(SEEK_RANGE),
                            e -> e.isAlive() && e != caster && !w.hit.contains(e.getUUID()))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(w.pos)))
                    .ifPresent(e -> {
                        Vec3 to = new Vec3(e.getX() - w.pos.x, 0, e.getZ() - w.pos.z);
                        if (to.lengthSqr() > 0.01) w.dir = w.dir.add(to.normalize().scale(SEEK_TURN)).normalize();
                    });
        }
        w.pos = w.pos.add(w.dir.scale(SPEED));
        w.travelled += SPEED;
        float half = (float) Math.max(0.3, w.width * 0.5);
        Vfx.play(w.level, SPIKES, w.pos.add(0, 0.7, 0), Vec3.ZERO, 0, 1f, half, 1, half, 1);
        Vfx.play(w.level, DUST, w.pos.add(0, 0.2, 0), Vec3.ZERO, 0, 1f, half, 1, half, 1);
        Fx.DEBRIS.play(w.level, w.pos.x, w.pos.y + 0.3, w.pos.z, 6, w.width * 0.3, 0.3, w.width * 0.3);
        for (LivingEntity e : w.level.getEntitiesOfClass(LivingEntity.class, new AABB(w.pos, w.pos).inflate(w.width, 1.5, w.width),
                e -> e.isAlive() && e != caster && !w.hit.contains(e.getUUID()))) {
            w.hit.add(e.getUUID());
            hurt(w, caster, e, w.damage);
            if (w.launch > 0) e.push(0, w.launch, 0);
        }
    }

    private static void end(Wave w, ServerPlayer caster) {
        double radius = Math.max(w.stunRadius, w.cageTicks > 0 ? 2.0 : 0);
        if (radius <= 0) return;
        for (LivingEntity e : w.level.getEntitiesOfClass(LivingEntity.class, new AABB(w.pos, w.pos).inflate(radius, 2, radius),
                e -> e.isAlive() && e != caster)) {
            int ticks = Math.max(w.stunRadius > 0 ? STUN_TICKS : 0, w.cageTicks);
            // Stun / stone cage: an overwhelming slowness is the portable "cannot move" (players move client-side)
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 9, false, true), caster);
            if (caster != null) MasteryService.onControl(caster, w.spell, w.castId, e);
        }
        Fx.DEBRIS.play(w.level, w.pos.x, w.pos.y + 0.8, w.pos.z, 50, radius * 0.5, 0.8, radius * 0.5);
        // the eruption at the end of the line: a ring of bigger spikes, a wave of dust, the earth's impact
        com.orbisarcana.spell.Sfx.impact(w.level, w.pos, com.orbisarcana.school.Schools.EARTH, 1.2f);
        Vfx.play(w.level, SPIKES, w.pos.add(0, 0.9, 0), Vec3.ZERO, 0, 1.3f, (float) radius * 0.6f, 1, (float) radius * 0.6f, 3);
        Vfx.play(w.level, DUST, w.pos.add(0, 0.2, 0), Vec3.ZERO, 0, 1.2f, (float) radius * 0.5f, 1, (float) radius * 0.5f, 2.5f);
    }

    private static void hurt(Wave w, ServerPlayer caster, LivingEntity e, float damage) {
        float before = e.getHealth();
        e.hurtServer(w.level, ModDamageTypes.source(e, w.school, null, caster), damage);
        if (caster != null && e.getHealth() < before) {
            MasteryService.onHit(caster, w.spell, w.castId, e, !e.isAlive());
            EchoService.onSpellHit(caster, w.school, e, w.castId);
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        WAVES.clear();
    }

    private WaveBehavior() {}
}
