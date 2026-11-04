package com.orbisarcana.spell.behavior;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.spell.Fx;
import com.orbisarcana.spell.Vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code orbit}: Guardian Orbit (§14.10). Stars circle the caster and each stops one projectile. Params: stars,
 * duration_ticks, radius; reflect (10: a stopped projectile flies back as the caster's), launch_damage (15: casting
 * again sends the stars that are left at the target under the crosshair, ignoring the cooldown).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class OrbitBehavior {
    /** Volumetric effects: a small sphere of stars when a star catches something. */
    private static final Identifier NOVA = Vfx.id("star_nova");
    /** Signature effects (tools/vfx-effects.cjs): the orbiting stars, their beams of starlight, the burst of a catch. */
    private static final Identifier STAR = Vfx.id("orbit_star"), CORE = Vfx.id("star_core"), GLOW = Vfx.id("star_glow"), BURST = Vfx.id("star_burst");
    private static final double CATCH_RADIUS = 1.0;
    private static final double SPIN = 0.15;
    private static final double LAUNCH_RANGE = 24;

    private static final class Orbit {
        final ServerLevel level;
        final Identifier spell, school;
        final int castId;
        final long until;
        final double radius;
        final boolean reflect;
        final float launchDamage;
        int stars;

        Orbit(CastContext ctx) {
            this.level = ctx.level();
            this.spell = ctx.spellId();
            this.school = ctx.spell().school();
            this.castId = ctx.castId();
            this.until = ctx.level().getGameTime() + ctx.ticks("duration_ticks");
            this.radius = ctx.param("radius");
            this.reflect = ctx.param("reflect") > 0;
            this.launchDamage = (float) ctx.scaled("launch_damage");
            this.stars = Math.max(1, ctx.ticks("stars"));
        }
    }

    private static final Map<UUID, Orbit> ORBITS = new HashMap<>();

    static void release(CastContext ctx) {
        ORBITS.put(ctx.caster().getUUID(), new Orbit(ctx));
    }

    public static int stars(Player p) {
        Orbit o = ORBITS.get(p.getUUID());
        return o == null ? 0 : o.stars;
    }

    /** Milestone 15: a second press while the orbit spins launches the stars. Called by the cast service first. */
    public static boolean tryLaunch(ServerPlayer p, Identifier spell) {
        Orbit o = ORBITS.get(p.getUUID());
        if (o == null || !o.spell.equals(spell) || o.launchDamage <= 0) return false;
        ORBITS.remove(p.getUUID());
        Vec3 eye = p.getEyePosition();
        Vec3 to = eye.add(p.getLookAngle().scale(LAUNCH_RANGE));
        var block = p.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        LivingEntity target = p.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1),
                        e -> e.isAlive() && e != p && !(e instanceof Player) && e.getBoundingBox().inflate(0.5).clip(eye, end).isPresent())
                .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
        Vec3 at = target != null ? target.getBoundingBox().getCenter() : end;
        for (int i = 0; i < o.stars; i++) {
            Vec3 from = starPos(p, o, i, p.level().getGameTime());
            Vec3 d = at.subtract(from);
            if (d.length() > 0.3) {
                Vfx.play(p.level(), CORE, from.add(d.scale(0.5)), d, 0, (float) d.length());
                Vfx.play(p.level(), GLOW, from.add(d.scale(0.5)), d, 0, (float) d.length());
            }
            if (target != null) {
                float before = target.getHealth();
                target.damageCooldownTime = 0;
                target.hurtServer(p.level(), ModDamageTypes.source(target, o.school, null, p), o.launchDamage);
                if (target.getHealth() < before) {
                    MasteryService.onHit(p, o.spell, o.castId, target, !target.isAlive());
                    EchoService.onSpellHit(p, o.school, target, o.castId);
                }
            }
        }
        return true;
    }

    private static Vec3 starPos(Player p, Orbit o, int i, long now) {
        double a = now * SPIN + Math.PI * 2 * i / Math.max(1, o.stars);
        return p.position().add(Math.cos(a) * o.radius, 1.1, Math.sin(a) * o.radius);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || ORBITS.isEmpty()) return;
        long now = level.getGameTime();
        for (var e : List.copyOf(ORBITS.entrySet())) {
            Orbit o = e.getValue();
            if (o.level != level) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive() || p.level() != level || now >= o.until || o.stars <= 0) {
                ORBITS.remove(e.getKey());
                continue;
            }
            for (int i = 0; i < o.stars; i++) {
                Vec3 s = starPos(p, o, i, now);
                Vfx.play(level, STAR, s, Vec3.ZERO, 0, 1f);
            }
            catchProjectiles(p, o, level, now);
        }
    }

    private static void catchProjectiles(ServerPlayer p, Orbit o, ServerLevel level, long now) {
        for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(o.radius + CATCH_RADIUS + 1),
                pr -> pr.isAlive() && pr.getOwner() != p)) {
            Vec3 at = proj.position();
            boolean caught = false;
            for (int i = 0; i < o.stars && !caught; i++) caught = starPos(p, o, i, now).distanceTo(at) <= CATCH_RADIUS + proj.getDeltaMovement().length();
            if (!caught) continue;
            o.stars--;
            MasteryService.award(p, o.spell, 1);
            Fx.TWINKLE.play(level, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1);
            Vfx.play(level, BURST, at, Vec3.ZERO, 0, 1f);
            Vfx.play(level, NOVA, at, Vec3.ZERO, 0, 0.4f);
            if (o.reflect) {
                proj.setDeltaMovement(proj.getDeltaMovement().scale(-1));
                proj.setOwner(p);
                proj.needsSync = true;
            } else {
                proj.discard();
            }
            if (o.stars <= 0) return;
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ORBITS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        ORBITS.clear();
    }

    private OrbitBehavior() {}
}
