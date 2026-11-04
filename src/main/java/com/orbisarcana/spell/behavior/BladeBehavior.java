package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.school.Schools;
import com.orbisarcana.school.TalentEffects;
import com.orbisarcana.spell.Fx;
import com.orbisarcana.spell.Vfx;

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
 * {@code blade}: a cutting arc of wind (Wind Blade, §14.4). Server object, not an entity (§19.1).
 * Params: damage, speed, range, width; returns (5: flies back to the caster), return_hits (10: cuts again on the
 * way back), tornado_ticks (15: leaves a mini-tornado where it turned). Also hosts the Air "Storm Trail" stream.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class BladeBehavior {
    /** Signature effects (tools/vfx-effects.cjs): the flying crescent of wind and its wisps. */
    private static final Identifier CRESCENT = Vfx.id("wind_crescent"), WISPS = Vfx.id("wind_wisps");
    private static final int MAX_LIFE_TICKS = 100;
    private static final double CATCH_DISTANCE = 1.5;
    private static final double TORNADO_RADIUS = 1.8;
    private static final float TORNADO_DAMAGE = 1.5f;
    private static final double TORNADO_PULL = 0.15;
    private static final int PULSE_TICKS = 10;
    private static final double TRAIL_WIDTH = 1.2;
    private static final float TRAIL_DAMAGE = 2f;
    private static final int TRAIL_TICKS = 40;
    private static final List<Gust> GUSTS = new ArrayList<>();

    /** A server-side wind object; returns false when it is done. */
    private interface Gust {
        ServerLevel level();

        boolean tick(long now);
    }

    static void release(CastContext ctx) {
        ServerPlayer caster = ctx.caster();
        double speed = Math.max(0.1, ctx.param("speed")) * TalentEffects.projectileSpeedMult(caster);
        GUSTS.add(new Blade(ctx, caster.getEyePosition().subtract(0, 0.3, 0), caster.getLookAngle(), speed));
    }

    /** Air talent "Storm Trail": a cutting stream along a dash from {@code from} to {@code to}. */
    public static void stormTrail(ServerPlayer caster, Vec3 from, Vec3 to) {
        GUSTS.add(new Trail(caster.level(), caster.getUUID(), from, to, caster.level().getGameTime() + TRAIL_TICKS));
    }

    private static final class Blade implements Gust {
        final ServerLevel level;
        final UUID caster;
        final Identifier spell, school;
        final int castId;
        final float damage;
        final double speed, range, width;
        final boolean returns, returnHits;
        final int tornadoTicks;
        final long endTick;
        final Set<UUID> hit = new HashSet<>();
        Vec3 pos, dir;
        double travelled;
        boolean back;

        Blade(CastContext ctx, Vec3 pos, Vec3 dir, double speed) {
            this.level = ctx.level();
            this.caster = ctx.caster().getUUID();
            this.spell = ctx.spellId();
            this.school = ctx.spell().school();
            this.castId = ctx.castId();
            this.damage = (float) ctx.scaled("damage");
            this.speed = speed;
            this.range = Math.max(1, ctx.param("range"));
            this.width = Math.max(0.3, ctx.param("width"));
            this.returns = ctx.param("returns") > 0;
            this.returnHits = ctx.param("return_hits") > 0;
            this.tornadoTicks = ctx.ticks("tornado_ticks");
            this.endTick = level.getGameTime() + MAX_LIFE_TICKS;
            this.pos = pos;
            this.dir = dir.normalize();
        }

        @Override
        public ServerLevel level() {
            return level;
        }

        @Override
        public boolean tick(long now) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(caster);
            if (now >= endTick || (back && owner == null)) return false;
            if (back) {
                Vec3 home = owner.getEyePosition().subtract(0, 0.3, 0);
                if (home.distanceTo(pos) <= CATCH_DISTANCE) return false;
                dir = home.subtract(pos).normalize();
            }
            Vec3 next = pos.add(dir.scale(speed));
            boolean blocked = false;
            if (!back) {
                var clip = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
                if (clip.getType() != HitResult.Type.MISS) {
                    next = clip.getLocation().subtract(dir.scale(0.2));
                    blocked = true;
                }
            }
            cut(owner, pos, next);
            pos = next;
            travelled += speed;
            particles();
            if (!back && (blocked || travelled >= range)) {
                if (tornadoTicks > 0) GUSTS.add(new Tornado(this, now + tornadoTicks));
                if (!returns) return false;
                back = true;
                if (returnHits) hit.clear();
            }
            return true;
        }

        private void cut(ServerPlayer owner, Vec3 from, Vec3 to) {
            AABB box = new AABB(from, to).inflate(width);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && e != owner && !hit.contains(e.getUUID()) && distToSegment(e.getBoundingBox().getCenter(), from, to) <= width + e.getBbWidth() / 2)) {
                hit.add(e.getUUID());
                if (back) e.damageCooldownTime = 0; // a quick bounce off a wall must still cut on the way back
                hurt(level, owner, spell, school, castId, e, damage, true);
                e.push(dir.x * 0.3, 0.15, dir.z * 0.3);
            }
        }

        private void particles() {
            // the crescent lies flat along the flight, as wide as the blade; wisps peel off behind it
            Vfx.play(level, CRESCENT, pos, dir, 0, (float) Math.max(0.6, width));
            Vfx.play(level, WISPS, pos, dir, 0, (float) Math.max(0.6, width));
        }
    }

    private static final class Tornado implements Gust {
        final ServerLevel level;
        final UUID caster;
        final Identifier spell, school;
        final int castId;
        final Vec3 center;
        final long endTick;

        Tornado(Blade b, long endTick) {
            this.level = b.level;
            this.caster = b.caster;
            this.spell = b.spell;
            this.school = b.school;
            this.castId = b.castId;
            this.center = new Vec3(b.pos.x, Math.floor(b.pos.y - 1), b.pos.z);
            this.endTick = endTick;
        }

        @Override
        public ServerLevel level() {
            return level;
        }

        @Override
        public boolean tick(long now) {
            if (now >= endTick) return false;
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(caster);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(TORNADO_RADIUS, 3, TORNADO_RADIUS),
                    e -> e.isAlive() && e != owner && !(e instanceof Player))) {
                Vec3 to = center.subtract(e.position()).multiply(1, 0, 1);
                if (to.lengthSqr() > 0.2) e.push(to.normalize().scale(TORNADO_PULL));
                e.push(0, 0.04, 0);
                if (now % PULSE_TICKS == 0) hurt(level, owner, spell, school, castId, e, TORNADO_DAMAGE, true);
            }
            double a = now * 0.6;
            for (double h = 0; h < 2.5; h += 0.4) {
                double r = 0.3 + h * 0.3;
                Fx.CLOUD.play(level, center.x + Math.cos(a + h * 2) * r, center.y + h, center.z + Math.sin(a + h * 2) * r, 1, 0, 0, 0);
            }
            return true;
        }
    }

    private record Trail(ServerLevel level, UUID caster, Vec3 from, Vec3 to, long endTick) implements Gust {
        @Override
        public boolean tick(long now) {
            if (now >= endTick) return false;
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(caster);
            if (now % PULSE_TICKS == 0) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(TRAIL_WIDTH, 1, TRAIL_WIDTH),
                        e -> e.isAlive() && e != owner && !(e instanceof Player) && distToSegment(e.position(), from, to) <= TRAIL_WIDTH)) {
                    hurt(level, owner, null, Schools.AIR, 0, e, TRAIL_DAMAGE, false);
                }
            }
            if (now % 3 == 0) {
                double t = level.getRandom().nextDouble();
                Vec3 p = from.lerp(to, t);
                Fx.SLASH.play(level, p.x, p.y + 0.4, p.z, 1, 0.2, 0.1, 0.2);
            }
            return true;
        }
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len < 1.0E-6 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    private static void hurt(ServerLevel level, ServerPlayer owner, Identifier spell, Identifier school, int castId,
            LivingEntity e, float damage, boolean progress) {
        float before = e.getHealth();
        e.hurtServer(level, ModDamageTypes.source(e, school, null, owner), damage);
        if (progress && owner != null && spell != null && e.getHealth() < before) {
            MasteryService.onHit(owner, spell, castId, e, !e.isAlive());
            EchoService.onSpellHit(owner, school, e, castId);
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || GUSTS.isEmpty()) return;
        long now = level.getGameTime();
        // copy: a blade may add a tornado while ticking
        for (Gust g : List.copyOf(GUSTS)) {
            if (g.level() == level && !g.tick(now)) GUSTS.remove(g);
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        GUSTS.clear();
    }

    private BladeBehavior() {}
}
