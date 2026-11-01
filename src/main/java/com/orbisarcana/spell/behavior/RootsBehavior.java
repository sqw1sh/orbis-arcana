package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code roots}: roots hold the target in place (Root Shackles, §14.7). Params: hold_ticks; spread_radius (5: the
 * roots also grab the nearest other enemy), damage_per_second (10: thorns), after_slow_ticks (15: slowed after).
 * Also used by the Nature talent "Wild Growth" through {@link #entangle}.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class RootsBehavior {
    private static final BlockParticleOption ROOT = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
    private static final List<Hold> HOLDS = new ArrayList<>();

    private record Hold(ServerLevel level, LivingEntity target, @Nullable UUID caster, Vec3 anchor, long endTick,
            float damagePerSecond, int afterSlow, @Nullable Identifier spell, Identifier school, int castId) {}

    static void release(CastContext ctx) {
        LivingEntity target = ctx.target();
        if (target == null || target == ctx.caster()) return;
        ServerPlayer caster = ctx.caster();
        grab(ctx, target);
        double spread = ctx.param("spread_radius");
        if (spread > 0) {
            ctx.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(spread),
                            e -> e.isAlive() && e != target && e != caster && !(e instanceof Player))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(target)))
                    .ifPresent(next -> grab(ctx, next));
        }
    }

    private static void grab(CastContext ctx, LivingEntity target) {
        add(ctx.level(), target, ctx.caster(), ctx.ticks("hold_ticks"), (float) ctx.scaled("damage_per_second"),
                ctx.ticks("after_slow_ticks"), ctx.spellId(), ctx.spell().school(), ctx.castId());
        MasteryService.onControl(ctx.caster(), ctx.spellId(), ctx.castId(), target);
        EchoService.onEffectiveCast(ctx.caster(), ctx.spell().school(), target.position(), ctx.castId());
    }

    /** Holds {@code target} for {@code ticks} without damage (talents). */
    public static void entangle(ServerPlayer caster, LivingEntity target, int ticks, Identifier school) {
        add(caster.level(), target, caster, ticks, 0, 0, null, school, 0);
    }

    private static void add(ServerLevel level, LivingEntity target, @Nullable ServerPlayer caster, int ticks, float dps, int afterSlow,
            @Nullable Identifier spell, Identifier school, int castId) {
        HOLDS.removeIf(h -> h.target() == target);
        HOLDS.add(new Hold(level, target, caster == null ? null : caster.getUUID(), target.position(), level.getGameTime() + ticks,
                dps, afterSlow, spell, school, castId));
        level.sendParticles(ROOT, target.getX(), target.getY() + 0.2, target.getZ(), 25, target.getBbWidth() / 2, 0.2, target.getBbWidth() / 2, 0.1);
    }

    public static boolean isHeld(LivingEntity e) {
        return HOLDS.stream().anyMatch(h -> h.target() == e);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || HOLDS.isEmpty()) return;
        long now = level.getGameTime();
        HOLDS.removeIf(h -> {
            if (h.level() != level) return false;
            LivingEntity t = h.target();
            if (!t.isAlive() || t.isRemoved()) return true;
            ServerPlayer caster = h.caster() == null ? null : level.getServer().getPlayerList().getPlayer(h.caster());
            if (now >= h.endTick()) {
                if (h.afterSlow() > 0) t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, h.afterSlow(), 1), caster);
                return true;
            }
            if (t instanceof Player) {
                t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 9, false, false));
            } else {
                t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
                if (t.position().distanceToSqr(h.anchor()) > 0.04) t.teleportTo(h.anchor().x, h.anchor().y, h.anchor().z);
            }
            if (h.damagePerSecond() > 0 && now % 20 == 0) {
                float before = t.getHealth();
                t.hurtServer(level, ModDamageTypes.source(t, h.school(), null, caster), h.damagePerSecond());
                if (caster != null && h.spell() != null && t.getHealth() < before) {
                    MasteryService.onHit(caster, h.spell(), h.castId(), t, !t.isAlive());
                    EchoService.onSpellHit(caster, h.school(), t, h.castId());
                }
            }
            if (now % 4 == 0) level.sendParticles(ROOT, t.getX(), t.getY() + 0.1, t.getZ(), 4, t.getBbWidth() / 2, 0.1, t.getBbWidth() / 2, 0.02);
            return false;
        });
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        HOLDS.clear();
    }

    private RootsBehavior() {}
}
