package com.orbisarcana.spell.behavior;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.mana.ManaService;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * {@code curse}: Withering Curse (§14.9). The target deals {@code weaken} less damage and withers every second.
 * Params: duration_ticks, damage_per_second, weaken; spread_radius (5: on death the curse jumps to the nearest enemy),
 * heal_cut (10: the target heals that much less), death_mana + death_heal (15: the target's death restores the caster).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class CurseBehavior {
    /** Volumetric effects: shadows closing on the cursed one, a ring of runes over its head. */
    private static final Identifier COLLAPSE = Vfx.id("dusk_sphere"), HALO = Vfx.id("curse_halo");
    /** Signature effects (tools/vfx-effects.cjs): shadows closing on the cursed one, a violet rune over its head. */
    private static final Identifier IMPLODE = Vfx.id("dusk_implode"), RUNE = Vfx.id("curse_rune");
    private record Curse(ServerLevel level, UUID caster, Identifier spell, Identifier school, int castId, long until,
            float dps, double weaken, double spread, double healCut, double deathMana, float deathHeal) {}

    /** target -> curse. */
    private static final Map<LivingEntity, Curse> CURSES = new HashMap<>();

    static void release(CastContext ctx) {
        LivingEntity target = ctx.target();
        if (target == null || target == ctx.caster()) return;
        apply(target, new Curse(ctx.level(), ctx.caster().getUUID(), ctx.spellId(), ctx.spell().school(), ctx.castId(),
                ctx.level().getGameTime() + ctx.ticks("duration_ticks"), (float) ctx.scaled("damage_per_second"), ctx.param("weaken"),
                ctx.param("spread_radius"), ctx.param("heal_cut"), ctx.param("death_mana"), (float) ctx.param("death_heal")), ctx.caster());
    }

    private static void apply(LivingEntity target, Curse curse, ServerPlayer caster) {
        CURSES.put(target, curse);
        MasteryService.onControl(caster, curse.spell(), curse.castId(), target);
        EchoService.markDusk(caster, target);
        Fx.SHADOW.play(curse.level(), target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 15, 0.3, 0.4, 0.3);
        Vfx.play(curse.level(), IMPLODE, target.position().add(0, target.getBbHeight() * 0.6, 0), Vec3.ZERO, 0, 1f);
        Vfx.play(curse.level(), COLLAPSE, target.position().add(0, target.getBbHeight() * 0.6, 0), Vec3.ZERO, 0, 1f);
    }

    public static boolean isCursed(LivingEntity e) {
        return CURSES.containsKey(e);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || CURSES.isEmpty()) return;
        long now = level.getGameTime();
        for (var e : List.copyOf(CURSES.entrySet())) {
            Curse c = e.getValue();
            if (c.level() != level) continue;
            LivingEntity t = e.getKey();
            if (t.isRemoved() || now >= c.until()) {
                CURSES.remove(t);
                continue;
            }
            if (!t.isAlive()) continue;
            if (now % 20 == 0 && c.dps() > 0) {
                ServerPlayer caster = level.getServer().getPlayerList().getPlayer(c.caster());
                float before = t.getHealth();
                // Withering ticks keep the Dusk mark: only real blows absorb it
                EchoService.quietly(() -> t.hurtServer(level, ModDamageTypes.source(t, c.school(), null, caster), c.dps()));
                if (caster != null && t.getHealth() < before) {
                    MasteryService.onHit(caster, c.spell(), c.castId(), t, !t.isAlive());
                    EchoService.onSpellHit(caster, c.school(), t, c.castId());
                }
            }
            if (now % 8 == 0) Fx.SHADOW.play(level, t.getX(), t.getY() + t.getBbHeight() * 0.8, t.getZ(), 2, 0.2, 0.2, 0.2);
            if (now % 16 == 0) Vfx.play(level, HALO, t.position().add(0, t.getBbHeight() + 0.4, 0), Vec3.ZERO, 0, 1f);
        }
    }

    /** The cursed deal less damage. */
    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!CURSES.isEmpty() && event.getSource().getEntity() instanceof LivingEntity attacker) {
            Curse c = CURSES.get(attacker);
            if (c != null) event.setAmount((float) (event.getAmount() * (1 - c.weaken())));
        }
    }

    /** Milestone 10: the cursed heal less. */
    @SubscribeEvent
    static void onHeal(LivingHealEvent event) {
        Curse c = CURSES.isEmpty() ? null : CURSES.get(event.getEntity());
        if (c != null && c.healCut() > 0) event.setAmount((float) (event.getAmount() * (1 - c.healCut())));
    }

    /** Milestones 5 and 15: the curse jumps on, the caster is restored. */
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Curse c = CURSES.remove(dead);
        if (c == null || !(dead.level() instanceof ServerLevel level)) return;
        ServerPlayer caster = level.getServer().getPlayerList().getPlayer(c.caster());
        if (caster == null) return;
        if (c.deathMana() > 0) ManaService.setMana(caster, Math.min(ManaService.maxMana(caster), ManaService.mana(caster) + c.deathMana()));
        if (c.deathHeal() > 0) caster.heal(c.deathHeal());
        if (c.spread() > 0) {
            level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(c.spread()),
                            e -> e.isAlive() && e != dead && !(e instanceof Player) && !CURSES.containsKey(e))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(dead)))
                    .ifPresent(next -> apply(next, c, caster));
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        CURSES.clear();
    }

    private CurseBehavior() {}
}
