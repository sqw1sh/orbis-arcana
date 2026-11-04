package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.spell.Frost;
import com.orbisarcana.spell.Sfx;
import com.orbisarcana.spell.SpellCompose;
import com.orbisarcana.spell.Vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code composite} (roadmap V9): a spell made of a shape, effects and visuals in its JSON ({@link SpellCompose}). The
 * shape finds who is affected — a bolt flying until it hits (with pierce, chain, explosion, volleys), a beam, a cone, a
 * burst at the ground point, a nova around the caster, the target, or the caster — and every effect is applied to each.
 * New spells need no Java: tools/new-spell.cjs writes the JSON.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
final class CompositeBehavior {
    private static final double HIT_INFLATE = 0.3, DEFAULT_SPEED = 1.5, DEFAULT_CHAIN_RANGE = 5;

    /** What a cast needs once its context is gone: who, which spell, its params and parts. */
    private record Cast(ServerLevel level, UUID caster, Identifier spell, Identifier school, int castId, SpellCompose compose,
                        java.util.Map<String, Double> params, double power) {
        double p(String name) {
            return params.getOrDefault(name, 0.0);
        }

        @Nullable ServerPlayer owner() {
            return level.getServer().getPlayerList().getPlayer(caster);
        }
    }

    /** A bolt in flight. */
    private static final class Bolt {
        final Cast cast;
        Vec3 pos, dir;
        double travelled;
        int pierceLeft;
        final Set<UUID> hit = new HashSet<>();

        Bolt(Cast cast, Vec3 pos, Vec3 dir) {
            this.cast = cast;
            this.pos = pos;
            this.dir = dir;
            this.pierceLeft = (int) Math.round(cast.p("pierce"));
        }
    }

    private static final List<Bolt> BOLTS = new ArrayList<>();

    static void release(CastContext ctx) {
        SpellCompose c = ctx.spell().compose().orElse(null);
        if (c == null) return;
        ServerPlayer caster = ctx.caster();
        Cast cast = new Cast(ctx.level(), caster.getUUID(), ctx.spellId(), ctx.spell().school(), ctx.castId(), c, ctx.params(), ctx.power());
        Vec3 hand = caster.getEyePosition().add(caster.getLookAngle().scale(0.6)).add(0, -0.25, 0);
        c.visuals().cast().forEach(id -> Vfx.play(ctx.level(), id, hand, caster.getLookAngle(), c.visuals().tint(), 1f));
        switch (c.shape()) {
            case "bolt" -> {
                int count = Math.max(1, (int) Math.round(cast.p("count")));
                double spread = cast.p("spread_deg");
                for (int i = 0; i < count; i++) {
                    double yaw = count == 1 ? 0 : spread * (i - (count - 1) / 2.0);
                    BOLTS.add(new Bolt(cast, hand, caster.getLookAngle().yRot((float) Math.toRadians(-yaw))));
                }
            }
            case "beam" -> beam(cast, caster, hand);
            case "cone" -> cone(cast, caster);
            case "burst" -> area(cast, caster, ctx.groundPoint() != null ? ctx.groundPoint() : caster.position(), cast.p("radius"));
            case "nova" -> area(cast, caster, caster.position(), cast.p("radius"));
            case "target" -> {
                LivingEntity t = ctx.target() != null ? ctx.target() : caster;
                apply(cast, caster, List.of(t), t.position().add(0, t.getBbHeight() / 2, 0));
            }
            case "self" -> apply(cast, caster, List.of(caster), caster.position().add(0, 1, 0));
            default -> { }
        }
    }

    // ---- shapes ----

    private static void beam(Cast c, ServerPlayer caster, Vec3 hand) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(Math.max(1, c.p("range"))));
        var block = c.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        Vec3 stop = end;
        List<LivingEntity> line = new ArrayList<>(c.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, stop).inflate(1),
                e -> affected(c, caster, e) && e.getBoundingBox().inflate(HIT_INFLATE).clip(eye, stop).isPresent()));
        line.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
        int keep = 1 + (int) Math.round(c.p("pierce"));
        if (line.size() > keep) line = line.subList(0, keep);
        Vec3 last = line.isEmpty() ? stop : line.getLast().getBoundingBox().getCenter();
        Vec3 d = last.subtract(hand);
        c.compose().visuals().travel().forEach(id -> Vfx.play(c.level(), id, hand, d, c.compose().visuals().tint(), (float) Math.max(0.3, d.length())));
        apply(c, caster, line, last);
    }

    private static void cone(Cast c, ServerPlayer caster) {
        Vec3 eye = caster.getEyePosition(), look = caster.getLookAngle();
        double range = Math.max(1, c.p("range")), cos = Math.cos(Math.toRadians(Math.max(5, c.p("angle_deg")) / 2));
        List<LivingEntity> in = c.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
                e -> affected(c, caster, e) && within(eye, look, e.getBoundingBox().getCenter(), range, cos));
        c.compose().visuals().travel().forEach(id -> Vfx.play(c.level(), id, eye.add(0, -0.25, 0).add(look.scale(0.6)), look,
                c.compose().visuals().tint(), (float) (range / 6)));
        apply(c, caster, in, eye.add(look.scale(range / 2)));
    }

    private static boolean within(Vec3 eye, Vec3 look, Vec3 at, double range, double cos) {
        Vec3 to = at.subtract(eye);
        double len = to.length();
        return len <= range && len > 1.0E-3 && to.scale(1 / len).dot(look) >= cos;
    }

    private static void area(Cast c, ServerPlayer caster, Vec3 center, double radius) {
        double r = Math.max(0.5, radius);
        List<LivingEntity> in = c.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r, 2, r),
                e -> affected(c, caster, e) && e.position().distanceTo(center) <= r + e.getBbWidth() / 2);
        apply(c, caster, in, center.add(0, 0.2, 0), (float) Math.max(1, r / 2.5));
    }

    // ---- bolts ----

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || BOLTS.isEmpty()) return;
        for (Bolt b : List.copyOf(BOLTS)) {
            if (b.cast.level() != level) continue;
            if (!fly(b)) BOLTS.remove(b);
        }
    }

    private static boolean fly(Bolt b) {
        Cast c = b.cast;
        ServerPlayer caster = c.owner();
        if (caster == null) return false;
        double speed = c.p("speed") > 0 ? c.p("speed") : DEFAULT_SPEED;
        Vec3 next = b.pos.add(b.dir.scale(speed));
        var clip = c.level().clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (clip.getType() != HitResult.Type.MISS) next = clip.getLocation();
        Vec3 from = b.pos, to = next;
        LivingEntity target = c.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1),
                        e -> affected(c, caster, e) && !b.hit.contains(e.getUUID()) && e.getBoundingBox().inflate(HIT_INFLATE).clip(from, to).isPresent())
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
        Vec3 trail = to.subtract(from);
        c.compose().visuals().travel().forEach(id -> Vfx.play(c.level(), id, from.add(trail.scale(0.5)), trail, c.compose().visuals().tint(), 1f));
        b.pos = target != null ? target.getBoundingBox().getCenter() : to;
        b.travelled += from.distanceTo(b.pos);
        if (target != null) {
            b.hit.add(target.getUUID());
            hitWithBolt(c, caster, target);
            if (b.pierceLeft-- > 0) return true;
            chain(c, caster, target, b.hit);
            return false;
        }
        if (clip.getType() != HitResult.Type.MISS || b.travelled >= Math.max(1, c.p("range"))) {
            if (c.p("explode_radius") > 0) area(c, caster, b.pos, c.p("explode_radius"));
            else impact(c, b.pos, 1f);
            return false;
        }
        return true;
    }

    /** A bolt hit: the target, and with explode_radius everyone around it. */
    private static void hitWithBolt(Cast c, ServerPlayer caster, LivingEntity target) {
        if (c.p("explode_radius") > 0) area(c, caster, target.position(), c.p("explode_radius"));
        else apply(c, caster, List.of(target), target.getBoundingBox().getCenter());
    }

    /** chain: the bolt jumps on to the nearest new target, chain times, each jump drawn with the travel effect. */
    private static void chain(Cast c, ServerPlayer caster, LivingEntity from, Set<UUID> hit) {
        int jumps = (int) Math.round(c.p("chain"));
        double range = c.p("chain_range") > 0 ? c.p("chain_range") : DEFAULT_CHAIN_RANGE;
        LivingEntity at = from;
        for (int i = 0; i < jumps; i++) {
            LivingEntity last = at;
            LivingEntity next = c.level().getEntitiesOfClass(LivingEntity.class, at.getBoundingBox().inflate(range),
                            e -> affected(c, caster, e) && !hit.contains(e.getUUID()))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(last))).orElse(null);
            if (next == null) return;
            Vec3 a = at.getBoundingBox().getCenter(), d = next.getBoundingBox().getCenter().subtract(a);
            c.compose().visuals().travel().forEach(id -> Vfx.play(c.level(), id, a.add(d.scale(0.5)), d, c.compose().visuals().tint(), 1f));
            hit.add(next.getUUID());
            hitWithBolt(c, caster, next);
            at = next;
        }
    }

    // ---- effects ----

    /** Who a shape may affect: enemies (not the caster or other players), allies (players), or only the caster. */
    private static boolean affected(Cast c, ServerPlayer caster, LivingEntity e) {
        if (!e.isAlive() || e.isSpectator()) return false;
        return switch (c.compose().affects()) {
            case "allies" -> e instanceof Player;
            case "self" -> e == caster;
            default -> e != caster && !(e instanceof Player);
        };
    }

    private static void apply(Cast c, ServerPlayer caster, List<LivingEntity> targets, Vec3 at) {
        apply(c, caster, targets, at, 1f);
    }

    private static void apply(Cast c, ServerPlayer caster, List<LivingEntity> targets, Vec3 at, float impactScale) {
        for (LivingEntity e : targets) for (SpellCompose.Effect fx : c.compose().effects()) effect(c, caster, e, fx, at);
        impact(c, at, impactScale);
    }

    private static void impact(Cast c, Vec3 at, float scale) {
        if (c.compose().visuals().impact().isEmpty()) return;
        c.compose().visuals().impact().forEach(id -> Vfx.play(c.level(), id, at, Vec3.ZERO, c.compose().visuals().tint(), scale));
        Sfx.impact(c.level(), at, c.school(), Math.min(1.5f, 0.6f + 0.3f * scale));
    }

    private static void effect(Cast c, ServerPlayer caster, LivingEntity e, SpellCompose.Effect fx, Vec3 at) {
        double v = c.p(fx.paramName());
        if (v <= 0) return;
        int ticks = (int) Math.round(v);
        switch (fx.type()) {
            case "damage" -> {
                float before = e.getHealth();
                e.hurtServer(c.level(), ModDamageTypes.source(e, c.school(), null, caster), (float) (v * c.power()));
                if (e.getHealth() < before) {
                    MasteryService.onHit(caster, c.spell(), c.castId(), e, !e.isAlive());
                    EchoService.onSpellHit(caster, c.school(), e, c.castId());
                }
            }
            case "heal" -> {
                float missing = e.getMaxHealth() - e.getHealth();
                if (missing <= 0) return;
                float healed = (float) Math.min(missing, v * c.power());
                e.heal(healed);
                MasteryService.onHeal(caster, c.spell(), c.castId(), e, healed);
                EchoService.onSupport(caster, healed);
            }
            case "ignite" -> e.igniteForTicks(ticks);
            case "slow" -> control(c, caster, e, new MobEffectInstance(MobEffects.SLOWNESS, ticks, Math.max(0, (int) c.p("slow_level") - 1)));
            case "weaken" -> control(c, caster, e, new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0));
            case "blind" -> control(c, caster, e, new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0));
            case "freeze" -> {
                Frost.freeze(e, ticks, caster);
                MasteryService.onControl(caster, c.spell(), c.castId(), e);
            }
            case "knockback", "pull" -> {
                Vec3 away = e.position().subtract(at).multiply(1, 0, 1);
                if (away.lengthSqr() < 1.0E-4) away = caster.getLookAngle().multiply(1, 0, 1);
                Vec3 push = away.normalize().scale(fx.type().equals("pull") ? -v : v);
                e.push(push.x, fx.type().equals("pull") ? 0.1 : 0.25, push.z);
                e.syncVelocity = true;
            }
            case "lift" -> {
                e.push(0, v, 0);
                e.syncVelocity = true;
            }
            default -> OrbisArcana.LOGGER.warn("Spell {}: unknown compose effect '{}'", c.spell(), fx.type());
        }
    }

    private static void control(Cast c, ServerPlayer caster, LivingEntity e, MobEffectInstance effect) {
        e.addEffect(effect, caster);
        MasteryService.onControl(caster, c.spell(), c.castId(), e);
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        BOLTS.clear();
    }

    private CompositeBehavior() {}
}
