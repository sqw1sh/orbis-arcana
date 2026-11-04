package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModBlocks;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.school.TalentEffects;
import com.orbisarcana.spell.Fx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code seed}: a thorn seed that sprouts into a thicket where it lands (Thorn Seed, §14.7). The thicket pricks and
 * slows enemies. Params: damage_per_second, radius, duration_ticks, speed, range; barrier (5: a ring of thorn
 * hedges), side_spikes (10: spikes burst out when it sprouts), guardian_ticks (15: a guardian grows for a while;
 * an iron golem stands in until the treant model exists).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
final class SeedBehavior {
    private static final double GRAVITY = 0.03;
    private static final int PULSE_TICKS = 20;
    private static final double SPIKE_RADIUS = 5;
    private static final double BARRIER_OFFSET = 0.8;
    private static final String GUARDIAN_TAG = "orbisarcana_guardian";
    private static final List<Seed> SEEDS = new ArrayList<>();
    private static final List<Thicket> THICKETS = new ArrayList<>();
    /** Summoned guardians -> game tick they vanish. */
    private static final Map<UUID, Long> GUARDIANS = new HashMap<>();

    private record Cast(ServerLevel level, UUID caster, Identifier spell, Identifier school, int castId, float dps, double radius,
            int duration, boolean barrier, float spikes, int guardianTicks) {}

    private static final class Seed {
        final Cast cast;
        Vec3 pos, vel;
        double travelled;
        final double range;

        Seed(Cast cast, Vec3 pos, Vec3 vel, double range) {
            this.cast = cast;
            this.pos = pos;
            this.vel = vel;
            this.range = range;
        }
    }

    private record Thicket(Cast cast, Vec3 center, long endTick) {}

    static void release(CastContext ctx) {
        ServerPlayer caster = ctx.caster();
        Cast c = new Cast(ctx.level(), caster.getUUID(), ctx.spellId(), ctx.spell().school(), ctx.castId(), (float) ctx.scaled("damage_per_second"),
                ctx.param("radius"), ctx.ticks("duration_ticks"), ctx.param("barrier") > 0, (float) ctx.scaled("side_spikes"), ctx.ticks("guardian_ticks"));
        double speed = Math.max(0.2, ctx.param("speed")) * TalentEffects.projectileSpeedMult(caster);
        SEEDS.add(new Seed(c, caster.getEyePosition().subtract(0, 0.2, 0), caster.getLookAngle().scale(speed), Math.max(1, ctx.param("range"))));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (!SEEDS.isEmpty()) SEEDS.removeIf(s -> s.cast.level() == level && !fly(s, now));
        if (!THICKETS.isEmpty()) THICKETS.removeIf(t -> t.cast().level() == level && !tick(t, now));
        if (!GUARDIANS.isEmpty() && now % 5 == 0) {
            GUARDIANS.entrySet().removeIf(e -> {
                if (now < e.getValue()) return false;
                var g = level.getEntity(e.getKey());
                if (g == null) return now > e.getValue() + 200; // maybe in another level; forget it eventually
                Fx.LEAF.play(level, g.getX(), g.getY() + 1.4, g.getZ(), 30, 0.6, 1, 0.6);
                g.discard();
                return true;
            });
        }
    }

    private static boolean fly(Seed s, long now) {
        Cast c = s.cast;
        ServerPlayer owner = c.level().getServer().getPlayerList().getPlayer(c.caster());
        Vec3 next = s.pos.add(s.vel);
        var clip = c.level().clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (clip.getType() != HitResult.Type.MISS) next = clip.getLocation();
        for (LivingEntity e : c.level().getEntitiesOfClass(LivingEntity.class, new AABB(s.pos, next).inflate(0.5),
                e -> e.isAlive() && e != owner && e.getBoundingBox().inflate(0.3).clip(s.pos, s.pos.add(s.vel)).isPresent())) {
            sprout(c, owner, e.position(), now);
            return false;
        }
        Fx.LEAF.play(c.level(), next.x, next.y, next.z, 1, 0, 0, 0);
        s.travelled += s.pos.distanceTo(next);
        s.pos = next;
        s.vel = s.vel.add(0, -GRAVITY, 0);
        if (clip.getType() != HitResult.Type.MISS) {
            sprout(c, owner, s.pos, now);
            return false;
        }
        return s.travelled < s.range;
    }

    /** The seed lands: the thicket grows on the ground under {@code at}. */
    private static void sprout(Cast c, @Nullable ServerPlayer owner, Vec3 at, long now) {
        var floor = c.level().clip(new ClipContext(at.add(0, 0.5, 0), at.add(0, -6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 center = floor.getType() == HitResult.Type.MISS ? at : floor.getLocation();
        THICKETS.add(new Thicket(c, center, now + c.duration()));
        Fx.LEAF.play(c.level(), center.x, center.y + 0.5, center.z, 40, c.radius() / 2, 0.4, c.radius() / 2);
        if (owner != null) EchoService.onEffectiveCast(owner, c.school(), center, c.castId());

        if (c.barrier()) {
            var hedge = ModBlocks.THORN_HEDGE.get().defaultBlockState();
            double r = c.radius() + BARRIER_OFFSET;
            int points = (int) Math.ceil(r * 2 * Math.PI);
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                BlockPos pos = BlockPos.containing(center.x + Math.cos(a) * r, center.y + 0.1, center.z + Math.sin(a) * r);
                if (!c.level().getBlockState(pos).isAir() || !c.level().getBlockState(pos.below()).isFaceSturdy(c.level(), pos.below(), net.minecraft.core.Direction.UP)) continue;
                c.level().setBlockAndUpdate(pos, hedge);
                c.level().scheduleTick(pos, hedge.getBlock(), c.duration());
            }
        }
        if (c.spikes() > 0) {
            for (LivingEntity e : enemies(c, owner, center, SPIKE_RADIUS)) {
                hurt(c, owner, e, c.spikes());
                Vec3 d = e.position().subtract(center);
                for (double t = 0.2; t < 1; t += 0.2) {
                    Vec3 p = center.add(d.scale(t));
                    Fx.SPARK.play(c.level(), p.x, p.y + 0.4, p.z, 1, 0, 0, 0);
                }
            }
        }
        if (c.guardianTicks() > 0 && owner != null) {
            var guardian = com.orbisarcana.registry.ModEntities.GUARDIAN_TREANT.get().create(c.level(), EntitySpawnReason.MOB_SUMMONED);
            if (guardian != null) {
                guardian.addTag(GUARDIAN_TAG);
                guardian.setPos(center);
                c.level().addFreshEntity(guardian);
                GUARDIANS.put(guardian.getUUID(), now + c.guardianTicks());
            }
        }
    }

    private static boolean tick(Thicket t, long now) {
        if (now >= t.endTick()) return false;
        Cast c = t.cast();
        ServerPlayer owner = c.level().getServer().getPlayerList().getPlayer(c.caster());
        if ((t.endTick() - now) % PULSE_TICKS == 0) {
            for (LivingEntity e : enemies(c, owner, t.center(), c.radius())) {
                hurt(c, owner, e, c.dps());
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PULSE_TICKS + 5, 1), owner);
            }
        }
        if (now % 4 == 0) {
            var rnd = c.level().getRandom();
            double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * c.radius();
            Fx.LEAF.play(c.level(), t.center().x + Math.cos(a) * r, t.center().y + 0.3, t.center().z + Math.sin(a) * r, 2, 0.1, 0.2, 0.1, 0xFFB0D8);
        }
        return true;
    }

    private static List<LivingEntity> enemies(Cast c, @Nullable ServerPlayer owner, Vec3 center, double radius) {
        return c.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, 2, radius),
                e -> e.isAlive() && e != owner && !(e instanceof Player) && !GUARDIANS.containsKey(e.getUUID())
                        && e.position().distanceToSqr(center.x, e.getY(), center.z) <= radius * radius);
    }

    private static void hurt(Cast c, @Nullable ServerPlayer owner, LivingEntity e, float damage) {
        float before = e.getHealth();
        e.hurtServer(c.level(), ModDamageTypes.source(e, c.school(), null, owner), damage);
        if (owner != null && e.getHealth() < before) {
            MasteryService.onHit(owner, c.spell(), c.castId(), e, !e.isAlive());
            EchoService.onSpellHit(owner, c.school(), e, c.castId());
        }
    }

    /** A guardian saved with an unloading chunk is gone when it loads again. */
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || !event.getEntity().entityTags().contains(GUARDIAN_TAG)) return;
        if (!GUARDIANS.containsKey(event.getEntity().getUUID())) event.setCanceled(true);
    }

    /** Summoned guardians leave nothing behind. */
    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        if (GUARDIANS.remove(event.getEntity().getUUID()) != null) event.setCanceled(true);
    }

    public static boolean isGuardian(LivingEntity e) {
        return GUARDIANS.containsKey(e.getUUID());
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        // guardians must not be saved as ordinary golems
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (UUID id : GUARDIANS.keySet()) {
                var g = level.getEntity(id);
                if (g != null) g.discard();
            }
        }
        SEEDS.clear();
        THICKETS.clear();
        GUARDIANS.clear();
    }

    private SeedBehavior() {}
}
