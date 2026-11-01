package com.orbisarcana.spell.behavior;

import java.util.Comparator;

import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.school.TalentEffects;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** {@code heal}: heals the resolved target (or the caster when the spell allows self-cast). */
final class HealBehavior {
    /** Absorption cap for overheal shields (§10.5 uses 4 HP for similar shields). */
    private static final float MAX_SHIELD = 4.0f;
    private static final double BOUNCE_RADIUS = 6.0;

    static void release(CastContext ctx) {
        LivingEntity target = ctx.target() != null ? ctx.target() : ctx.caster();
        float amount = (float) (ctx.scaled("heal") * TalentEffects.healMult(ctx.caster()));
        heal(ctx, target, amount);

        double bounce = ctx.param("bounce_fraction");
        if (bounce > 0) {
            ctx.level().getEntitiesOfClass(Player.class, target.getBoundingBox().inflate(BOUNCE_RADIUS),
                            p -> p != target && p.isAlive() && p.getHealth() < p.getMaxHealth())
                    .stream()
                    .min(Comparator.comparingDouble(p -> p.getHealth() / p.getMaxHealth()))
                    .ifPresent(p -> heal(ctx, p, (float) (amount * bounce)));
        }
    }

    private static void heal(CastContext ctx, LivingEntity target, float amount) {
        float missing = target.getMaxHealth() - target.getHealth();
        target.heal(amount);
        MasteryService.onHeal(ctx.caster(), ctx.spellId(), ctx.castId(), target, Math.min(amount, missing));
        float overheal = amount - missing;
        if (overheal > 0 && ctx.param("overheal_to_shield") > 0) {
            target.setAbsorptionAmount(Math.min(MAX_SHIELD, Math.max(target.getAbsorptionAmount(), overheal)));
        }
        double hot = ctx.param("heal_over_time");
        if (hot > 0) {
            // Regeneration I heals 1 HP per 50 ticks
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (int) (hot * 50), 0), ctx.caster());
        }
        ctx.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + target.getBbHeight() * 0.6,
                target.getZ(), 10, 0.4, 0.4, 0.4, 0.0);
    }

    private HealBehavior() {}
}
