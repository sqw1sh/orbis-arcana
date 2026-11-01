package com.orbisarcana.spell;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.EchoRules;
import com.orbisarcana.core.TalentRules;
import com.orbisarcana.network.CastPayloads.EchoState;
import com.orbisarcana.network.Net;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.AttunementService;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.school.Schools;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Astral stars (§14.10, §10.5 "Constellation"): points in the world owned by a caster, at most 3 (4 with "Celestial
 * Geometry"), living 8 s (12 with "First Star"). A full set collapses into a gravity pulse in the figure they make
 * when the Astral Echo is on, or when the spell that placed the last star asks for it. Server objects (§19.1).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class Stars {
    /** {@code arc}: damage per second of arcs to the neighbouring stars (Star Spike milestone 10). */
    public record Star(UUID caster, ServerLevel level, Vec3 pos, long until, float arc) {}

    private static final List<Star> STARS = new ArrayList<>();

    /**
     * Places a star. {@code collapse}: pulse damage the placing spell brings to a full set (0 for none); the Echo pulses
     * a full set anyway.
     */
    public static void place(ServerPlayer caster, Vec3 pos, float arc, float collapse) {
        ServerLevel level = caster.level();
        long life = AttunementService.hasTalent(caster, Schools.ASTRAL, TalentRules.ROOT) ? EchoRules.FIRST_STAR_TICKS : EchoRules.STAR_TICKS;
        int max = max(caster);
        List<Star> own = of(caster);
        if (own.size() >= max) STARS.remove(own.getFirst());
        STARS.add(new Star(caster.getUUID(), level, pos, level.getGameTime() + life, arc));
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 12, 0.1, 0.1, 0.1, 0.08);
        own = of(caster);
        int tier = EchoService.tier(caster, Schools.ASTRAL);
        if (own.size() >= EchoRules.STARS_FOR_PULSE && (tier > 0 || collapse > 0)) {
            float damage = (float) Math.max(collapse, tier > 0 ? EchoRules.pulseDamage(tier, AttunementService.isDissonant(caster)) : 0);
            if (AttunementService.hasTalent(caster, Schools.ASTRAL, TalentRules.A1)) damage *= (float) EchoRules.STAR_ORBIT_MULT;
            // Celestial Geometry: wait for the 4th star, a diamond pulses twice
            if (own.size() >= max) {
                int pulses = own.size() >= 4 ? 2 : 1;
                for (int i = 0; i < pulses; i++) pulse(caster, own, damage);
                STARS.removeAll(own);
            }
        }
        send(caster);
    }

    public static int max(ServerPlayer caster) {
        return AttunementService.hasTalent(caster, Schools.ASTRAL, TalentRules.A2) ? EchoRules.STARS_MAX_GEOMETRY : EchoRules.STARS_MAX;
    }

    public static List<Star> of(ServerPlayer caster) {
        return STARS.stream().filter(s -> s.caster().equals(caster.getUUID()) && s.level() == caster.level()).toList();
    }

    public static boolean near(ServerPlayer player, double radius) {
        return of(player).stream().anyMatch(s -> s.pos().distanceTo(player.position()) <= radius);
    }

    /** Gravity pulse in the figure: enemies inside are pulled to the centre and hurt. B2 "Null Moment": so is the caster. */
    private static void pulse(ServerPlayer caster, List<Star> figure, float damage) {
        ServerLevel level = caster.level();
        Vec3 centre = figure.stream().map(Star::pos).reduce(Vec3.ZERO, Vec3::add).scale(1.0 / figure.size());
        double radius = figure.stream().mapToDouble(s -> s.pos().distanceTo(centre)).max().orElse(2) + 1;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(radius),
                e -> e.isAlive() && !(e instanceof Player) && e.position().distanceTo(centre) <= radius)) {
            Vec3 in = centre.subtract(e.position());
            Vec3 pull = in.lengthSqr() < 1.0E-4 ? Vec3.ZERO : in.normalize().scale(Math.min(1.2, in.length() * 0.35));
            e.push(pull.x, 0.2, pull.z);
            e.damageCooldownTime = 0;
            e.hurtServer(level, ModDamageTypes.source(e, Schools.ASTRAL, null, caster), damage);
        }
        if (AttunementService.hasTalent(caster, Schools.ASTRAL, TalentRules.B2) && caster.position().distanceTo(centre) <= EchoRules.NULL_MOMENT_RANGE) {
            Vec3 to = centre.subtract(caster.position());
            caster.setDeltaMovement(to.normalize().scale(Math.min(2.0, to.length() * 0.3)).add(0, 0.4, 0));
            caster.syncVelocity = true;
        }
        for (Star s : figure) line(level, s.pos(), centre, ParticleTypes.REVERSE_PORTAL);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y + 0.5, centre.z, 80, radius / 2, 0.5, radius / 2, 0.1);
        level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y + 0.5, centre.z, 30, 0.3, 0.3, 0.3, 0.2);
    }

    private static void line(ServerLevel level, Vec3 a, Vec3 b, net.minecraft.core.particles.SimpleParticleType type) {
        Vec3 d = b.subtract(a);
        int n = (int) Math.ceil(d.length() / 0.4);
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale((double) i / Math.max(1, n)));
            level.sendParticles(type, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private static void send(ServerPlayer caster) {
        if (EchoService.tier(caster, Schools.ASTRAL) > 0) Net.toPlayer(caster, new EchoState(Schools.ASTRAL, of(caster).size(), max(caster)));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || STARS.isEmpty()) return;
        long now = level.getGameTime();
        List<Star> expired = STARS.stream().filter(s -> s.level() == level && now >= s.until()).toList();
        if (!expired.isEmpty()) {
            STARS.removeAll(expired);
            expired.stream().map(Star::caster).distinct().forEach(id -> {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null) send(p);
            });
        }
        if (now % 4 == 0) {
            for (Star s : STARS) {
                if (s.level() == level) level.sendParticles(ParticleTypes.END_ROD, s.pos().x, s.pos().y, s.pos().z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
        if (now % 20 != 0) return;
        // Arcs between neighbouring stars of one caster
        for (int i = 0; i < STARS.size(); i++) {
            Star a = STARS.get(i);
            if (a.level() != level || a.arc() <= 0) continue;
            for (int j = i + 1; j < STARS.size(); j++) {
                Star b = STARS.get(j);
                if (!b.caster().equals(a.caster()) || b.level() != level || b.arc() <= 0) continue;
                ServerPlayer caster = level.getServer().getPlayerList().getPlayer(a.caster());
                if (caster == null) continue;
                float dmg = Math.min(a.arc(), b.arc());
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a.pos(), b.pos()).inflate(1),
                        e -> e.isAlive() && !(e instanceof Player) && e.getBoundingBox().inflate(0.3).clip(a.pos(), b.pos()).isPresent())) {
                    e.hurtServer(level, ModDamageTypes.source(e, Schools.ASTRAL, null, caster), dmg);
                }
                line(level, a.pos(), b.pos(), ParticleTypes.ELECTRIC_SPARK);
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STARS.removeIf(s -> s.caster().equals(event.getEntity().getUUID()));
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        STARS.clear();
    }

    private Stars() {}
}
