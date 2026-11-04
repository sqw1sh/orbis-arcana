package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.spell.Fx;
import com.orbisarcana.spell.Vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code prison}: holds the target in place (Water Prison, §14.3). Params: hold_ticks; damage_per_second
 * (milestone 10); pop_wave (15: bursts at the end, pushing everyone nearby away).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
final class PrisonBehavior {
    /** Signature effects (tools/vfx-effects.cjs): the shimmering bubble, the burst of water when it pops. */
    private static final Identifier BUBBLE = Vfx.id("bubble"), WAVE = Vfx.id("water_wave");
    private static final double POP_RADIUS = 3.0;
    private static final double POP_PUSH = 1.0;
    private static final float POP_DAMAGE = 2.0f;
    private static final List<Prison> PRISONS = new ArrayList<>();

    private record Prison(ServerLevel level, LivingEntity target, UUID caster, Vec3 anchor, long endTick,
                          float damagePerSecond, boolean popWave, Identifier spell, Identifier school, int castId) {}

    static void release(CastContext ctx) {
        LivingEntity target = ctx.target();
        if (target == null || target == ctx.caster()) return;
        PRISONS.removeIf(p -> p.target() == target);
        PRISONS.add(new Prison(ctx.level(), target, ctx.caster().getUUID(), target.position(),
                ctx.level().getGameTime() + ctx.ticks("hold_ticks"), (float) ctx.scaled("damage_per_second"),
                ctx.param("pop_wave") > 0, ctx.spellId(), ctx.spell().school(), ctx.castId()));
        MasteryService.onControl(ctx.caster(), ctx.spellId(), ctx.castId(), target);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || PRISONS.isEmpty()) return;
        long now = level.getGameTime();
        PRISONS.removeIf(p -> {
            if (p.level() != level) return false;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(p.caster());
            if (!p.target().isAlive() || p.target().isRemoved()) return true;
            if (now >= p.endTick()) {
                if (p.popWave()) pop(p, caster);
                return true;
            }
            hold(p, caster, now);
            return false;
        });
    }

    private static void hold(Prison p, ServerPlayer caster, long now) {
        LivingEntity t = p.target();
        if (t instanceof Player) {
            // Players move client-side: freeze them with an overwhelming slowness instead of teleports
            t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 9, false, false));
        } else {
            t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
            if (t.position().distanceToSqr(p.anchor()) > 0.04) t.teleportTo(p.anchor().x, p.anchor().y, p.anchor().z);
        }
        if (p.damagePerSecond() > 0 && now % 20 == 0) {
            float before = t.getHealth();
            t.hurtServer(p.level(), ModDamageTypes.source(t, p.school(), null, caster), p.damagePerSecond());
            if (caster != null && t.getHealth() < before) MasteryService.onHit(caster, p.spell(), p.castId(), t, !t.isAlive());
        }
        if (now % 3 == 0) {
            double h = t.getBbHeight() / 2;
            Fx.SPLASH.play(p.level(), t.getX(), t.getY() + h, t.getZ(), 3, t.getBbWidth() * 0.6, h, t.getBbWidth() * 0.6);
        }
        // the bubble, as big as the one inside it
        if (now % 6 == 0) Vfx.play(p.level(), BUBBLE, t.position().add(0, t.getBbHeight() / 2, 0), Vec3.ZERO, 0, (float) Math.max(0.6, t.getBbHeight() * 0.65));
    }

    private static void pop(Prison p, ServerPlayer caster) {
        Vec3 c = p.target().position();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.target().getBoundingBox().inflate(POP_RADIUS),
                e -> e.isAlive() && e != caster)) {
            Vec3 away = e.position().subtract(c).multiply(1, 0, 1);
            Vec3 dir = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : away.normalize();
            e.push(dir.x * POP_PUSH, 0.35, dir.z * POP_PUSH);
            e.hurtServer(p.level(), ModDamageTypes.source(e, p.school(), null, caster), POP_DAMAGE);
        }
        Fx.SPLASH.play(p.level(), c.x, c.y + 1, c.z, 30, 1.2, 0.6, 1.2);
        Vfx.play(p.level(), WAVE, c.add(0, 0.6, 0), Vec3.ZERO, 0, 1f, 0.8f, 1, 0.8f, 1.5f);
        com.orbisarcana.spell.Sfx.impact(p.level(), c, p.school(), 1f);
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        PRISONS.clear();
    }

    private PrisonBehavior() {}
}
