package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code dome}: a wind dome that turns away incoming projectiles (Gust Dome, §14.4). Server object (§19.1).
 * Projectiles shot by players pass (allies); the rest bounce off the shell. Params: radius, duration_ticks;
 * edge_push (5: enemies at the edge are pushed out), ally_speed (10: allies inside are faster),
 * burst_damage (15: the dome bursts outward when it ends).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
final class DomeBehavior {
    private static final double SHELL = 0.75;
    private static final double EDGE_PUSH = 0.45;
    private static final double BURST_PUSH = 1.2;
    private static final int ALLY_SPEED_TICKS = 30;
    private static final List<Dome> DOMES = new ArrayList<>();

    private static final class Dome {
        final ServerLevel level;
        final UUID caster;
        final Identifier spell, school;
        final int castId;
        final Vec3 center;
        final double radius;
        final boolean edgePush, allySpeed;
        final float burst;
        final long endTick;

        Dome(CastContext ctx, Vec3 center) {
            this.level = ctx.level();
            this.caster = ctx.caster().getUUID();
            this.spell = ctx.spellId();
            this.school = ctx.spell().school();
            this.castId = ctx.castId();
            this.center = center;
            this.radius = Math.max(1, ctx.param("radius"));
            this.edgePush = ctx.param("edge_push") > 0;
            this.allySpeed = ctx.param("ally_speed") > 0;
            this.burst = (float) ctx.scaled("burst_damage");
            this.endTick = level.getGameTime() + ctx.ticks("duration_ticks");
        }
    }

    static void release(CastContext ctx) {
        Vec3 point = ctx.groundPoint() != null ? ctx.groundPoint() : ctx.caster().position();
        DOMES.add(new Dome(ctx, point));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || DOMES.isEmpty()) return;
        long now = level.getGameTime();
        DOMES.removeIf(d -> {
            if (d.level != level) return false;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(d.caster);
            if (now >= d.endTick) {
                if (d.burst > 0) burst(d, caster);
                return true;
            }
            tick(d, caster, now);
            return false;
        });
    }

    private static void tick(Dome d, ServerPlayer caster, long now) {
        AABB box = new AABB(d.center, d.center).inflate(d.radius + SHELL * 2);
        for (Projectile p : d.level.getEntitiesOfClass(Projectile.class, box, p -> p.isAlive() && !(p.getOwner() instanceof Player))) {
            Vec3 out = p.position().subtract(d.center);
            double dist = out.length();
            Vec3 v = p.getDeltaMovement();
            if (dist > d.radius + SHELL || dist < 1.0E-3) continue;
            Vec3 n = out.scale(1 / dist);
            double inward = v.dot(n);
            if (inward >= 0) continue; // already leaving
            p.setDeltaMovement(v.subtract(n.scale(2 * inward)));
            p.syncVelocity = true;
            p.needsSync = true;
            d.level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
            if (caster != null && p.getOwner() instanceof LivingEntity shooter) {
                MasteryService.onControl(caster, d.spell, d.castId, shooter);
            }
        }
        if (d.edgePush && now % 5 == 0) {
            for (LivingEntity e : enemies(d, caster, d.radius + 1)) {
                Vec3 out = e.position().subtract(d.center).multiply(1, 0, 1);
                double dist = out.length();
                if (dist < d.radius - 1.2 || dist < 1.0E-3) continue;
                Vec3 dir = out.scale(1 / dist);
                e.push(dir.x * EDGE_PUSH, 0.1, dir.z * EDGE_PUSH);
                if (caster != null) MasteryService.onControl(caster, d.spell, d.castId, e);
            }
        }
        if (d.allySpeed && now % 10 == 0) {
            for (Player ally : d.level.getEntitiesOfClass(Player.class, new AABB(d.center, d.center).inflate(d.radius),
                    a -> a.isAlive() && a.distanceToSqr(d.center) <= d.radius * d.radius)) {
                ally.addEffect(new MobEffectInstance(MobEffects.SPEED, ALLY_SPEED_TICKS, 0), caster);
            }
        }
        if (now % 4 == 0) {
            var rnd = d.level.getRandom();
            for (int i = 0; i < 14; i++) {
                double yaw = rnd.nextDouble() * Math.PI * 2;
                double pitch = rnd.nextDouble() * Math.PI / 2;
                double r = d.radius;
                d.level.sendParticles(ParticleTypes.CLOUD, d.center.x + Math.cos(yaw) * Math.cos(pitch) * r,
                        d.center.y + Math.sin(pitch) * r, d.center.z + Math.sin(yaw) * Math.cos(pitch) * r, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void burst(Dome d, ServerPlayer caster) {
        for (LivingEntity e : enemies(d, caster, d.radius + 1)) {
            Vec3 out = e.position().subtract(d.center).multiply(1, 0, 1);
            Vec3 dir = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize();
            e.push(dir.x * BURST_PUSH, 0.5, dir.z * BURST_PUSH);
            float before = e.getHealth();
            e.hurtServer(d.level, ModDamageTypes.source(e, d.school, null, caster), d.burst);
            if (caster != null && e.getHealth() < before) MasteryService.onHit(caster, d.spell, d.castId, e, !e.isAlive());
        }
        d.level.sendParticles(ParticleTypes.EXPLOSION, d.center.x, d.center.y + 1, d.center.z, 3, d.radius * 0.4, 0.5, d.radius * 0.4, 0);
        d.level.sendParticles(ParticleTypes.CLOUD, d.center.x, d.center.y + 1, d.center.z, 80, d.radius * 0.5, 0.8, d.radius * 0.5, 0.3);
    }

    private static List<LivingEntity> enemies(Dome d, ServerPlayer caster, double radius) {
        return d.level.getEntitiesOfClass(LivingEntity.class, new AABB(d.center, d.center).inflate(radius, 3, radius),
                e -> e.isAlive() && e != caster && !(e instanceof Player)
                        && e.position().distanceToSqr(d.center.x, e.getY(), d.center.z) <= radius * radius);
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        DOMES.clear();
    }

    private DomeBehavior() {}
}
