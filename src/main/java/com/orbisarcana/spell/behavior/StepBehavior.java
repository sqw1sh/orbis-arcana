package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.school.TalentEffects;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import com.orbisarcana.entity.ShadowDecoy;
import com.orbisarcana.registry.ModEntities;
import com.orbisarcana.spell.Fx;
import com.orbisarcana.spell.Vfx;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code step}: Shadow Step (§14.9), a teleport to the point you look at. Params: range; decoy_ticks (5: an illusion
 * stays behind and draws the mobs that hunted you), return_ticks (10: casting again within this time takes you back,
 * free), fear_ticks + fear_radius (15: mobs around the exit flee).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class StepBehavior {
    /** Signature effects (tools/vfx-effects.cjs): shadows rushing in where the caster leaves, a dark ring where they land. */
    private static final Identifier IMPLODE = Vfx.id("dusk_implode"), RING = Vfx.id("dusk_ring");
    private static final String DECOY_TAG = "orbisarcana_decoy";
    private static final double DECOY_LURE_RADIUS = 16;
    private static final double FLEE_DISTANCE = 8;
    private static final double FLEE_SPEED = 1.3;

    private record Return(Identifier spell, ServerLevel level, Vec3 origin, long until) {}

    private record Decoy(ServerLevel level, UUID id, long until) {}

    private record Fear(PathfinderMob mob, Vec3 from, long until) {}

    private static final Map<UUID, Return> RETURNS = new HashMap<>();
    private static final List<Decoy> DECOYS = new ArrayList<>();
    private static final List<Fear> FEARS = new ArrayList<>();

    static void release(CastContext ctx) {
        ServerPlayer p = ctx.caster();
        Vec3 origin = p.position();
        Vec3 dest = destination(p, ctx.param("range"));
        if (dest == null) return;
        long now = ctx.level().getGameTime();
        int decoyTicks = ctx.ticks("decoy_ticks");
        if (decoyTicks > 0) decoy(p, origin, now + decoyTicks);
        jump(p, dest);
        MasteryService.onMove(p, ctx.spellId(), origin.distanceTo(dest));
        int back = ctx.ticks("return_ticks");
        if (back > 0) RETURNS.put(p.getUUID(), new Return(ctx.spellId(), ctx.level(), origin, now + back));
        int fear = ctx.ticks("fear_ticks");
        if (fear > 0) {
            for (PathfinderMob mob : ctx.level().getEntitiesOfClass(PathfinderMob.class, p.getBoundingBox().inflate(ctx.param("fear_radius")),
                    m -> m.isAlive() && m.distanceTo(p) <= ctx.param("fear_radius"))) {
                FEARS.removeIf(f -> f.mob() == mob);
                FEARS.add(new Fear(mob, dest, now + fear));
                mob.setTarget(null);
            }
            Fx.SHADOW.play(ctx.level(), dest.x, dest.y + 0.5, dest.z, 30, ctx.param("fear_radius") / 2, 0.3, ctx.param("fear_radius") / 2);
            Vfx.play(ctx.level(), RING, dest.add(0, 0.5, 0), Vec3.ZERO, 0, (float) Math.max(1, ctx.param("fear_radius") / 2));
        }
    }

    /**
     * Milestone 10: a second press within the return window takes the caster back, ignoring the cooldown. Called by
     * the cast service before any rule.
     */
    public static boolean tryReturn(ServerPlayer p, Identifier spell) {
        Return r = RETURNS.get(p.getUUID());
        if (r == null || !r.spell().equals(spell)) return false;
        RETURNS.remove(p.getUUID());
        if (r.level() != p.level() || p.level().getGameTime() >= r.until()) return false;
        jump(p, r.origin());
        return true;
    }

    /** Teleports with shadow puffs at both ends; Dark B2 "Blind Spot" makes mobs lose you. */
    private static void jump(ServerPlayer p, Vec3 to) {
        ServerLevel level = p.level();
        Fx.SHADOW.play(level, p.getX(), p.getY() + 1, p.getZ(), 25, 0.3, 0.6, 0.3);
        Vfx.play(level, IMPLODE, p.position().add(0, 1, 0), Vec3.ZERO, 0, 1f);
        p.teleportTo(to.x, to.y, to.z);
        p.resetFallDistance();
        Fx.SHADOW.play(level, to.x, to.y + 1, to.z, 25, 0.3, 0.6, 0.3);
        Vfx.play(level, RING, to.add(0, 1, 0), Vec3.ZERO, 0, 1.2f);
        level.playSound(null, to.x, to.y, to.z, com.orbisarcana.registry.ModSounds.release(com.orbisarcana.school.Schools.DARK), SoundSource.PLAYERS, 1.2f, 0.85f);
        TalentEffects.blindSpot(p);
    }

    /** Where the player can stand along the look ray, up to {@code range}; null if nowhere. */
    static @Nullable Vec3 destination(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        var hit = p.level().clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double dist = hit.getType() == HitResult.Type.MISS ? range : hit.getLocation().distanceTo(eye) - 0.6;
        for (double d = dist; d > 1; d -= 0.5) {
            Vec3 at = eye.add(dir.scale(d)).subtract(0, p.getEyeHeight(), 0);
            // land on the ground below if it is close
            for (int drop = 0; drop <= 3; drop++) {
                Vec3 feet = at.subtract(0, drop, 0);
                if (!fits(p, feet)) break;
                if (!fits(p, feet.subtract(0, 0.1, 0))) return feet;
            }
            if (fits(p, at)) return at;
        }
        return null;
    }

    private static boolean fits(ServerPlayer p, Vec3 feet) {
        return p.level().noCollision(p, p.getBoundingBox().move(feet.subtract(p.position())));
    }

    private static void decoy(ServerPlayer p, Vec3 at, long until) {
        ServerLevel level = p.level();
        ShadowDecoy stand = ModEntities.SHADOW_DECOY.get().create(level, EntitySpawnReason.TRIGGERED);
        if (stand == null) return;
        stand.snapTo(at.x, at.y, at.z, p.getYRot(), 0);
        stand.setYHeadRot(p.getYRot());
        stand.setYBodyRot(p.getYRot());
        stand.setLife((int) (until - level.getGameTime()));
        stand.addTag(DECOY_TAG);
        stand.setCustomName(p.getName().copy().append(Component.literal("?")));
        level.addFreshEntity(stand);
        DECOYS.add(new Decoy(level, stand.getUUID(), until));
        for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(DECOY_LURE_RADIUS), m -> m.getTarget() == p)) {
            mob.setTarget(stand);
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        DECOYS.removeIf(d -> {
            if (d.level() != level || now < d.until()) return false;
            var e = level.getEntity(d.id());
            if (e != null) {
                Fx.SHADOW.play(level, e.getX(), e.getY() + 1, e.getZ(), 20, 0.3, 0.6, 0.3);
                e.discard();
            }
            return true;
        });
        RETURNS.values().removeIf(r -> r.level() == level && now >= r.until());
        if (now % 5 != 0) return;
        FEARS.removeIf(f -> {
            PathfinderMob m = f.mob();
            if (m.level() != level) return false;
            if (!m.isAlive() || now >= f.until()) return true;
            Vec3 away = m.position().subtract(f.from()).multiply(1, 0, 1);
            Vec3 dir = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            Vec3 to = m.position().add(dir.scale(FLEE_DISTANCE));
            m.setTarget(null);
            m.getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
            return false;
        });
    }

    public static boolean isFeared(Mob mob) {
        return FEARS.stream().anyMatch(f -> f.mob() == mob);
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        for (Decoy d : DECOYS) {
            var e = d.level().getEntity(d.id());
            if (e != null) e.discard();
        }
        DECOYS.clear();
        RETURNS.clear();
        FEARS.clear();
    }

    private StepBehavior() {}
}
