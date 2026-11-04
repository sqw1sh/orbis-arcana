package com.orbisarcana.spell.behavior;

import com.orbisarcana.core.TalentRules;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.school.AttunementService;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.CastService;
import com.orbisarcana.spell.Fx;
import com.orbisarcana.spell.Vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code dash}: an impulse in the movement direction (look direction when standing still).
 * Velocity is tuned so the travelled distance roughly matches {@code distance} on flat ground.
 */
final class DashBehavior {
    /** Volumetric effects: the ribbon left behind the caster. */
    private static final Identifier TRAIL = Vfx.id("dash_trail");
    /** Signature effects (tools/vfx-effects.cjs): a stream of flame or wind that follows the caster for a moment. */
    private static final Identifier FLAMES = Vfx.id("dash_flames"), WIND = Vfx.id("dash_wind");
    /** Horizontal speed per block of distance, compensating ground friction. */
    private static final double SPEED_PER_BLOCK = 0.16;
    private static final double ALLY_RADIUS = 2.5;
    private static final int TRAIL_PATCHES = 3;
    private static final int PATH_IGNITE_TICKS = 80;
    private static final double PATH_PUSH = 0.8;
    private static final int HOVER_TICKS = 60;

    static void release(CastContext ctx) {
        var player = ctx.caster();
        Vec3 move = player.getKnownMovement().multiply(1, 0, 1);
        Vec3 dir = move.lengthSqr() > 1.0E-4 ? move.normalize() : player.getLookAngle().multiply(1, 0, 1).normalize();
        // Air Echo "Impulse": combat movement charge makes the dash longer
        double distance = ctx.scaled("distance") * EchoService.consumeImpulse(player);
        double speed = distance * SPEED_PER_BLOCK;
        Vec3 start = player.position();

        player.setDeltaMovement(dir.x * speed, Math.max(player.getDeltaMovement().y, 0.25), dir.z * speed);
        player.syncVelocity = true;
        boolean fire = ctx.spell().school().equals(Schools.FIRE);
        Vfx.attach(player, fire ? FLAMES : WIND, 0, 1f);
        Vfx.attach(player, TRAIL, fire ? 0xFF8A3C : 0, 1f);
        (fire ? Fx.FLAME : Fx.CLOUD).play(ctx.level(), player.getX(), player.getY() + 0.5, player.getZ(), 15, 0.3, 0.3, 0.3);

        MasteryService.onMove(player, ctx.spellId(), distance);
        if (AttunementService.hasTalent(player, Schools.AIR, TalentRules.A2)) BladeBehavior.stormTrail(player, start, start.add(dir.scale(distance)));
        if (AttunementService.hasTalent(player, Schools.AIR, TalentRules.B1)) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, HOVER_TICKS, 0), player);
        }

        int noFall = ctx.ticks("no_fall_ticks");
        if (noFall > 0) CastService.grantNoFall(player, noFall);

        AABB path = player.getBoundingBox().expandTowards(dir.scale(ctx.param("distance"))).inflate(ALLY_RADIUS);
        int allySpeed = ctx.ticks("ally_speed_ticks");
        if (allySpeed > 0) {
            for (Player ally : ctx.level().getEntitiesOfClass(Player.class, path, p -> p != player)) {
                ally.addEffect(new MobEffectInstance(MobEffects.SPEED, allySpeed, 0), player);
            }
        }

        // Flame dash: burning trail behind (milestone 5), enemies on the path ignited and pushed (10)
        int trail = ctx.ticks("fire_trail_ticks");
        if (trail > 0) {
            Vec3 from = player.position();
            for (int i = 0; i < TRAIL_PATCHES; i++) {
                Vec3 at = from.add(dir.scale(ctx.param("distance") * i / TRAIL_PATCHES));
                BurningGround.spawn(ctx.level(), at, 1.0, trail, player, ctx.spell().school(), ctx.spellId(), ctx.castId());
            }
        }
        if (ctx.param("path_ignite") > 0) {
            for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, path, e -> e != player && e.isAlive() && !(e instanceof Player))) {
                e.igniteForTicks(PATH_IGNITE_TICKS);
                e.push(dir.x * PATH_PUSH, 0.3, dir.z * PATH_PUSH);
            }
        }
    }

    private DashBehavior() {}
}
