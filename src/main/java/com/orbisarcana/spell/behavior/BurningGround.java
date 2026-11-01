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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Burning ground left by fireball milestone 10 (§14.1). A server-side area, not fire blocks, so it never
 * spreads or damages the world (§1.4): it sets creatures standing in it on fire and burns them lightly.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class BurningGround {
    private static final int PULSE_TICKS = 10;
    private static final float PULSE_DAMAGE = 1.0f;
    private static final int IGNITE_TICKS = 40;
    private static final List<Patch> PATCHES = new ArrayList<>();

    private record Patch(ServerLevel level, Vec3 center, double radius, long endTick, UUID caster,
                         Identifier school, Identifier spell, int castId) {}

    public static void spawn(ServerLevel level, Vec3 center, double radius, int ticks, ServerPlayer caster,
                             Identifier school, Identifier spell, int castId) {
        PATCHES.add(new Patch(level, center, radius, level.getGameTime() + ticks, caster.getUUID(), school, spell, castId));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || PATCHES.isEmpty()) return;
        long now = level.getGameTime();
        PATCHES.removeIf(p -> p.level() == level && now >= p.endTick());
        for (Patch p : PATCHES) {
            if (p.level() != level) continue;
            if (now % 4 == 0) {
                level.sendParticles(ParticleTypes.FLAME, p.center().x, p.center().y + 0.1, p.center().z,
                        (int) (p.radius() * 4), p.radius() / 2, 0.05, p.radius() / 2, 0.01);
            }
            if (now % PULSE_TICKS == 0) burn(level, p);
        }
    }

    private static void burn(ServerLevel level, Patch p) {
        ServerPlayer caster = level.getServer().getPlayerList().getPlayer(p.caster());
        AABB box = new AABB(p.center(), p.center()).inflate(p.radius(), 1.0, p.radius());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != caster && horizontalDistSqr(e.position(), p.center()) <= p.radius() * p.radius())) {
            float before = e.getHealth();
            e.igniteForTicks(IGNITE_TICKS);
            e.hurtServer(level, ModDamageTypes.source(e, p.school(), null, caster), PULSE_DAMAGE);
            if (caster != null && e.getHealth() < before) MasteryService.onHit(caster, p.spell(), p.castId(), e, !e.isAlive());
        }
    }

    private static double horizontalDistSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        PATCHES.clear();
    }

    private BurningGround() {}
}
