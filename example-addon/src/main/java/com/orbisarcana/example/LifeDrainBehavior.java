package com.orbisarcana.example;

import com.orbisarcana.api.OrbisArcanaAPI;
import com.orbisarcana.spell.behavior.CastContext;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * {@code orbisexample:life_drain}: hurts the target under the crosshair and heals the caster for a share of the damage
 * dealt. Params: damage, heal_share. Reports the hit and the healing through the API, so mastery and Echoes work.
 */
final class LifeDrainBehavior {
    private static final DustParticleOptions BLOOD = new DustParticleOptions(0xB01030, 1.2f);

    static void release(CastContext ctx) {
        LivingEntity target = ctx.target();
        ServerPlayer caster = ctx.caster();
        if (target == null || target == caster) return;
        float before = target.getHealth();
        target.hurtServer(ctx.level(), ctx.level().damageSources().indirectMagic(caster, caster), (float) ctx.scaled("damage"));
        float dealt = before - target.getHealth();
        if (dealt <= 0) return;
        var api = OrbisArcanaAPI.get();
        api.reportHit(caster, ctx.spellId(), ctx.castId(), target);
        float missing = caster.getMaxHealth() - caster.getHealth();
        float heal = (float) (dealt * ctx.param("heal_share"));
        caster.heal(heal);
        api.reportHeal(caster, ctx.spellId(), ctx.castId(), caster, Math.min(heal, missing));
        Vec3 from = target.getBoundingBox().getCenter();
        Vec3 to = caster.getBoundingBox().getCenter();
        for (int i = 0; i <= 12; i++) {
            Vec3 p = from.lerp(to, i / 12.0);
            ctx.level().sendParticles(BLOOD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private LifeDrainBehavior() {}
}
