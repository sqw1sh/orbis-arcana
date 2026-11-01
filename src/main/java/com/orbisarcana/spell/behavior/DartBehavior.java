package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.school.TalentEffects;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * {@code dart}: Shadow Dart (§14.9), a fast shadow bolt that leaves a Dusk mark. Server objects (§19.1).
 * Params: damage, speed, range; count + volley_delay (5: two darts), echo_hit (10: the hit repeats after
 * {@link #ECHO_DELAY} for this share of the damage), kill_jump (15: a kill sends the dart on to the nearest enemy
 * within this range).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
final class DartBehavior {
    private static final double HIT_INFLATE = 0.3;
    private static final int ECHO_DELAY = 20;

    private record Cast(ServerLevel level, UUID caster, Identifier spell, Identifier school, int castId,
            float damage, double speed, double range, double echoShare, double jumpRange) {}

    private static final class Dart {
        final Cast cast;
        final long startTick;
        @Nullable Vec3 pos, dir;
        double travelled;
        boolean launched;
        final @Nullable UUID skip;

        Dart(Cast cast, long startTick, @Nullable Vec3 pos, @Nullable Vec3 dir, @Nullable UUID skip) {
            this.cast = cast;
            this.startTick = startTick;
            this.pos = pos;
            this.dir = dir;
            this.skip = skip;
        }
    }

    /** A delayed second hit (milestone 10). */
    private record Echo(Cast cast, LivingEntity target, long at, float damage) {}

    private static final List<Dart> DARTS = new ArrayList<>();
    private static final List<Echo> ECHOES = new ArrayList<>();

    static void release(CastContext ctx) {
        ServerPlayer caster = ctx.caster();
        double speed = Math.max(0.2, ctx.param("speed")) * TalentEffects.projectileSpeedMult(caster);
        Cast cast = new Cast(ctx.level(), caster.getUUID(), ctx.spellId(), ctx.spell().school(), ctx.castId(),
                (float) ctx.scaled("damage"), speed, Math.max(1, ctx.param("range")), ctx.param("echo_hit"), ctx.param("kill_jump"));
        int count = Math.max(1, ctx.ticks("count"));
        long now = ctx.level().getGameTime();
        for (int i = 0; i < count; i++) DARTS.add(new Dart(cast, now + (long) i * ctx.ticks("volley_delay"), null, null, null));
        tickNow(ctx.level(), now);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (!DARTS.isEmpty()) tickNow(level, now);
        if (ECHOES.isEmpty()) return;
        for (Echo e : List.copyOf(ECHOES)) {
            if (e.cast().level() != level || now < e.at()) continue;
            ECHOES.remove(e);
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(e.cast().caster());
            if (owner == null || !e.target().isAlive()) continue;
            e.target().damageCooldownTime = 0;
            hurt(e.cast(), owner, e.target(), e.damage());
            Vec3 c = e.target().getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.02);
        }
    }

    private static void tickNow(ServerLevel level, long now) {
        for (Dart d : List.copyOf(DARTS)) {
            if (d.cast.level() != level || now < d.startTick) continue;
            if (!tick(d)) DARTS.remove(d);
        }
    }

    private static boolean tick(Dart d) {
        Cast c = d.cast;
        ServerPlayer owner = c.level().getServer().getPlayerList().getPlayer(c.caster());
        if (owner == null) return false;
        if (!d.launched) {
            d.launched = true;
            if (d.pos == null) {
                d.pos = owner.getEyePosition().subtract(0, 0.2, 0);
                d.dir = owner.getLookAngle();
            }
        }
        Vec3 next = d.pos.add(d.dir.scale(c.speed()));
        var clip = c.level().clip(new ClipContext(d.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (clip.getType() != HitResult.Type.MISS) next = clip.getLocation();
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        Vec3 from = d.pos;
        for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, next).inflate(1),
                e -> e.isAlive() && e != owner && !(e instanceof Player) && !e.getUUID().equals(d.skip))) {
            var hit = e.getBoundingBox().inflate(HIT_INFLATE).clip(from, next);
            if (hit.isPresent() && hit.get().distanceToSqr(from) < best) {
                best = hit.get().distanceToSqr(from);
                target = e;
            }
        }
        d.pos = target != null ? from.add(next.subtract(from).normalize().scale(Math.sqrt(best))) : next;
        d.travelled += c.speed();
        Vec3 trail = d.pos.subtract(from);
        for (double t = 0; t < 1; t += 0.25) {
            Vec3 p = from.add(trail.scale(t));
            c.level().sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            c.level().sendParticles(ParticleTypes.WITCH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        if (target != null) {
            hit(d, owner, target);
            return false;
        }
        return clip.getType() == HitResult.Type.MISS && d.travelled < c.range();
    }

    private static void hit(Dart d, ServerPlayer owner, LivingEntity target) {
        Cast c = d.cast;
        hurt(c, owner, target, c.damage());
        if (target.isAlive()) {
            EchoService.markDusk(owner, target);
            if (c.echoShare() > 0) ECHOES.add(new Echo(c, target, c.level().getGameTime() + ECHO_DELAY, (float) (c.damage() * c.echoShare())));
        } else if (c.jumpRange() > 0) {
            Vec3 at = target.getBoundingBox().getCenter();
            c.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(c.jumpRange()),
                            e -> e.isAlive() && e != target && !(e instanceof Player))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(target)))
                    .ifPresent(next -> DARTS.add(new Dart(c, c.level().getGameTime() + 1, at,
                            next.getBoundingBox().getCenter().subtract(at).normalize(), target.getUUID())));
        }
        Vec3 p = target.getBoundingBox().getCenter();
        c.level().sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 10, 0.2, 0.3, 0.2, 0.02);
    }

    private static void hurt(Cast c, ServerPlayer owner, LivingEntity target, float damage) {
        float before = target.getHealth();
        target.hurtServer(c.level(), ModDamageTypes.source(target, c.school(), null, owner), damage);
        if (target.getHealth() < before) {
            MasteryService.onHit(owner, c.spell(), c.castId(), target, !target.isAlive());
            EchoService.onSpellHit(owner, c.school(), target, c.castId());
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        DARTS.clear();
        ECHOES.clear();
    }

    private DartBehavior() {}
}
