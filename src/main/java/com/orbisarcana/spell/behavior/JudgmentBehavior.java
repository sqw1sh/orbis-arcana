package com.orbisarcana.spell.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.school.TalentEffects;
import com.orbisarcana.spell.Fx;
import com.orbisarcana.spell.Vfx;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code judgment}: Judgment of Heaven (§14.8), a column of light strikes the ground point and marks the enemies it
 * hits (Glowing, which the Light talent "Exposure" punishes). Params: damage, radius; linger_ticks (5: the column
 * stays, burning every half second), small_columns (10: that many small columns strike around it), hallowed_ticks
 * (15: hallowed ground heals allies). Columns are server objects (§19.1).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class JudgmentBehavior {
    /** Volumetric effects: the column of light from the sky. */
    private static final Identifier COLUMN = Vfx.id("judgment_column");
    /** Signature effects (tools/vfx-effects.cjs): the golden rune marking the ground, the column of light from the sky. */
    private static final Identifier RUNE = Vfx.id("judgment_rune"), CORE = Vfx.id("ray_core"), GLOW = Vfx.id("ray_glow"),
            BURST = Vfx.id("dawn_burst"), RING = Vfx.id("dawn_ring");
    private static final int MARK_TICKS = 100;
    private static final double LINGER_DAMAGE_MULT = 0.25;
    private static final int LINGER_PULSE = 10;
    private static final double SMALL_DISTANCE = 4.0;
    private static final double SMALL_RADIUS_MULT = 0.5;
    private static final double SMALL_DAMAGE_MULT = 0.4;
    private static final int SMALL_DELAY = 10;
    private static final float HALLOWED_HEAL = 1f;
    private static final int COLUMN_HEIGHT = 12;

    /** A column: strikes at {@code strikeAt}, then lingers and/or hallows until {@code endTick}. */
    private static final class Column {
        final ServerLevel level;
        final UUID caster;
        final Identifier spell, school;
        final int castId;
        final Vec3 pos;
        final double radius;
        final float damage;
        final long strikeAt, lingerUntil, hallowedUntil;
        boolean struck;

        Column(CastContext ctx, Vec3 pos, double radius, float damage, long strikeAt, int linger, int hallowed) {
            this.level = ctx.level();
            this.caster = ctx.caster().getUUID();
            this.spell = ctx.spellId();
            this.school = ctx.spell().school();
            this.castId = ctx.castId();
            this.pos = pos;
            this.radius = radius;
            this.damage = damage;
            this.strikeAt = strikeAt;
            this.lingerUntil = strikeAt + linger;
            this.hallowedUntil = strikeAt + hallowed;
        }

        long end() {
            return Math.max(strikeAt, Math.max(lingerUntil, hallowedUntil));
        }
    }

    private static final List<Column> COLUMNS = new ArrayList<>();

    static void release(CastContext ctx) {
        Vec3 point = ctx.groundPoint() != null ? ctx.groundPoint() : ctx.caster().position();
        long now = ctx.level().getGameTime();
        double radius = ctx.param("radius");
        float damage = (float) ctx.scaled("damage");
        COLUMNS.add(new Column(ctx, point, radius, damage, now, ctx.ticks("linger_ticks"), ctx.ticks("hallowed_ticks")));
        int small = ctx.ticks("small_columns");
        for (int i = 0; i < small; i++) {
            double a = Math.PI * 2 * i / small + ctx.caster().getRandom().nextDouble() * 0.5;
            Vec3 p = point.add(Math.cos(a) * SMALL_DISTANCE, 0, Math.sin(a) * SMALL_DISTANCE);
            COLUMNS.add(new Column(ctx, p, radius * SMALL_RADIUS_MULT, (float) (damage * SMALL_DAMAGE_MULT), now + SMALL_DELAY, 0, 0));
        }
        // The main column strikes at once, so its first hit lands in the cast tick
        tickColumns(ctx.level(), now);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && !COLUMNS.isEmpty()) tickColumns(level, level.getGameTime());
    }

    private static void tickColumns(ServerLevel level, long now) {
        COLUMNS.removeIf(c -> {
            if (c.level != level) return false;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(c.caster);
            if (caster == null) return true;
            if (now < c.strikeAt) return false;
            if (!c.struck) {
                c.struck = true;
                strike(c, caster, c.damage, true);
                beam(c, 40);
                com.orbisarcana.spell.Sfx.impact(level, c.pos, com.orbisarcana.school.Schools.LIGHT, 1.3f);
            } else if (now > c.strikeAt && now < c.lingerUntil && (now - c.strikeAt) % LINGER_PULSE == 0) {
                strike(c, caster, (float) (c.damage * LINGER_DAMAGE_MULT), false);
            }
            if (now < c.lingerUntil && now % 2 == 0) beam(c, 6);
            if (now < c.hallowedUntil) hallow(c, caster, now);
            return now >= c.end();
        });
    }

    private static void strike(Column c, ServerPlayer caster, float damage, boolean mark) {
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, area(c),
                e -> e.isAlive() && !(e instanceof Player) && horizontal(e.position(), c.pos) <= c.radius + e.getBbWidth() / 2)) {
            float before = e.getHealth();
            e.hurtServer(c.level, ModDamageTypes.source(e, c.school, null, caster), damage);
            if (mark && e.isAlive()) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_TICKS, 0, false, false), caster);
            if (e.getHealth() < before) {
                MasteryService.onHit(caster, c.spell, c.castId, e, !e.isAlive());
                EchoService.onSpellHit(caster, c.school, e, c.castId);
            }
        }
    }

    private static void hallow(Column c, ServerPlayer caster, long now) {
        if (now % 10 == 0) {
            Vfx.play(c.level, RUNE, c.pos.add(0, 0.06, 0), Vec3.ZERO, 0, (float) Math.max(1, c.radius / 1.6));
            for (int i = 0; i < 4; i++) {
                double a = Math.PI * 2 * i / 4 + now * 0.05;
                Fx.MOTE.play(c.level, c.pos.x + Math.cos(a) * c.radius, c.pos.y + 0.1, c.pos.z + Math.sin(a) * c.radius, 1, 0, 0.02, 0);
            }
        }
        if ((now - c.strikeAt) % 20 != 0 || now == c.strikeAt) return;
        float heal = (float) (HALLOWED_HEAL * TalentEffects.healMult(caster));
        for (Player ally : c.level.getEntitiesOfClass(Player.class, area(c), p -> p.isAlive() && horizontal(p.position(), c.pos) <= c.radius)) {
            float missing = ally.getMaxHealth() - ally.getHealth();
            if (missing <= 0) continue;
            ally.heal(heal);
            MasteryService.onHeal(caster, c.spell, c.castId, ally, Math.min(heal, missing));
            EchoService.onSupport(caster, Math.min(heal, missing));
        }
    }

    private static AABB area(Column c) {
        return new AABB(c.pos, c.pos).inflate(c.radius + 1, 0, c.radius + 1).expandTowards(0, 4, 0).move(0, -1, 0);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
    }

    private static void beam(Column c, int count) {
        // the column of light falling from the sky, and on the strike a flash and a ring
        Vec3 top = c.pos.add(0, COLUMN_HEIGHT + 12, 0), down = c.pos.subtract(top);
        float len = (float) down.length();
        Vfx.play(c.level, CORE, top.add(down.scale(0.5)), down, 0, len);
        Vfx.play(c.level, GLOW, top.add(down.scale(0.5)), down, 0, len);
        if (count >= 20) {
            Vfx.play(c.level, COLUMN, c.pos, Vec3.ZERO, 0, (float) Math.max(1, c.radius / 2.5));
            Vfx.play(c.level, BURST, c.pos.add(0, 0.5, 0), Vec3.ZERO, 0, (float) Math.max(1, c.radius / 2));
            Vfx.play(c.level, RING, c.pos.add(0, 0.2, 0), Vec3.ZERO, 0, (float) Math.max(1, c.radius / 3));
        }
        double spread = c.radius * 0.3;
        for (int i = 0; i < count; i++) {
            double y = c.level.getRandom().nextDouble() * COLUMN_HEIGHT;
            Fx.MOTE.play(c.level, c.pos.x, c.pos.y + y, c.pos.z, 1, spread, 0.2, spread);
        }
        if (count >= 20) Fx.TWINKLE.play(c.level, c.pos.x, c.pos.y + 0.3, c.pos.z, 30, c.radius * 0.5, 0.1, c.radius * 0.5);
    }

    /** For tests. */
    public static int active() {
        return COLUMNS.size();
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        COLUMNS.clear();
    }

    private JudgmentBehavior() {}
}
