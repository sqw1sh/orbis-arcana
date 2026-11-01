package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.school.TalentEffects;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code ray}: Dawn Ray (§14.8), a channelled beam of light. Every pulse it burns the first enemy on the line (undead
 * take x{@code undead_mult}). Params: damage_per_pulse, range, undead_mult; pierce (5: hits every enemy on the line),
 * ally_heal (10: heals allies the beam crosses), finale (15: undead are blinded, the end of the channel flashes).
 */
final class RayBehavior implements ChannelBehavior {
    static final RayBehavior INSTANCE = new RayBehavior();

    private static final int PULSE_TICKS = 5;
    private static final double HIT_WIDTH = 0.4;
    private static final int BLIND_TICKS = 60;
    private static final double FLASH_RADIUS = 3.0;
    private static final double FLASH_DAMAGE_MULT = 3.0;

    @Override
    public void channelTick(CastContext ctx, int elapsed) {
        Vec3 from = ctx.caster().getEyePosition();
        Vec3 to = end(ctx, from);
        if (elapsed % 2 == 0) particles(ctx, from, to);
        if (elapsed % PULSE_TICKS != 0) return;
        ServerPlayer caster = ctx.caster();
        boolean pierce = ctx.param("pierce") > 0;
        double allyHeal = ctx.param("ally_heal") * TalentEffects.healMult(caster);
        for (LivingEntity e : onLine(ctx, from, to)) {
            if (e instanceof Player ally) {
                if (allyHeal > 0) heal(ctx, ally, (float) allyHeal);
                continue;
            }
            burn(ctx, e, (float) ctx.scaled("damage_per_pulse"));
            if (!pierce) break;
        }
    }

    /** End of the channel: milestone 15 flashes at the end of the beam. */
    @Override
    public void release(CastContext ctx) {
        if (ctx.param("finale") <= 0) return;
        Vec3 from = ctx.caster().getEyePosition();
        Vec3 at = onLine(ctx, from, end(ctx, from)).stream().filter(e -> !(e instanceof Player)).findFirst()
                .map(e -> e.getBoundingBox().getCenter()).orElse(end(ctx, from));
        float damage = (float) (ctx.scaled("damage_per_pulse") * FLASH_DAMAGE_MULT);
        for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(FLASH_RADIUS),
                e -> e.isAlive() && !(e instanceof Player) && e.getBoundingBox().getCenter().distanceTo(at) <= FLASH_RADIUS + e.getBbWidth())) {
            burn(ctx, e, damage);
        }
        ctx.level().sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 60, 1, 1, 1, 0.25);
    }

    private static void burn(CastContext ctx, LivingEntity e, float damage) {
        ServerPlayer caster = ctx.caster();
        boolean undead = TalentEffects.isUndead(e);
        if (undead) damage *= (float) Math.max(1, ctx.param("undead_mult"));
        float before = e.getHealth();
        e.hurtServer(ctx.level(), ModDamageTypes.source(e, ctx.spell().school(), null, caster), damage);
        if (undead && ctx.param("finale") > 0) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0), caster);
            if (e instanceof Mob mob) mob.setTarget(null);
        }
        if (e.getHealth() < before) {
            MasteryService.onHit(caster, ctx.spellId(), ctx.castId(), e, !e.isAlive());
            EchoService.onSpellHit(caster, ctx.spell().school(), e, ctx.castId());
        }
    }

    private static void heal(CastContext ctx, Player ally, float amount) {
        float missing = ally.getMaxHealth() - ally.getHealth();
        if (missing <= 0) return;
        ally.heal(amount);
        float healed = Math.min(amount, missing);
        MasteryService.onHeal(ctx.caster(), ctx.spellId(), ctx.castId(), ally, healed);
        EchoService.onSupport(ctx.caster(), healed);
        ctx.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getY() + 1, ally.getZ(), 3, 0.3, 0.4, 0.3, 0);
    }

    /** Where the beam stops: the first block, or full range. */
    private static Vec3 end(CastContext ctx, Vec3 from) {
        Vec3 to = from.add(ctx.caster().getLookAngle().scale(ctx.param("range")));
        var hit = ctx.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.caster()));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    /** Living things the beam crosses, nearest first. */
    static List<LivingEntity> onLine(CastContext ctx, Vec3 from, Vec3 to) {
        ServerPlayer caster = ctx.caster();
        List<LivingEntity> hits = new ArrayList<>();
        for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(from, to).inflate(1),
                e -> e != caster && e.isAlive())) {
            Optional<Vec3> clip = e.getBoundingBox().inflate(HIT_WIDTH).clip(from, to);
            if (clip.isPresent()) hits.add(e);
        }
        hits.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        return hits;
    }

    private static void particles(CastContext ctx, Vec3 from, Vec3 to) {
        Vec3 start = from.add(0, -0.2, 0);
        Vec3 d = to.subtract(start);
        double len = d.length();
        Vec3 step = d.normalize().scale(0.5);
        Vec3 p = start.add(step);
        for (double t = 0.5; t < len; t += 0.5, p = p.add(step)) {
            ctx.level().sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
        }
    }

    private RayBehavior() {}
}
