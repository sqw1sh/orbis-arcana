package com.orbisarcana.spell.behavior;

import com.orbisarcana.entity.SpellProjectile;
import com.orbisarcana.school.TalentEffects;

import net.minecraft.world.phys.Vec3;

/** {@code projectile}: fires {@code count} projectiles in a fan of {@code spread_deg}. */
final class ProjectileBehavior {
    static void release(CastContext ctx) {
        int count = Math.max(1, ctx.ticks("count"));
        double spread = ctx.param("spread_deg");
        float speed = (float) Math.max(0.1, ctx.param("speed"));
        int maxAge = (int) Math.ceil(Math.max(1, ctx.param("range")) / speed);

        for (int i = 0; i < count; i++) {
            double yawOffset = count == 1 ? 0 : spread * (i - (count - 1) / 2.0);
            SpellProjectile p = SpellProjectile.create(ctx.level(), ctx.caster(), ctx.spell().school(),
                    (float) ctx.scaled("damage"), (int) Math.round(ctx.param("ignite_ticks") * TalentEffects.igniteMult(ctx.caster())), (float) ctx.param("explode_radius"),
                    ctx.ticks("ground_fire_ticks"), maxAge).attribute(ctx.spellId(), ctx.castId());
            Vec3 dir = ctx.caster().getLookAngle().yRot((float) Math.toRadians(-yawOffset));
            p.shoot(dir.x, dir.y, dir.z, speed, 0.0f);
            ctx.level().addFreshEntity(p);
        }
    }

    private ProjectileBehavior() {}
}
