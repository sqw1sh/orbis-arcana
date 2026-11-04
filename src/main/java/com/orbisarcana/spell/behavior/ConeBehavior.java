package com.orbisarcana.spell.behavior;

import java.util.List;

import com.orbisarcana.core.TalentRules;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.AttunementService;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.Fx;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * {@code cone}: a channelled cone in front of the caster (Tidal Stream, §14.3). Every pulse it damages and
 * pushes enemies and puts out fire. Params: range, angle_deg, damage_per_pulse, knockback; ally_heal (10),
 * final_wave (15, on release). Water talents: Surf (+50% push), Undertow (pushed enemies are slowed).
 */
final class ConeBehavior implements ChannelBehavior {
    static final ConeBehavior INSTANCE = new ConeBehavior();

    private static final int PULSE_TICKS = 5;
    private static final double SURF_MULT = 1.5;
    private static final int UNDERTOW_TICKS = 40;
    private static final double WAVE_PUSH = 1.3;
    private static final double WAVE_DAMAGE_MULT = 3.0;

    @Override
    public void channelTick(CastContext ctx, int elapsed) {
        Vec3 dir = ctx.caster().getLookAngle();
        if (elapsed % 2 == 0) particles(ctx, dir);
        if (elapsed % PULSE_TICKS != 0) return;
        pulse(ctx, dir, (float) ctx.scaled("damage_per_pulse"), ctx.param("knockback"), ctx.param("ally_heal"));
    }

    /** End of the channel: milestone 15 releases a final wave. */
    @Override
    public void release(CastContext ctx) {
        if (ctx.param("final_wave") <= 0) return;
        Vec3 dir = ctx.caster().getLookAngle();
        pulse(ctx, dir, (float) (ctx.scaled("damage_per_pulse") * WAVE_DAMAGE_MULT), WAVE_PUSH, 0);
        Vec3 eye = ctx.caster().getEyePosition();
        for (int i = 1; i <= 6; i++) {
            Vec3 p = eye.add(dir.scale(i));
            Fx.SPLASH.play(ctx.level(), p.x, p.y - 0.3, p.z, 25, 0.6, 0.4, 0.6);
        }
    }

    private static void pulse(CastContext ctx, Vec3 dir, float damage, double knockback, double allyHeal) {
        ServerPlayer caster = ctx.caster();
        boolean surf = AttunementService.hasTalent(caster, Schools.WATER, TalentRules.A1);
        boolean undertow = AttunementService.hasTalent(caster, Schools.WATER, TalentRules.A2);
        double push = knockback * (surf ? SURF_MULT : 1.0);
        Vec3 flat = new Vec3(dir.x, 0, dir.z).normalize();
        for (LivingEntity e : inCone(ctx, dir)) {
            e.clearFire();
            if (e instanceof Player ally) {
                if (allyHeal > 0 && ally.getHealth() < ally.getMaxHealth()) {
                    float missing = ally.getMaxHealth() - ally.getHealth();
                    ally.heal((float) allyHeal);
                    MasteryService.onHeal(caster, ctx.spellId(), ctx.castId(), ally, Math.min((float) allyHeal, missing));
                    EchoService.onHealCast(caster, ctx.castId());
                }
                continue;
            }
            float before = e.getHealth();
            e.hurtServer(ctx.level(), ModDamageTypes.source(e, ctx.spell().school(), null, caster), damage);
            e.push(flat.x * push, 0.15, flat.z * push);
            if (undertow) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, UNDERTOW_TICKS, 1), caster);
            if (e.getHealth() < before) MasteryService.onHit(caster, ctx.spellId(), ctx.castId(), e, !e.isAlive());
        }
        ctx.caster().clearFire();
    }

    static List<LivingEntity> inCone(CastContext ctx, Vec3 dir) {
        ServerPlayer caster = ctx.caster();
        Vec3 eye = caster.getEyePosition();
        double range = ctx.param("range");
        double cosHalf = Math.cos(Math.toRadians(ctx.param("angle_deg") / 2));
        return ctx.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range), e -> {
            if (e == caster || !e.isAlive()) return false;
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            return dist <= range && dist > 1.0E-3 && to.normalize().dot(dir) >= cosHalf;
        });
    }

    private static void particles(CastContext ctx, Vec3 dir) {
        Vec3 eye = ctx.caster().getEyePosition().add(0, -0.25, 0);
        double range = ctx.param("range");
        double spread = Math.tan(Math.toRadians(ctx.param("angle_deg") / 2));
        for (int i = 0; i < 6; i++) {
            double d = 1 + ctx.caster().getRandom().nextDouble() * (range - 1);
            Vec3 p = eye.add(dir.scale(d));
            double r = spread * d;
            Fx.SPLASH.play(ctx.level(), p.x, p.y, p.z, 3, r * 0.4, r * 0.3, r * 0.4);
            Fx.SPLASH.play(ctx.level(), p.x, p.y, p.z, 1, r * 0.3, r * 0.3, r * 0.3);
        }
    }

    private ConeBehavior() {}
}
