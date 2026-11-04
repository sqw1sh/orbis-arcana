package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.school.TalentEffects;
import com.orbisarcana.spell.Frost;
import com.orbisarcana.spell.Fx;
import com.orbisarcana.spell.Vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
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
 * {@code shard}: ice shards that slow on hit (Ice Shard, §14.5). Server objects (§19.1).
 * Params: damage, speed, range, slow_ticks; count + volley_delay (5: three in a row), split (10: the shard breaks
 * into three small ones), frozen_burst (15: hitting a frozen target sets off an ice blast of this radius).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
final class ShardBehavior {
    /** Volumetric effects: the crystal of a shard in flight, the sphere of frost when it breaks. */
    private static final Identifier CONE = Vfx.id("ice_cone"), NOVA = Vfx.id("ice_nova");
    /** Signature effects (tools/vfx-effects.cjs): the streak and mist of a shard in flight, the shatter on impact. */
    private static final Identifier STREAK = Vfx.id("ice_streak"), MIST = Vfx.id("ice_mist"), FLASH = Vfx.id("ice_flash"),
            SHATTER = Vfx.id("ice_shatter"), PUFF = Vfx.id("ice_puff");

    private static final double HIT_INFLATE = 0.3;
    private static final double SPLIT_SHARE = 0.4;
    private static final double SPLIT_RANGE = 6;
    private static final double SPLIT_ANGLE_DEG = 25;
    private static final double BURST_MULT = 1.0;
    private static final List<Shard> SHARDS = new ArrayList<>();

    /** Shared numbers of one cast. */
    private record Cast(ServerLevel level, UUID caster, Identifier spell, Identifier school, int castId,
            float damage, double speed, int slowTicks, double burstRadius) {}

    private static final class Shard {
        final Cast cast;
        final long startTick;
        final double range;
        final boolean split;
        final float damage;
        final @Nullable UUID skip;
        Vec3 pos, dir;
        double travelled;
        boolean launched;

        Shard(Cast cast, long startTick, double range, boolean split, float damage, @Nullable UUID skip, Vec3 pos, Vec3 dir) {
            this.cast = cast;
            this.startTick = startTick;
            this.range = range;
            this.split = split;
            this.damage = damage;
            this.skip = skip;
            this.pos = pos;
            this.dir = dir;
        }
    }

    static void release(CastContext ctx) {
        ServerPlayer caster = ctx.caster();
        double speed = Math.max(0.2, ctx.param("speed")) * TalentEffects.projectileSpeedMult(caster);
        Cast cast = new Cast(ctx.level(), caster.getUUID(), ctx.spellId(), ctx.spell().school(), ctx.castId(),
                (float) ctx.scaled("damage"), speed, ctx.ticks("slow_ticks"), ctx.param("frozen_burst"));
        int count = Math.max(1, ctx.ticks("count"));
        long now = ctx.level().getGameTime();
        for (int i = 0; i < count; i++) {
            // later shards of a volley take the caster's aim when they launch
            SHARDS.add(new Shard(cast, now + (long) i * ctx.ticks("volley_delay"), Math.max(1, ctx.param("range")),
                    ctx.param("split") > 0, cast.damage(), null, null, null));
        }
        tickNow(ctx.level(), now);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && !SHARDS.isEmpty()) tickNow(level, level.getGameTime());
    }

    private static void tickNow(ServerLevel level, long now) {
        for (Shard s : List.copyOf(SHARDS)) {
            if (s.cast.level() != level || now < s.startTick) continue;
            if (!tick(s, now)) SHARDS.remove(s);
        }
    }

    private static boolean tick(Shard s, long now) {
        Cast c = s.cast;
        ServerPlayer owner = c.level().getServer().getPlayerList().getPlayer(c.caster());
        if (!s.launched) {
            if (owner == null) return false;
            s.launched = true;
            if (s.pos == null) {
                s.pos = owner.getEyePosition().subtract(0, 0.2, 0);
                s.dir = owner.getLookAngle();
            }
            return true; // flies from the next tick; one shard per tick keeps a volley readable
        }
        Vec3 next = s.pos.add(s.dir.scale(c.speed()));
        var clip = c.level().clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (clip.getType() != HitResult.Type.MISS) next = clip.getLocation();

        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(s.pos, next).inflate(1),
                e -> e.isAlive() && e != owner && !e.getUUID().equals(s.skip))) {
            var hit = e.getBoundingBox().inflate(HIT_INFLATE).clip(s.pos, next);
            if (hit.isPresent() && hit.get().distanceToSqr(s.pos) < best) {
                best = hit.get().distanceToSqr(s.pos);
                target = e;
            }
        }
        Vec3 from = s.pos;
        s.pos = target != null ? from.add(next.subtract(from).normalize().scale(Math.sqrt(best))) : next;
        s.travelled += c.speed();
        Vec3 trail = s.pos.subtract(from);
        if (trail.lengthSqr() > 1.0E-6) {
            Vfx.play(c.level(), STREAK, from.add(trail.scale(0.5)), trail, 0, 1f);
            Vfx.play(c.level(), CONE, s.pos.subtract(trail.normalize().scale(1.3)), trail, 0, 1f);
        }
        Vfx.play(c.level(), MIST, s.pos, Vec3.ZERO, 0, 1f);
        if (target != null) {
            hit(s, owner, target);
            return false;
        }
        if (clip.getType() != HitResult.Type.MISS) {
            shatter(c.level(), s.pos, 0.7f);
            return false;
        }
        return s.travelled < s.range;
    }

    /** A shard breaking: cold flash, ice splinters, a puff of frost. */
    private static void shatter(ServerLevel level, Vec3 at, float scale) {
        Vfx.play(level, FLASH, at, Vec3.ZERO, 0, scale);
        Vfx.play(level, NOVA, at, Vec3.ZERO, 0, scale);
        Vfx.play(level, SHATTER, at, Vec3.ZERO, 0, scale);
        Vfx.play(level, PUFF, at, Vec3.ZERO, 0, scale);
        com.orbisarcana.spell.Sfx.impact(level, at, com.orbisarcana.school.Schools.ICE, 0.6f * scale);
    }

    private static void hit(Shard s, @Nullable ServerPlayer owner, LivingEntity target) {
        Cast c = s.cast;
        boolean wasFrozen = Frost.isFrozen(target);
        hurt(c, owner, target, s.damage);
        if (c.slowTicks() > 0) target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.slowTicks(), 0), owner);
        shatter(c.level(), s.pos, 1f);
        if (wasFrozen && c.burstRadius() > 0) {
            for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(c.burstRadius()),
                    e -> e.isAlive() && e != owner && e != target && e.distanceTo(target) <= c.burstRadius() + target.getBbWidth())) {
                hurt(c, owner, e, (float) (c.damage() * BURST_MULT));
            }
            Fx.FROST.play(c.level(), target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 60, c.burstRadius() / 2, 0.5, c.burstRadius() / 2);
            Fx.FROST.play(c.level(), target.getX(), target.getY() + 1, target.getZ(), 40, c.burstRadius() / 2, 0.5, c.burstRadius() / 2);
            shatter(c.level(), target.position().add(0, target.getBbHeight() / 2, 0), (float) Math.max(1, c.burstRadius() / 1.5));
        }
        if (s.split) {
            long now = c.level().getGameTime();
            for (int i = -1; i <= 1; i++) {
                Vec3 d = s.dir.yRot((float) Math.toRadians(SPLIT_ANGLE_DEG * i));
                Shard child = new Shard(c, now, SPLIT_RANGE, false, (float) (s.damage * SPLIT_SHARE), target.getUUID(), s.pos, d);
                child.launched = true;
                SHARDS.add(child);
            }
        }
    }

    private static void hurt(Cast c, @Nullable ServerPlayer owner, LivingEntity e, float damage) {
        float before = e.getHealth();
        e.hurtServer(c.level(), ModDamageTypes.source(e, c.school(), null, owner), damage);
        if (owner != null && e.getHealth() < before) {
            MasteryService.onHit(owner, c.spell(), c.castId(), e, !e.isAlive());
            EchoService.onSpellHit(owner, c.school(), e, c.castId());
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        SHARDS.clear();
    }

    private ShardBehavior() {}
}
