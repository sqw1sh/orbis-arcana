package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.spell.Arcs;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code anchor}: a lightning trap on the ground that strikes when an enemy comes close (Storm Anchor, §14.6).
 * Params: damage, trigger_radius, life_ticks, max_anchors (5: two at once), arc_damage (10: an arc between two
 * anchors hurts whoever crosses it), storm_ticks (15: a strike leaves a storm of radius 4).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
final class AnchorBehavior {
    private static final int ARM_TICKS = 10;
    private static final int PULSE_TICKS = 10;
    private static final double ARC_MAX_LENGTH = 12;
    private static final double ARC_WIDTH = 0.8;
    private static final double STORM_RADIUS = 4;
    private static final float STORM_DAMAGE = 2f;
    private static final List<Anchor> ANCHORS = new ArrayList<>();
    private static final List<Storm> STORMS = new ArrayList<>();

    private record Anchor(ServerLevel level, UUID caster, Identifier spell, Identifier school, int castId, Vec3 pos,
            float damage, double radius, float arcDamage, int stormTicks, long armedAt, long endTick) {}

    private record Storm(Anchor from, long endTick) {}

    static void release(CastContext ctx) {
        Vec3 pos = ctx.groundPoint() != null ? ctx.groundPoint() : ctx.caster().position();
        long now = ctx.level().getGameTime();
        UUID caster = ctx.caster().getUUID();
        int max = Math.max(1, ctx.ticks("max_anchors"));
        List<Anchor> own = ANCHORS.stream().filter(a -> a.caster().equals(caster)).toList();
        for (int i = 0; i <= own.size() - max; i++) ANCHORS.remove(own.get(i)); // oldest go first
        ANCHORS.add(new Anchor(ctx.level(), caster, ctx.spellId(), ctx.spell().school(), ctx.castId(), pos,
                (float) ctx.scaled("damage"), ctx.param("trigger_radius"), (float) ctx.scaled("arc_damage"), ctx.ticks("storm_ticks"),
                now + ARM_TICKS, now + ctx.ticks("life_ticks")));
        ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.3, pos.z, 20, 0.3, 0.2, 0.3, 0.1);
    }

    /** For tests: anchors the player has out. */
    static long count(ServerPlayer player) {
        return ANCHORS.stream().filter(a -> a.caster().equals(player.getUUID())).count();
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || (ANCHORS.isEmpty() && STORMS.isEmpty())) return;
        long now = level.getGameTime();
        for (Anchor a : List.copyOf(ANCHORS)) {
            if (a.level() != level) continue;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(a.caster());
            if (now >= a.endTick() || caster == null) {
                ANCHORS.remove(a);
                continue;
            }
            if (now % 8 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, a.pos().x, a.pos().y + 0.15, a.pos().z, 2, 0.15, 0.05, 0.15, 0.02);
            if (now < a.armedAt()) continue;
            List<LivingEntity> near = enemies(a, caster, a.radius());
            if (!near.isEmpty()) {
                ANCHORS.remove(a);
                strike(a, caster);
            }
        }
        arcs(level, now);
        STORMS.removeIf(s -> {
            if (s.from().level() != level) return false;
            if (now >= s.endTick()) return true;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(s.from().caster());
            Vec3 c = s.from().pos();
            if (now % PULSE_TICKS == 0) {
                for (LivingEntity e : enemies(s.from(), caster, STORM_RADIUS)) {
                    hurt(s.from(), caster, e, STORM_DAMAGE);
                    Arcs.draw(level, c.add(0, 3, 0), Arcs.body(e));
                }
            }
            if (now % 2 == 0) {
                var rnd = level.getRandom();
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x + (rnd.nextDouble() - 0.5) * STORM_RADIUS * 2, c.y + 0.2 + rnd.nextDouble() * 2.5,
                        c.z + (rnd.nextDouble() - 0.5) * STORM_RADIUS * 2, 2, 0, 0, 0, 0.05);
            }
            return false;
        });
    }

    private static void strike(Anchor a, @Nullable ServerPlayer caster) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(a.level(), EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.setPos(a.pos());
            a.level().addFreshEntity(bolt);
        }
        for (LivingEntity e : enemies(a, caster, a.radius())) hurt(a, caster, e, a.damage());
        if (a.stormTicks() > 0) STORMS.add(new Storm(a, a.level().getGameTime() + a.stormTicks()));
    }

    /** Milestone 10: two anchors of one caster close enough are joined by an arc that hurts whoever crosses it. */
    private static void arcs(ServerLevel level, long now) {
        if (now % 5 != 0) return;
        for (int i = 0; i < ANCHORS.size(); i++) {
            Anchor a = ANCHORS.get(i);
            if (a.level() != level || a.arcDamage() <= 0) continue;
            for (int j = i + 1; j < ANCHORS.size(); j++) {
                Anchor b = ANCHORS.get(j);
                if (!b.caster().equals(a.caster()) || b.level() != level || a.pos().distanceTo(b.pos()) > ARC_MAX_LENGTH) continue;
                Vec3 p = a.pos().add(0, 0.8, 0), q = b.pos().add(0, 0.8, 0);
                Arcs.draw(level, p, q);
                if (now % PULSE_TICKS != 0) continue;
                ServerPlayer caster = level.getServer().getPlayerList().getPlayer(a.caster());
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p, q).inflate(ARC_WIDTH, 1, ARC_WIDTH),
                        e -> e.isAlive() && e != caster && !(e instanceof Player) && distToSegment(Arcs.body(e), p, q) <= ARC_WIDTH + e.getBbWidth() / 2)) {
                    hurt(a, caster, e, a.arcDamage());
                }
            }
        }
    }

    private static List<LivingEntity> enemies(Anchor a, @Nullable ServerPlayer caster, double radius) {
        Vec3 c = a.pos();
        return a.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius, 2.5, radius),
                e -> e.isAlive() && e != caster && !(e instanceof Player) && e.position().distanceToSqr(c.x, e.getY(), c.z) <= radius * radius);
    }

    private static void hurt(Anchor a, @Nullable ServerPlayer caster, LivingEntity e, float damage) {
        float before = e.getHealth();
        e.hurtServer(a.level(), ModDamageTypes.source(e, a.school(), null, caster), damage);
        if (caster != null && e.getHealth() < before) {
            MasteryService.onHit(caster, a.spell(), a.castId(), e, !e.isAlive());
            EchoService.onSpellHit(caster, a.school(), e, a.castId());
        }
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len < 1.0E-6 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        ANCHORS.clear();
        STORMS.clear();
    }

    private AnchorBehavior() {}
}
