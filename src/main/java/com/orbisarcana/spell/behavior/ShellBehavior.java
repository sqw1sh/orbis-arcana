package com.orbisarcana.spell.behavior;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.spell.Arcs;
import com.orbisarcana.spell.Fx;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * {@code shell}: Current Shell (§14.6), a TOGGLE. While on: +speed, melee hits shock. Params: speed_bonus,
 * shock_damage; return_spark (5: whoever hits you is sparked), transfer_ticks (10: right-click an ally to give them
 * the shell for this long), final_burst (15: turning it off discharges around you, stronger the longer it was on).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class ShellBehavior implements ToggleBehavior {
    public static final ShellBehavior INSTANCE = new ShellBehavior();
    private static final Identifier SPEED_ID = OrbisArcana.id("current_shell");
    private static final long TRANSFER_COOLDOWN = 10 * 20;
    private static final double BURST_RADIUS = 3.0;
    private static final double BURST_PER_SECOND = 0.5;
    private static final int BURST_MAX_SECONDS = 10;

    /** Holder -> shell. {@code until} is Long.MAX_VALUE for the caster's own toggle. */
    private record Shell(UUID caster, Identifier spell, Identifier school, int castId, double speed, float shock, float spark,
            int transferTicks, long until) {}

    private static final Map<UUID, Shell> SHELLS = new HashMap<>();
    private static final Map<UUID, Long> TRANSFER_READY = new HashMap<>();

    @Override
    public void release(CastContext ctx) {
        ServerPlayer p = ctx.caster();
        give(p, new Shell(p.getUUID(), ctx.spellId(), ctx.spell().school(), ctx.castId(), ctx.param("speed_bonus"),
                (float) ctx.scaled("shock_damage"), (float) ctx.scaled("return_spark"), ctx.ticks("transfer_ticks"), Long.MAX_VALUE));
        Fx.ARC.play(p.level(), p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.6, 0.4);
    }

    @Override
    public void tick(CastContext ctx, int ticks) {
        ServerPlayer p = ctx.caster();
        if (ticks % 4 == 0) Fx.ARC.play(p.level(), p.getX(), p.getY() + 1, p.getZ(), 2, 0.35, 0.6, 0.35);
    }

    @Override
    public void disable(CastContext ctx, int ticks, boolean manual) {
        ServerPlayer p = ctx.caster();
        take(p);
        double base = ctx.scaled("final_burst");
        if (base <= 0) return;
        float damage = (float) (base + BURST_PER_SECOND * Math.min(BURST_MAX_SECONDS, ticks / 20));
        ServerLevel level = p.level();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(BURST_RADIUS),
                e -> e.isAlive() && e != p && !(e instanceof Player) && e.distanceTo(p) <= BURST_RADIUS)) {
            hurt(level, p, ctx.spellId(), ctx.spell().school(), ctx.castId(), e, damage);
            Arcs.draw(level, Arcs.body(p), Arcs.body(e));
        }
        Fx.ARC.play(level, p.getX(), p.getY() + 1, p.getZ(), 80, BURST_RADIUS / 2, 0.6, BURST_RADIUS / 2);
    }

    public static boolean has(LivingEntity e) {
        return SHELLS.containsKey(e.getUUID());
    }

    private static void give(Player holder, Shell shell) {
        SHELLS.put(holder.getUUID(), shell);
        AttributeInstance speed = holder.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_ID);
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, shell.speed(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void take(Player holder) {
        SHELLS.remove(holder.getUUID());
        AttributeInstance speed = holder.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED_ID);
    }

    /** Melee hits of a shell holder shock; hits on a holder spark back (milestone 5). */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (SHELLS.isEmpty() || !(event.getEntity().level() instanceof ServerLevel level)) return;
        LivingEntity victim = event.getEntity();
        var source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker && attacker != victim) {
            Shell s = SHELLS.get(attacker.getUUID());
            if (s != null && s.shock() > 0 && victim.isAlive()) {
                ServerPlayer caster = level.getServer().getPlayerList().getPlayer(s.caster());
                victim.damageCooldownTime = 0; // the shock lands on top of the blow
                hurt(level, caster != null ? caster : attacker, s.spell(), s.school(), s.castId(), victim, s.shock());
                Arcs.draw(level, Arcs.body(attacker), Arcs.body(victim));
            }
        }
        Shell held = SHELLS.get(victim.getUUID());
        if (held != null && held.spark() > 0 && source.getEntity() instanceof LivingEntity hitter && source.getDirectEntity() == hitter
                && hitter != victim && hitter.isAlive() && victim instanceof ServerPlayer holder) {
            hitter.hurtServer(level, ModDamageTypes.source(hitter, held.school(), null, holder), held.spark());
            Arcs.draw(level, Arcs.body(victim), Arcs.body(hitter));
        }
    }

    /** Milestone 10: right-click an ally to pass them the shell for a while. */
    @SubscribeEvent
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer giver) || !(event.getTarget() instanceof ServerPlayer ally)) return;
        Shell s = SHELLS.get(giver.getUUID());
        if (s == null || s.transferTicks() <= 0 || s.until() != Long.MAX_VALUE || has(ally)) return;
        long now = giver.level().getGameTime();
        if (now < TRANSFER_READY.getOrDefault(giver.getUUID(), 0L)) return;
        TRANSFER_READY.put(giver.getUUID(), now + TRANSFER_COOLDOWN);
        give(ally, new Shell(s.caster(), s.spell(), s.school(), s.castId(), s.speed(), s.shock(), s.spark(), 0, now + s.transferTicks()));
        Arcs.draw(giver.level(), Arcs.body(giver), Arcs.body(ally));
        event.setCanceled(true);
    }

    /** Passed shells run out. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (SHELLS.isEmpty()) return;
        for (var e : List.copyOf(SHELLS.entrySet())) {
            if (e.getValue().until() == Long.MAX_VALUE) continue;
            ServerPlayer holder = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (holder == null) {
                SHELLS.remove(e.getKey());
            } else if (holder.level().getGameTime() >= e.getValue().until()) {
                take(holder);
            } else if (holder.tickCount % 4 == 0) {
                Fx.ARC.play(holder.level(), holder.getX(), holder.getY() + 1, holder.getZ(), 1, 0.35, 0.6, 0.35);
            }
        }
    }

    private static void hurt(ServerLevel level, ServerPlayer caster, Identifier spell, Identifier school, int castId, LivingEntity e, float damage) {
        float before = e.getHealth();
        e.hurtServer(level, ModDamageTypes.source(e, school, null, caster), damage);
        if (e.getHealth() < before) {
            MasteryService.onHit(caster, spell, castId, e, !e.isAlive());
            EchoService.onSpellHit(caster, school, e, castId);
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        SHELLS.clear();
        TRANSFER_READY.clear();
    }

    private ShellBehavior() {}
}
