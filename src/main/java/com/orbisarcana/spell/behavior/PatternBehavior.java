package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.spell.Frost;
import com.orbisarcana.spell.Fx;
import com.orbisarcana.spell.Vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code pattern}: a frost pattern on the ground that strongly slows enemies (Frost Pattern, §14.5).
 * Params: radius, duration_ticks, slow_amplifier; link (10: damage to one target in the pattern is shared with the
 * others at {@code link_share}), freeze_end_ticks (15: everyone inside freezes when it ends).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
final class PatternBehavior {
    /** Signature effects (tools/vfx-effects.cjs): frost glinting on the ground, a cold mist, the freezing ring. */
    private static final Identifier FIELD = Vfx.id("frost_field"), MIST = Vfx.id("frost_mist"), RING = Vfx.id("frost_ring"),
            SHATTER = Vfx.id("ice_shatter");
    private static final int SLOW_TICKS = 20;
    private static final List<Pattern> PATTERNS = new ArrayList<>();
    /** Guards against shared damage sharing itself again. */
    private static boolean sharing;

    private static final class Pattern {
        final ServerLevel level;
        final UUID caster;
        final Identifier spell, school;
        final int castId;
        final Vec3 center;
        final double radius, linkShare;
        final int amplifier, freezeTicks;
        final long endTick;
        final Set<UUID> controlled = new HashSet<>();

        Pattern(CastContext ctx, Vec3 center) {
            this.level = ctx.level();
            this.caster = ctx.caster().getUUID();
            this.spell = ctx.spellId();
            this.school = ctx.spell().school();
            this.castId = ctx.castId();
            this.center = center;
            this.radius = Math.max(1, ctx.param("radius"));
            this.linkShare = ctx.param("link") > 0 ? ctx.param("link_share") : 0;
            this.amplifier = ctx.ticks("slow_amplifier");
            this.freezeTicks = ctx.ticks("freeze_end_ticks");
            this.endTick = level.getGameTime() + ctx.ticks("duration_ticks");
        }

        List<LivingEntity> inside(ServerPlayer caster) {
            return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, 2, radius),
                    e -> e.isAlive() && e != caster && !(e instanceof Player)
                            && e.position().distanceToSqr(center.x, e.getY(), center.z) <= radius * radius);
        }
    }

    static void release(CastContext ctx) {
        Vec3 point = ctx.groundPoint() != null ? ctx.groundPoint() : ctx.caster().position();
        PATTERNS.add(new Pattern(ctx, point));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || PATTERNS.isEmpty()) return;
        long now = level.getGameTime();
        PATTERNS.removeIf(p -> {
            if (p.level != level) return false;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(p.caster);
            if (now >= p.endTick) {
                if (p.freezeTicks > 0) {
                    for (LivingEntity e : p.inside(caster)) Frost.freeze(e, p.freezeTicks, caster);
                    Fx.FROST.play(level, p.center.x, p.center.y + 0.3, p.center.z, 40, p.radius / 2, 0.3, p.radius / 2);
                    Vfx.play(level, RING, p.center.add(0, 0.1, 0), Vec3.ZERO, 0, (float) Math.max(1, p.radius / 3));
                    Vfx.play(level, SHATTER, p.center.add(0, 0.5, 0), Vec3.ZERO, 0, 1f, (float) p.radius / 2, 0.3f, (float) p.radius / 2, 2.5f);
                    com.orbisarcana.spell.Sfx.impact(level, p.center, com.orbisarcana.school.Schools.ICE, 1f);
                }
                return true;
            }
            if (now % 10 == 0) {
                for (LivingEntity e : p.inside(caster)) {
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, p.amplifier), caster);
                    if (caster != null && p.controlled.add(e.getUUID())) MasteryService.onControl(caster, p.spell, p.castId, e);
                }
            }
            if (now % 3 == 0) particles(p, now);
            return false;
        });
    }

    private static void particles(Pattern p, long now) {
        float r = (float) p.radius;
        if (now % 6 == 0) Vfx.play(p.level, FIELD, p.center.add(0, 0.08, 0), Vec3.ZERO, 0, 1f, r, 1, r, 1);
        if (now % 9 == 0) Vfx.play(p.level, MIST, p.center.add(0, 0.3, 0), Vec3.ZERO, 0, 1f, r, 1, r, 1);
        // a glint running round the edge
        double a = now * 0.15;
        Fx.FROST.play(p.level, p.center.x + Math.cos(a) * p.radius, p.center.y + 0.1, p.center.z + Math.sin(a) * p.radius, 1, 0, 0, 0);
    }

    /** Milestone 10: damage taken by one linked target is shared with the others in the pattern. */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (sharing || PATTERNS.isEmpty() || event.getHealthDamage() <= 0 || !(event.getEntity().level() instanceof ServerLevel level)) return;
        LivingEntity hurt = event.getEntity();
        for (Pattern p : PATTERNS) {
            if (p.linkShare <= 0 || p.level != level) continue;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(p.caster);
            List<LivingEntity> linked = p.inside(caster);
            if (!linked.contains(hurt)) continue;
            float share = (float) (event.getHealthDamage() * p.linkShare);
            sharing = true;
            try {
                for (LivingEntity e : linked) {
                    if (e == hurt) continue;
                    e.hurtServer(level, ModDamageTypes.source(e, p.school, null, caster), share);
                    Fx.FROST.play(level, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 6, 0.2, 0.2, 0.2);
                }
            } finally {
                sharing = false;
            }
            return;
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        PATTERNS.clear();
    }

    private PatternBehavior() {}
}
