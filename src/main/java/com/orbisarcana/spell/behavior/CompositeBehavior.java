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
 * burst at the ground point, a nova around the caster, the target, or the caster; lasting shapes (roadmap V10) — a zone
 * on the ground, an aura that follows the caster, a wall across the aim, a meteor falling from the sky — and every effect
 * is applied to each. Channelled spells repeat their beam, cone or nova every {@code pulse_ticks} while held.
 * New spells need no Java: tools/new-spell.cjs writes the JSON.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
final class CompositeBehavior {
    private static final double HIT_INFLATE = 0.3, DEFAULT_SPEED = 1.5, DEFAULT_CHAIN_RANGE = 5;
    private static final int DEFAULT_PULSE = 10, DEFAULT_CHANNEL_PULSE = 5;
    private static final double METEOR_HEIGHT = 16, METEOR_BACK = 5;

    /** The registered behavior: instant and charged spells act on release, channelled ones on every pulse while held. */
    static final ChannelBehavior INSTANCE = new ChannelBehavior() {
        @Override
        public void release(CastContext ctx) {
            if (ctx.spell().cast().mode() != com.orbisarcana.core.CastMode.CHANNEL) CompositeBehavior.release(ctx);
        }

        @Override
        public void channelTick(CastContext ctx, int elapsed) {
            int pulse = (int) Math.max(1, Math.round(ctx.params().getOrDefault("pulse_ticks", (double) DEFAULT_CHANNEL_PULSE)));
            if (elapsed % pulse == 0) CompositeBehavior.release(ctx);
        }
    };

    /** What a cast needs once its context is gone: who, which spell, its params and parts. */
    private record Cast(ServerLevel level, UUID caster, Identifier spell, Identifier school, int castId, SpellCompose compose,
                        java.util.Map<String, Double> params, double power, int mastery) {
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
        final double maxTravel;
        final Set<UUID> hit = new HashSet<>();

        Bolt(Cast cast, Vec3 pos, Vec3 dir) {
            this(cast, pos, dir, Math.max(1, cast.p("range")));
        }

        Bolt(Cast cast, Vec3 pos, Vec3 dir, double maxTravel) {
            this.cast = cast;
            this.pos = pos;
            this.dir = dir;
            this.maxTravel = maxTravel;
            this.pierceLeft = (int) Math.round(cast.p("pierce"));
        }
    }

    private static final List<Bolt> BOLTS = new ArrayList<>();

    /** A lasting shape: a zone, an aura (its centre follows the caster) or a wall from {@code a} to {@code b}. */
    private static final class Lasting {
        final Cast cast;
        final String shape;
        Vec3 center;
        final Vec3 a, b;
        final long end;

        Lasting(Cast cast, String shape, Vec3 center, Vec3 a, Vec3 b, long end) {
            this.cast = cast;
            this.shape = shape;
            this.center = center;
            this.a = a;
            this.b = b;
            this.end = end;
        }
    }

    private static final List<Lasting> LASTING = new ArrayList<>();

    static void release(CastContext ctx) {
        SpellCompose c = ctx.spell().compose().orElse(null);
        if (c == null) return;
        ServerPlayer caster = ctx.caster();
        Cast cast = new Cast(ctx.level(), caster.getUUID(), ctx.spellId(), ctx.spell().school(), ctx.castId(), c, ctx.params(), ctx.power(), ctx.mastery());
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
            case "zone" -> lasting(cast, "zone", ground(ctx), null, null);
            case "aura" -> lasting(cast, "aura", caster.position(), null, null);
            case "wall" -> {
                // across the aim, centred on the ground point
                Vec3 g = ground(ctx), side = new Vec3(-caster.getLookAngle().z, 0, caster.getLookAngle().x);
                if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
                side = side.normalize().scale(Math.max(1, cast.p("length")) / 2);
                lasting(cast, "wall", g, g.subtract(side), g.add(side));
            }
            case "meteor" -> {
                // falls from high above, a little behind the caster's line of sight, and bursts where it lands
                Vec3 g = ground(ctx), back = caster.getLookAngle().multiply(1, 0, 1);
                back = back.lengthSqr() < 1.0E-4 ? Vec3.ZERO : back.normalize().scale(-METEOR_BACK);
                Vec3 from = g.add(back).add(0, METEOR_HEIGHT, 0), d = g.subtract(from);
                BOLTS.add(new Bolt(cast, from, d.normalize(), d.length() + 1));
            }
            default -> { }
        }
    }

    // ---- shapes ----

    private static Vec3 ground(CastContext ctx) {
        return ctx.groundPoint() != null ? ctx.groundPoint() : ctx.caster().position();
    }

    /** The travel effects, with the apogee ones on top at mastery 15. */
    private static List<Identifier> travel(Cast c) {
        return withApogee(c, c.compose().visuals().travel(), c.compose().visuals().apogeeTravel());
    }

    private static List<Identifier> withApogee(Cast c, List<Identifier> base, List<Identifier> apogee) {
        if (apogee.isEmpty() || c.mastery() < 15) return base;
        List<Identifier> all = new ArrayList<>(base);
        all.addAll(apogee);
        return all;
    }

    private static void lasting(Cast c, String shape, Vec3 center, @Nullable Vec3 a, @Nullable Vec3 b) {
        long ticks = Math.max(1, Math.round(c.p("duration_ticks")));
        LASTING.add(new Lasting(c, shape, center, a, b, c.level().getGameTime() + ticks));
    }

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
        travel(c).forEach(id -> Vfx.play(c.level(), id, hand, d, c.compose().visuals().tint(), (float) Math.max(0.3, d.length())));
        apply(c, caster, line, last);
    }

    private static void cone(Cast c, ServerPlayer caster) {
        Vec3 eye = caster.getEyePosition(), look = caster.getLookAngle();
        double range = Math.max(1, c.p("range")), cos = Math.cos(Math.toRadians(Math.max(5, c.p("angle_deg")) / 2));
        List<LivingEntity> in = c.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
                e -> affected(c, caster, e) && within(eye, look, e.getBoundingBox().getCenter(), range, cos));
        travel(c).forEach(id -> Vfx.play(c.level(), id, eye.add(0, -0.25, 0).add(look.scale(0.6)), look,
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
        int burn = (int) Math.round(c.p("ground_fire_ticks"));
        if (burn > 0) BurningGround.spawn(c.level(), center, r, burn, caster, c.school(), c.spell(), c.castId());
    }

    // ---- bolts ----

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        for (Bolt b : List.copyOf(BOLTS)) {
            if (b.cast.level() != level) continue;
            if (!fly(b)) BOLTS.remove(b);
        }
        for (Lasting z : List.copyOf(LASTING)) {
            if (z.cast.level() != level) continue;
            if (!pulse(z, level.getGameTime())) LASTING.remove(z);
        }
    }

    /** One tick of a lasting shape: the travel effects and the effects on whoever is inside every pulse, the impact at the end. */
    private static boolean pulse(Lasting z, long now) {
        Cast c = z.cast;
        ServerPlayer caster = c.owner();
        if (caster == null || !caster.isAlive()) return false;
        if (z.shape.equals("aura")) z.center = caster.position();
        double r = Math.max(0.5, c.p("radius"));
        if (now >= z.end) {
            impact(c, z.center.add(0, 0.2, 0), (float) Math.max(1, r / 2.5));
            return false;
        }
        int every = (int) Math.max(1, Math.round(c.p("pulse_ticks") > 0 ? c.p("pulse_ticks") : DEFAULT_PULSE));
        if ((z.end - now) % every != 0) return true;
        var vis = c.compose().visuals();
        List<LivingEntity> in;
        if (z.shape.equals("wall")) {
            double half = Math.max(0.5, c.p("width") > 0 ? c.p("width") / 2 : 0.75), h = c.p("height") > 0 ? c.p("height") : 3;
            Vec3 ab = z.b.subtract(z.a);
            double len = ab.length();
            for (double t = 0; t <= len + 1.0E-3; t += 1.5) {
                Vec3 at = z.a.add(ab.scale(len < 1.0E-3 ? 0 : t / len));
                travel(c).forEach(id -> Vfx.play(c.level(), id, at, Vec3.ZERO, vis.tint(), (float) (h / 3)));
            }
            in = c.level().getEntitiesOfClass(LivingEntity.class, new AABB(z.a, z.b).inflate(half + 1, h, half + 1),
                    e -> affected(c, caster, e) && e.getY() >= z.center.y - 1 && e.getY() <= z.center.y + h
                            && distanceToSegment(e.position().multiply(1, 0, 1), z.a.multiply(1, 0, 1), z.b.multiply(1, 0, 1)) <= half + e.getBbWidth() / 2);
        } else {
            travel(c).forEach(id -> Vfx.play(c.level(), id, z.center.add(0, 0.1, 0), Vec3.ZERO, vis.tint(), (float) Math.max(0.5, r / 2.5)));
            in = c.level().getEntitiesOfClass(LivingEntity.class, new AABB(z.center, z.center).inflate(r, 2, r),
                    e -> affected(c, caster, e) && e.position().distanceTo(z.center) <= r + e.getBbWidth() / 2);
        }
        for (LivingEntity e : in) for (SpellCompose.Effect fx : c.compose().effects()) effect(c, caster, e, fx, z.center);
        return true;
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = ab.lengthSqr() < 1.0E-6 ? 0 : Math.clamp(p.subtract(a).dot(ab) / ab.lengthSqr(), 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
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
        travel(c).forEach(id -> Vfx.play(c.level(), id, from.add(trail.scale(0.5)), trail, c.compose().visuals().tint(), 1f));
        b.pos = target != null ? target.getBoundingBox().getCenter() : to;
        b.travelled += from.distanceTo(b.pos);
        if (target != null) {
            b.hit.add(target.getUUID());
            hitWithBolt(c, caster, target);
            if (b.pierceLeft-- > 0) return true;
            chain(c, caster, target, b.hit);
            return false;
        }
        if (clip.getType() != HitResult.Type.MISS || b.travelled >= b.maxTravel) {
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
        if (jumps > 0 && c.school().equals(com.orbisarcana.school.Schools.LIGHTNING)) {
            // the Lightning chain talents (Splitting, Superconductor) work on composite chains as on Discharge
            jumps += com.orbisarcana.school.TalentEffects.extraJumps(caster);
            range *= com.orbisarcana.school.TalentEffects.jumpRangeMult(caster);
        }
        LivingEntity at = from;
        for (int i = 0; i < jumps; i++) {
            LivingEntity last = at;
            LivingEntity next = c.level().getEntitiesOfClass(LivingEntity.class, at.getBoundingBox().inflate(range),
                            e -> affected(c, caster, e) && !hit.contains(e.getUUID()))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(last))).orElse(null);
            if (next == null) return;
            Vec3 a = at.getBoundingBox().getCenter(), d = next.getBoundingBox().getCenter().subtract(a);
            var vis = c.compose().visuals();
            if (vis.chain().isEmpty()) travel(c).forEach(id -> Vfx.play(c.level(), id, a.add(d.scale(0.5)), d, vis.tint(), 1f));
            else vis.chain().forEach(id -> Vfx.play(c.level(), id, a.add(d.scale(0.5)), d, vis.tint(), (float) d.length()));
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
        List<Identifier> impact = withApogee(c, c.compose().visuals().impact(), c.compose().visuals().apogeeImpact());
        if (impact.isEmpty()) return;
        impact.forEach(id -> Vfx.play(c.level(), id, at, Vec3.ZERO, c.compose().visuals().tint(), scale));
        float volume = Math.min(1.5f, 0.6f + 0.3f * scale);
        var own = c.compose().visuals().impactSound();
        if (own.isPresent()) Sfx.impactSound(c.level(), at, own.get(), volume);
        else Sfx.impact(c.level(), at, c.school(), volume);
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
            case "ignite" -> e.igniteForTicks((int) Math.round(ticks * com.orbisarcana.school.TalentEffects.igniteMult(caster)));
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
            case "shield" -> {
                // absorption that does not stack with itself: tops the current amount up to the shield
                float shield = (float) (v * c.power()), added = shield - e.getAbsorptionAmount();
                if (added <= 0) return;
                e.setAbsorptionAmount(shield);
                MasteryService.onHeal(caster, c.spell(), c.castId(), e, added);
            }
            case "speed" -> e.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 0), caster);
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
        LASTING.clear();
    }

    private CompositeBehavior() {}
}
