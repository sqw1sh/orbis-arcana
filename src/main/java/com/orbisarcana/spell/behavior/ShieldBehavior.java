package com.orbisarcana.spell.behavior;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.spell.Shields;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code shield}: an absorption shield (Granite Oath, §14.2). Params: shield, duration_ticks; break_impulse
 * (5: a stone impulse when it breaks); knockback_resist (10); ally_target (15: cast on the ally you look at).
 * Absorbed damage counts as Shield mastery XP (§7.2).
 */
final class ShieldBehavior {
    private static final double ALLY_RANGE = 16.0;
    private static final double IMPULSE_RADIUS = 3.0;
    private static final float IMPULSE_DAMAGE = 3.0f;
    private static final double IMPULSE_PUSH = 0.8;
    private static final BlockParticleOption STONE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRANITE.defaultBlockState());

    static void release(CastContext ctx) {
        ServerPlayer caster = ctx.caster();
        LivingEntity target = ctx.param("ally_target") > 0 ? lookedAtAlly(caster) : null;
        if (target == null) target = caster;
        float amount = (float) ctx.scaled("shield");
        int ticks = ctx.ticks("duration_ticks");
        boolean impulse = ctx.param("break_impulse") > 0;
        AttributeModifier kb = ctx.param("knockback_resist") > 0
                ? new AttributeModifier(OrbisArcana.id("granite_oath_" + ctx.castId()), ctx.param("knockback_resist"), AttributeModifier.Operation.ADD_VALUE)
                : null;
        if (kb != null) {
            AttributeInstance attr = target.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (attr != null) attr.addTransientModifier(kb);
        }

        Shields.grant(target, amount, ticks, (holder, absorbed, broken) -> {
            if (kb != null) {
                AttributeInstance attr = holder.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
                if (attr != null) attr.removeModifier(kb.id());
            }
            if (absorbed > 0) MasteryService.onHeal(caster, ctx.spellId(), ctx.castId(), holder, absorbed);
            if (broken && impulse) impulse(ctx, holder, caster);
        });
        com.orbisarcana.school.EchoService.onSupport(caster, amount);
        Vec3 p = target.position();
        ctx.level().sendParticles(STONE, p.x, p.y + 1, p.z, 30, 0.5, 0.8, 0.5, 0.1);
    }

    private static void impulse(CastContext ctx, LivingEntity holder, ServerPlayer caster) {
        for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, holder.getBoundingBox().inflate(IMPULSE_RADIUS),
                e -> e.isAlive() && e != holder && e != caster && !(e instanceof Player))) {
            Vec3 away = e.position().subtract(holder.position()).multiply(1, 0, 1);
            Vec3 dir = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
            e.push(dir.x * IMPULSE_PUSH, 0.3, dir.z * IMPULSE_PUSH);
            e.hurtServer(ctx.level(), ModDamageTypes.source(e, ctx.spell().school(), null, caster), IMPULSE_DAMAGE);
        }
        Vec3 p = holder.position();
        ctx.level().sendParticles(STONE, p.x, p.y + 0.5, p.z, 60, 1.5, 0.4, 1.5, 0.2);
    }

    private static @Nullable LivingEntity lookedAtAlly(ServerPlayer caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(ALLY_RANGE));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, end, caster.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),
                e -> e instanceof Player && e.isAlive() && !e.isSpectator(), ALLY_RANGE * ALLY_RANGE);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private ShieldBehavior() {}
}
