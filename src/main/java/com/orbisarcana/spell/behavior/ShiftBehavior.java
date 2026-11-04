package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.spell.Stars;
import com.orbisarcana.spell.Fx;
import com.orbisarcana.spell.Vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code shift}: Constellation Shift (§14.10), a gravity zone that drags enemies to its centre and wears them down.
 * Params: radius, duration_ticks, pull, damage_per_second; second_zone (5: a smaller zone further along the aim),
 * hop_ticks (10: the centre hops between the caster's stars this often), collapse_damage (15: at the end the zone
 * collapses, hurting and throwing enemies up). Zones are server objects (§19.1).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class ShiftBehavior {
    /** Signature effects (tools/vfx-effects.cjs): the burst of stars when the field turns. */
    private static final Identifier BURST = Vfx.id("star_burst");
    private static final double SECOND_OFFSET = 6;
    private static final double SECOND_RADIUS_MULT = 0.5;
    private static final double LAUNCH = 0.9;

    private static final class Zone {
        final ServerLevel level;
        final UUID caster;
        final Identifier spell, school;
        final int castId;
        final double radius, pull;
        final float dps, collapse;
        final long start, until;
        final int hopTicks;
        Vec3 centre;
        int hop;

        Zone(CastContext ctx, Vec3 centre, double radius, int hopTicks) {
            this.level = ctx.level();
            this.caster = ctx.caster().getUUID();
            this.spell = ctx.spellId();
            this.school = ctx.spell().school();
            this.castId = ctx.castId();
            this.radius = radius;
            this.pull = ctx.param("pull");
            this.dps = (float) ctx.scaled("damage_per_second");
            this.collapse = (float) ctx.scaled("collapse_damage");
            this.start = ctx.level().getGameTime();
            this.until = start + ctx.ticks("duration_ticks");
            this.hopTicks = hopTicks;
            this.centre = centre;
        }
    }

    private static final List<Zone> ZONES = new ArrayList<>();

    static void release(CastContext ctx) {
        Vec3 point = ctx.groundPoint() != null ? ctx.groundPoint() : ctx.caster().position();
        double r = ctx.param("radius");
        ZONES.add(new Zone(ctx, point, r, ctx.ticks("hop_ticks")));
        if (ctx.param("second_zone") > 0) {
            Vec3 look = ctx.caster().getLookAngle().multiply(1, 0, 1);
            Vec3 dir = look.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : look.normalize();
            ZONES.add(new Zone(ctx, point.add(dir.scale(SECOND_OFFSET)), r * SECOND_RADIUS_MULT, 0));
        }
    }

    public static int active() {
        return ZONES.size();
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || ZONES.isEmpty()) return;
        long now = level.getGameTime();
        ZONES.removeIf(z -> {
            if (z.level != level) return false;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(z.caster);
            if (caster == null) return true;
            if (z.hopTicks > 0 && now > z.start && (now - z.start) % z.hopTicks == 0) {
                var stars = Stars.of(caster);
                if (!stars.isEmpty()) {
                    z.centre = stars.get(z.hop++ % stars.size()).pos();
                    Fx.TWINKLE.play(level, z.centre.x, z.centre.y + 0.5, z.centre.z, 40, 0.5, 0.5, 0.5, 0xB08CFF);
                }
            }
            if (now >= z.until) {
                if (z.collapse > 0) collapse(z, caster);
                return true;
            }
            for (LivingEntity e : inside(z)) {
                Vec3 in = z.centre.subtract(e.position()).multiply(1, 0, 1);
                if (in.lengthSqr() > 0.25) {
                    Vec3 d = in.normalize().scale(z.pull);
                    e.push(d.x, 0, d.z);
                }
                if (z.dps > 0 && (now - z.start) % 20 == 10) hurt(z, caster, e, z.dps);
            }
            if (now % 3 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = level.getRandom().nextDouble() * Math.PI * 2;
                    double d = level.getRandom().nextDouble() * z.radius;
                    Fx.TWINKLE.play(level, z.centre.x + Math.cos(a) * d, z.centre.y + 0.2, z.centre.z + Math.sin(a) * d, 1, 0, 0, 0, 0xB08CFF);
                }
            }
            return false;
        });
    }

    private static List<LivingEntity> inside(Zone z) {
        return z.level.getEntitiesOfClass(LivingEntity.class, new AABB(z.centre, z.centre).inflate(z.radius, 2, z.radius),
                e -> e.isAlive() && !(e instanceof Player) && e.position().multiply(1, 0, 1).distanceTo(z.centre.multiply(1, 0, 1)) <= z.radius);
    }

    private static void collapse(Zone z, ServerPlayer caster) {
        for (LivingEntity e : inside(z)) {
            e.damageCooldownTime = 0;
            hurt(z, caster, e, z.collapse);
            e.push(0, LAUNCH, 0);
        }
        Fx.TWINKLE.play(z.level, z.centre.x, z.centre.y + 0.5, z.centre.z, 40, z.radius / 2, 0.5, z.radius / 2);
        Vfx.play(z.level, BURST, z.centre.add(0, 0.5, 0), Vec3.ZERO, 0, 1f, (float) z.radius / 2, 0.5f, (float) z.radius / 2, 3);
        com.orbisarcana.spell.Sfx.impact(z.level, z.centre, com.orbisarcana.school.Schools.ASTRAL, 1.2f);
    }

    private static void hurt(Zone z, ServerPlayer caster, LivingEntity e, float damage) {
        float before = e.getHealth();
        e.hurtServer(z.level, ModDamageTypes.source(e, z.school, null, caster), damage);
        if (e.getHealth() < before) {
            MasteryService.onHit(caster, z.spell, z.castId, e, !e.isAlive());
            EchoService.onSpellHit(caster, z.school, e, z.castId);
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        ZONES.clear();
    }

    private ShiftBehavior() {}
}
