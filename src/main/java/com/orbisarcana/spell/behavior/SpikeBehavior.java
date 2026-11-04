package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.spell.Stars;
import com.orbisarcana.spell.Fx;
import com.orbisarcana.spell.Vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code spike}: Star Spike (§14.10), a piercing star beam that leaves a star where it first hits (or where it ends).
 * Params: damage, range; stars_per_hit (5: a second star at the end of the beam), arc_damage (10: the caster's stars
 * arc to each other), collapse_damage (15: a full set of stars collapses).
 */
final class SpikeBehavior {
    /** Signature effects (tools/vfx-effects.cjs): the beam of starlight along the line. */
    private static final Identifier CORE = Vfx.id("star_core"), GLOW = Vfx.id("star_glow");
    private static final double HIT_WIDTH = 0.35;

    static void release(CastContext ctx) {
        ServerPlayer caster = ctx.caster();
        Vec3 from = caster.getEyePosition();
        Vec3 to = from.add(caster.getLookAngle().scale(ctx.param("range")));
        var block = ctx.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        Vec3 end = to;
        List<LivingEntity> hits = new ArrayList<>(ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1),
                e -> e != caster && e.isAlive() && !(e instanceof Player) && e.getBoundingBox().inflate(HIT_WIDTH).clip(from, end).isPresent()));
        hits.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        float damage = (float) ctx.scaled("damage");
        for (LivingEntity e : hits) {
            float before = e.getHealth();
            e.hurtServer(ctx.level(), ModDamageTypes.source(e, ctx.spell().school(), null, caster), damage);
            if (e.getHealth() < before) {
                MasteryService.onHit(caster, ctx.spellId(), ctx.castId(), e, !e.isAlive());
                EchoService.onSpellHit(caster, ctx.spell().school(), e, ctx.castId());
            }
        }
        Vec3 d = end.subtract(from);
        Vec3 start = from.add(0, -0.2, 0);
        Vec3 beam = end.add(0, -0.2, 0).subtract(start);
        if (beam.length() > 0.3) {
            Vfx.play(ctx.level(), CORE, start.add(beam.scale(0.5)), beam, 0, (float) beam.length());
            Vfx.play(ctx.level(), GLOW, start.add(beam.scale(0.5)), beam, 0, (float) beam.length());
        }
        for (double t = 0.5; t < d.length(); t += 1.2) {
            Vec3 p = from.add(d.normalize().scale(t)).add(0, -0.2, 0);
            Fx.TWINKLE.play(ctx.level(), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02);
        }
        float arc = (float) ctx.param("arc_damage");
        float collapse = (float) ctx.scaled("collapse_damage");
        Vec3 first = hits.isEmpty() ? end.subtract(d.normalize().scale(0.3)) : hits.getFirst().getBoundingBox().getCenter();
        if (ctx.ticks("stars_per_hit") >= 2 && !hits.isEmpty()) Stars.place(caster, end.subtract(d.normalize().scale(0.3)), arc, collapse);
        Stars.place(caster, first, arc, collapse);
    }

    private SpikeBehavior() {}
}
