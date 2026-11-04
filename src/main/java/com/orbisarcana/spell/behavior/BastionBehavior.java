package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModBlocks;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.spell.Frost;
import com.orbisarcana.spell.Fx;
import com.orbisarcana.spell.Vfx;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code bastion}: a temporary ice wall across the caster's view (Ice Bastion, §14.5). Only fills air.
 * Params: width, height, duration_ticks; attacker_slow (5: enemies at the wall are slowed), wings (10: two side
 * leaves protect the flanks), shatter_damage (15: when the wall ends or falls, shards hit enemies around it).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
final class BastionBehavior {
    /** Signature effects (tools/vfx-effects.cjs): frost bursting where the wall rises and when it shatters. */
    private static final Identifier FLASH = Vfx.id("ice_flash"), SHATTER = Vfx.id("ice_shatter"), PUFF = Vfx.id("ice_puff");
    private static final double SLOW_REACH = 1.5;
    private static final int SLOW_TICKS = 40;
    private static final int WING_LENGTH = 3;
    private static final double SHATTER_RADIUS = 4.0;
    private static final List<Wall> WALLS = new ArrayList<>();

    private record Wall(ServerLevel level, UUID caster, Identifier spell, Identifier school, int castId, List<BlockPos> blocks,
            Vec3 center, boolean slow, float shatter, long endTick) {}

    static void release(CastContext ctx) {
        ServerLevel level = ctx.level();
        ServerPlayer caster = ctx.caster();
        Vec3 point = ctx.groundPoint() != null ? ctx.groundPoint() : caster.position().add(caster.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
        Vec3 fwd = caster.getLookAngle().multiply(1, 0, 1);
        fwd = fwd.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : fwd.normalize();
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        int width = Math.max(1, ctx.ticks("width"));
        int height = Math.max(1, ctx.ticks("height"));
        int duration = ctx.ticks("duration_ticks");

        Set<BlockPos> columns = new LinkedHashSet<>();
        double half = (width - 1) / 2.0;
        for (double i = -half; i <= half + 1.0E-6; i += 0.5) columns.add(BlockPos.containing(point.add(side.scale(i))));
        if (ctx.param("wings") > 0) {
            for (int sign = -1; sign <= 1; sign += 2) {
                Vec3 end = point.add(side.scale(sign * half));
                Vec3 wingDir = side.scale(sign).subtract(fwd).normalize(); // angled back toward the caster
                for (double j = 0.5; j <= WING_LENGTH; j += 0.5) columns.add(BlockPos.containing(end.add(wingDir.scale(j))));
            }
        }
        List<BlockPos> placed = new ArrayList<>();
        var wall = ModBlocks.FROST_WALL.get().defaultBlockState();
        for (BlockPos column : columns) {
            for (int y = 0; y < height; y++) {
                BlockPos pos = column.above(y);
                if (!level.getBlockState(pos).isAir()) continue;
                level.setBlockAndUpdate(pos, wall);
                level.scheduleTick(pos, wall.getBlock(), duration);
                placed.add(pos);
            }
        }
        Fx.FROST.play(level, point.x, point.y + 1, point.z, 50, half / 2 + 0.5, 0.8, half / 2 + 0.5);
        Vfx.play(level, PUFF, point.add(0, 0.5, 0), Vec3.ZERO, 0, 1f, (float) half / 2 + 0.5f, 0.5f, (float) half / 2 + 0.5f, 2);
        WALLS.add(new Wall(level, caster.getUUID(), ctx.spellId(), ctx.spell().school(), ctx.castId(), placed, point,
                ctx.param("attacker_slow") > 0, (float) ctx.scaled("shatter_damage"), level.getGameTime() + duration));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || WALLS.isEmpty()) return;
        long now = level.getGameTime();
        WALLS.removeIf(w -> {
            if (w.level() != level) return false;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(w.caster());
            boolean standing = w.blocks().stream().anyMatch(p -> level.getBlockState(p).is(ModBlocks.FROST_WALL.get()));
            if (now >= w.endTick() || !standing) {
                end(w, caster);
                return true;
            }
            if (w.slow() && now % 10 == 0) slowAttackers(w, caster);
            return false;
        });
    }

    private static void slowAttackers(Wall w, ServerPlayer caster) {
        Set<LivingEntity> near = new LinkedHashSet<>();
        for (BlockPos p : w.blocks()) {
            near.addAll(w.level().getEntitiesOfClass(LivingEntity.class, new AABB(p).inflate(SLOW_REACH),
                    e -> e.isAlive() && e != caster && !(e instanceof Player)));
        }
        for (LivingEntity e : near) {
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 1), caster);
            if (caster != null) MasteryService.onControl(caster, w.spell(), w.castId(), e);
        }
    }

    private static void end(Wall w, ServerPlayer caster) {
        for (BlockPos p : w.blocks()) {
            if (w.level().getBlockState(p).is(ModBlocks.FROST_WALL.get())) w.level().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
        if (w.shatter() <= 0) return;
        Vec3 c = w.center();
        for (LivingEntity e : w.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(SHATTER_RADIUS, 3, SHATTER_RADIUS),
                e -> e.isAlive() && e != caster && !(e instanceof Player) && e.position().distanceTo(c) <= SHATTER_RADIUS)) {
            float before = e.getHealth();
            e.hurtServer(w.level(), ModDamageTypes.source(e, w.school(), null, caster), w.shatter());
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 1), caster);
            if (caster != null && e.getHealth() < before) MasteryService.onHit(caster, w.spell(), w.castId(), e, !e.isAlive());
        }
        Fx.FROST.play(w.level(), c.x, c.y + 1.2, c.z, 60, 2, 1, 2);
        Vfx.play(w.level(), FLASH, c.add(0, 1.2, 0), Vec3.ZERO, 0, 2f);
        Vfx.play(w.level(), SHATTER, c.add(0, 1.2, 0), Vec3.ZERO, 0, 1.6f, 1.5f, 1, 1.5f, 3);
        Vfx.play(w.level(), PUFF, c.add(0, 1, 0), Vec3.ZERO, 0, 2f);
        com.orbisarcana.spell.Sfx.impact(w.level(), c, com.orbisarcana.school.Schools.ICE, 1.3f);
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        // blocks also carry a scheduled removal tick; this only clears the trackers
        WALLS.clear();
    }

    private BastionBehavior() {}
}
