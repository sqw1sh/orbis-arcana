package com.orbisarcana.spell.behavior;

import com.orbisarcana.spell.CastService;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * {@code dash}: an impulse in the movement direction (look direction when standing still).
 * Velocity is tuned so the travelled distance roughly matches {@code distance} on flat ground.
 */
final class DashBehavior {
    /** Horizontal speed per block of distance, compensating ground friction. */
    private static final double SPEED_PER_BLOCK = 0.16;
    private static final double ALLY_RADIUS = 2.5;

    static void release(CastContext ctx) {
        var player = ctx.caster();
        Vec3 move = player.getKnownMovement().multiply(1, 0, 1);
        Vec3 dir = move.lengthSqr() > 1.0E-4 ? move.normalize() : player.getLookAngle().multiply(1, 0, 1).normalize();
        double speed = ctx.scaled("distance") * SPEED_PER_BLOCK;

        player.setDeltaMovement(dir.x * speed, Math.max(player.getDeltaMovement().y, 0.25), dir.z * speed);
        player.syncVelocity = true;
        ctx.level().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 15, 0.3, 0.3, 0.3, 0.02);

        int noFall = ctx.ticks("no_fall_ticks");
        if (noFall > 0) CastService.grantNoFall(player, noFall);

        int allySpeed = ctx.ticks("ally_speed_ticks");
        if (allySpeed > 0) {
            for (Player ally : ctx.level().getEntitiesOfClass(Player.class, player.getBoundingBox().expandTowards(dir.scale(ctx.param("distance"))).inflate(ALLY_RADIUS),
                    p -> p != player)) {
                ally.addEffect(new MobEffectInstance(MobEffects.SPEED, allySpeed, 0), player);
            }
        }
    }

    private DashBehavior() {}
}
