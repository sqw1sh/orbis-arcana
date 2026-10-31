package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code zone}: a healing area on the ground. Zones are plain server objects, not entities (§19.1),
 * ticked per level and dropped when the server stops.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
final class ZoneBehavior {
    private static final int PULSE_TICKS = 20;
    private static final double FOLLOW_SPEED = 0.08;
    private static final List<Zone> ZONES = new ArrayList<>();

    private static final class Zone {
        final ServerLevel level;
        final UUID caster;
        Vec3 center;
        final double radius;
        final float healPerPulse;
        final int regenTicks;
        final boolean followWounded;
        final float finalBurst;
        final long endTick;

        Zone(CastContext ctx, Vec3 center) {
            this.level = ctx.level();
            this.caster = ctx.caster().getUUID();
            this.center = center;
            this.radius = ctx.param("radius");
            this.healPerPulse = (float) ctx.scaled("heal_per_second");
            this.regenTicks = ctx.ticks("regen_ticks");
            this.followWounded = ctx.param("follow_wounded") > 0;
            this.finalBurst = (float) ctx.scaled("final_burst_heal");
            this.endTick = ctx.level().getGameTime() + ctx.ticks("duration_ticks");
        }

        List<Player> alliesInside() {
            return level.getEntitiesOfClass(Player.class, new AABB(center, center).inflate(radius, 2, radius),
                    p -> p.isAlive() && p.position().distanceToSqr(center) <= radius * radius + 4);
        }
    }

    static void release(CastContext ctx) {
        Vec3 point = ctx.groundPoint() != null ? ctx.groundPoint() : ctx.caster().position();
        ZONES.add(new Zone(ctx, point));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || ZONES.isEmpty()) return;
        long now = level.getGameTime();
        ZONES.removeIf(zone -> {
            if (zone.level != level) return false;
            if (now >= zone.endTick) {
                if (zone.finalBurst > 0) zone.alliesInside().forEach(p -> p.heal(zone.finalBurst));
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, zone.center.x, zone.center.y + 0.5, zone.center.z, 30, zone.radius / 2, 0.3, zone.radius / 2, 0);
                return true;
            }
            tick(zone, now);
            return false;
        });
    }

    private static void tick(Zone zone, long now) {
        if (zone.followWounded) {
            zone.alliesInside().stream()
                    .filter(p -> p.getHealth() < p.getMaxHealth())
                    .min(Comparator.comparingDouble(p -> p.getHealth() / p.getMaxHealth()))
                    .ifPresent(p -> zone.center = zone.center.lerp(p.position(), FOLLOW_SPEED));
        }
        if ((zone.endTick - now) % PULSE_TICKS == 0) {
            for (Player p : zone.alliesInside()) {
                p.heal(zone.healPerPulse);
                if (zone.regenTicks > 0) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, zone.regenTicks, 0));
            }
        }
        if (now % 5 == 0) {
            // Ring outline until magic circles (S3) take over the visuals
            int points = (int) (zone.radius * 8);
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                zone.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, zone.center.x + Math.cos(a) * zone.radius,
                        zone.center.y + 0.1, zone.center.z + Math.sin(a) * zone.radius, 1, 0, 0, 0, 0);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        ZONES.clear();
    }

    private ZoneBehavior() {}
}
