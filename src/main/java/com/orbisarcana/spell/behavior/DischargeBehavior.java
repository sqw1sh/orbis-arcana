package com.orbisarcana.spell.behavior;

import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.spell.Arcs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code discharge}: an instant bolt at what the caster aims at (Discharge, §14.6). Params: damage, range;
 * jumps (5: 1, 10: 3) to the nearest enemies within jump_range; shock_ticks (15: each jump paralyses briefly).
 * Lightning A1 "Splitting" adds a jump.
 */
final class DischargeBehavior {
    private static final double JUMP_SHARE = 0.75;

    static void release(CastContext ctx) {
        ServerPlayer caster = ctx.caster();
        ServerLevel level = ctx.level();
        double range = Math.max(1, ctx.param("range"));
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        var block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, end, caster.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable(), eye.distanceToSqr(end));
        Vec3 hand = eye.add(caster.getLookAngle().scale(0.6)).add(0, -0.25, 0);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity first)) {
            Arcs.draw(level, hand, end);
            return;
        }
        Arcs.draw(level, hand, Arcs.body(first));
        float damage = (float) ctx.scaled("damage");
        strike(ctx, caster, first, damage, false);

        int jumps = ctx.ticks("jumps") + com.orbisarcana.school.TalentEffects.extraJumps(caster);
        Set<LivingEntity> hitSet = new HashSet<>();
        hitSet.add(first);
        LivingEntity from = first;
        for (int i = 0; i < jumps; i++) {
            LivingEntity next = nearest(level, caster, from, ctx.param("jump_range") * com.orbisarcana.school.TalentEffects.jumpRangeMult(caster), hitSet);
            if (next == null) break;
            Arcs.draw(level, Arcs.body(from), Arcs.body(next));
            damage *= (float) JUMP_SHARE;
            strike(ctx, caster, next, damage, true);
            hitSet.add(next);
            from = next;
        }
    }

    private static void strike(CastContext ctx, ServerPlayer caster, LivingEntity e, float damage, boolean jump) {
        float before = e.getHealth();
        Arcs.strike(ctx.level(), Arcs.body(e));
        if (!jump) com.orbisarcana.spell.Sfx.impact(ctx.level(), Arcs.body(e), ctx.spell().school(), 1f);
        e.hurtServer(ctx.level(), ModDamageTypes.source(e, ctx.spell().school(), null, caster), damage);
        if (jump && ctx.ticks("shock_ticks") > 0) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ctx.ticks("shock_ticks"), 5), caster);
        if (e.getHealth() < before) {
            MasteryService.onHit(caster, ctx.spellId(), ctx.castId(), e, !e.isAlive());
            EchoService.onSpellHit(caster, ctx.spell().school(), e, ctx.castId());
        }
    }

    private static @Nullable LivingEntity nearest(ServerLevel level, ServerPlayer caster, LivingEntity from, double range, Set<LivingEntity> skip) {
        LivingEntity best = null;
        double bestDist = range * range;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(range),
                e -> e.isAlive() && e != caster && !(e instanceof Player) && !skip.contains(e))) {
            double d = e.distanceToSqr(from);
            if (d <= bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private DischargeBehavior() {}
}
