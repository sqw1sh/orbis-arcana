package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.spell.Fx;
import com.orbisarcana.spell.Vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code vortex}: a damaging whirl on the ground (Ash Spiral, §14.1). Server object, not an entity (§19.1).
 * Params: radius, duration_ticks, damage_per_pulse; pull (milestone 5), follow_aim (10), final_pillar (15).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
final class VortexBehavior {
    /** Volumetric effects: spiral arms of flame, a ring of fire on the ground, the column of fire. */
    private static final Identifier VORTEX = Vfx.id("ash_vortex"), FLOOR = Vfx.id("ash_floor"), COLUMN = Vfx.id("fire_column");
    /** Signature effects (tools/vfx-effects.cjs): the ring of flame, the whirling ash, the pillar of fire. */
    private static final Identifier RING = Vfx.id("ash_ring"), ASH = Vfx.id("ash_flakes"), PILLAR = Vfx.id("fire_pillar");
    private static final int PULSE_TICKS = 10;
    private static final double PULL_STRENGTH = 0.18;
    private static final double FOLLOW_SPEED = 0.08;
    private static final double FOLLOW_RANGE = 24;
    private static final double PILLAR_RADIUS = 1.5;
    private static final double PILLAR_MULT = 4.0; // pillar = two pulses' worth, doubled (§14.1 "x2 in the centre")
    private static final List<Vortex> VORTICES = new ArrayList<>();

    private static final class Vortex {
        final ServerLevel level;
        final UUID caster;
        final Identifier spell, school;
        final int castId;
        final double radius;
        final float damage;
        final boolean pull, follow, pillar;
        final long endTick;
        Vec3 center;

        Vortex(CastContext ctx, Vec3 center) {
            this.level = ctx.level();
            this.caster = ctx.caster().getUUID();
            this.spell = ctx.spellId();
            this.school = ctx.spell().school();
            this.castId = ctx.castId();
            this.radius = ctx.param("radius");
            this.damage = (float) ctx.scaled("damage_per_pulse");
            this.pull = ctx.param("pull") > 0;
            this.follow = ctx.param("follow_aim") > 0;
            this.pillar = ctx.param("final_pillar") > 0;
            this.endTick = ctx.level().getGameTime() + ctx.ticks("duration_ticks");
            this.center = center;
        }
    }

    static void release(CastContext ctx) {
        Vec3 point = ctx.groundPoint() != null ? ctx.groundPoint() : ctx.caster().position();
        VORTICES.add(new Vortex(ctx, point));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || VORTICES.isEmpty()) return;
        long now = level.getGameTime();
        VORTICES.removeIf(v -> {
            if (v.level != level) return false;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(v.caster);
            if (now >= v.endTick) {
                if (v.pillar) pillar(v, caster);
                return true;
            }
            tick(v, caster, now);
            return false;
        });
    }

    private static void tick(Vortex v, ServerPlayer caster, long now) {
        if (v.follow && caster != null) {
            Vec3 eye = caster.getEyePosition();
            var hit = v.level.clip(new ClipContext(eye, eye.add(caster.getLookAngle().scale(FOLLOW_RANGE)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 to = hit.getLocation().subtract(v.center);
                if (to.length() > FOLLOW_SPEED) to = to.normalize().scale(FOLLOW_SPEED);
                v.center = v.center.add(to);
            }
        }
        List<LivingEntity> inside = targets(v, caster, v.radius);
        if (v.pull) {
            for (LivingEntity e : inside) {
                Vec3 toCenter = v.center.subtract(e.position()).multiply(1, 0, 1);
                if (toCenter.lengthSqr() > 0.25) e.push(toCenter.normalize().scale(PULL_STRENGTH));
            }
        }
        if (now % PULSE_TICKS == 0) {
            for (LivingEntity e : inside) hurt(v, caster, e, v.damage);
        }
        // the spiral and the fire on the ground, renewed every second (they follow the vortex when it moves)
        if (now % 20 == 0) {
            Vfx.mesh(v.level, VORTEX, v.center, Vec3.ZERO, 0, (float) v.radius, 1.25f);
            Vfx.mesh(v.level, FLOOR, v.center.add(0, 0.05, 0), Vec3.ZERO, 0, (float) v.radius, 1.25f);
        }
        if (now % 2 == 0) {
            float r = (float) v.radius;
            Vfx.play(v.level, RING, v.center.add(0, 0.1, 0), Vec3.ZERO, 0, 1f, r, 1, r, 1);
            Vfx.play(v.level, ASH, v.center.add(0, 0.2, 0), Vec3.ZERO, 0, 1f, r * 0.8f, 1, r * 0.8f, 1);
            // one arm of the spiral, turning with time
            double t = now * 0.4;
            for (double d = 0.4; d <= v.radius; d += 0.9) {
                Fx.FLAME.play(v.level, v.center.x + Math.cos(t + d * 0.9) * d, v.center.y + 0.15 + d * 0.25, v.center.z + Math.sin(t + d * 0.9) * d, 1, 0, 0.02, 0);
            }
        }
    }

    private static void pillar(Vortex v, ServerPlayer caster) {
        for (LivingEntity e : targets(v, caster, PILLAR_RADIUS)) hurt(v, caster, e, (float) (v.damage * PILLAR_MULT));
        Vfx.play(v.level, PILLAR, v.center.add(0, 0.3, 0), Vec3.ZERO, 0, 1f);
        Vfx.play(v.level, COLUMN, v.center, Vec3.ZERO, 0, 1f);
        Fx.FLAME.play(v.level, v.center.x, v.center.y + 0.5, v.center.z, 25, 0.4, 1.2, 0.4);
        com.orbisarcana.spell.Sfx.impact(v.level, v.center, com.orbisarcana.school.Schools.FIRE, 1f);
    }

    private static List<LivingEntity> targets(Vortex v, ServerPlayer caster, double radius) {
        return v.level.getEntitiesOfClass(LivingEntity.class, new AABB(v.center, v.center).inflate(radius, 2, radius),
                e -> e.isAlive() && e != caster && e.position().distanceToSqr(v.center.x, e.getY(), v.center.z) <= radius * radius);
    }

    private static void hurt(Vortex v, ServerPlayer caster, LivingEntity e, float damage) {
        float before = e.getHealth();
        e.hurtServer(v.level, ModDamageTypes.source(e, v.school, null, caster), damage);
        if (caster != null && e.getHealth() < before) {
            MasteryService.onHit(caster, v.spell, v.castId, e, !e.isAlive());
            EchoService.onSpellHit(caster, v.school, e, v.castId);
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        VORTICES.clear();
    }

    private VortexBehavior() {}
}
