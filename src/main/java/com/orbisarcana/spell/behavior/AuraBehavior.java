package com.orbisarcana.spell.behavior;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.registry.ModDamageTypes;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.school.TalentEffects;
import com.orbisarcana.spell.Shields;
import com.orbisarcana.spell.Fx;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * {@code aura}: Purifying Aura (§14.8), a TOGGLE. Allies within {@code radius} (the caster too) take
 * {@code damage_reduction} less damage. Params: cleanse_interval (5: every so often each ally loses one harmful effect),
 * ward_shield (10: an ally who is hit gets a small shield, once per {@link #WARD_COOLDOWN}), undead_burn (15: undead in
 * the aura catch fire).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class AuraBehavior implements ToggleBehavior {
    public static final AuraBehavior INSTANCE = new AuraBehavior();
    private static final long WARD_COOLDOWN = 10 * 20;
    private static final int WARD_TICKS = 6 * 20;
    private static final int BURN_TICKS = 60;

    private record Aura(UUID caster, Identifier spell, int castId, double radius, double reduction, float ward) {}

    private static final Map<UUID, Aura> AURAS = new HashMap<>();
    private static final Map<UUID, Long> WARD_READY = new HashMap<>();

    @Override
    public void release(CastContext ctx) {
        ServerPlayer p = ctx.caster();
        AURAS.put(p.getUUID(), new Aura(p.getUUID(), ctx.spellId(), ctx.castId(), ctx.param("radius"), ctx.param("damage_reduction"),
                (float) ctx.param("ward_shield")));
        Fx.MOTE.play(p.level(), p.getX(), p.getY() + 1, p.getZ(), 30, 0.6, 0.6, 0.6);
    }

    @Override
    public void tick(CastContext ctx, int ticks) {
        ServerPlayer p = ctx.caster();
        ServerLevel level = p.level();
        double r = ctx.param("radius");
        if (ticks % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2;
                Fx.MOTE.play(level, p.getX() + Math.cos(a) * r, p.getY() + 0.2, p.getZ() + Math.sin(a) * r, 1, 0, 0.05, 0);
            }
        }
        int cleanse = ctx.ticks("cleanse_interval");
        if (cleanse > 0 && ticks > 0 && ticks % cleanse == 0) {
            for (Player ally : allies(p, r)) {
                ally.getActiveEffects().stream()
                        .filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                        .map(MobEffectInstance::getEffect).findFirst()
                        .ifPresent(effect -> {
                            ally.removeEffect(effect);
                            MasteryService.award(p, ctx.spellId(), 1);
                            Fx.MOTE.play(level, ally.getX(), ally.getY() + 1, ally.getZ(), 10, 0.3, 0.5, 0.3);
                        });
            }
        }
        if (ctx.param("undead_burn") > 0 && ticks % 20 == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                    e -> e.isAlive() && TalentEffects.isUndead(e) && e.distanceTo(p) <= r)) {
                e.igniteForTicks(BURN_TICKS);
                e.hurtServer(level, ModDamageTypes.source(e, ctx.spell().school(), null, p), 1f);
            }
        }
    }

    @Override
    public void disable(CastContext ctx, int ticks, boolean manual) {
        AURAS.remove(ctx.caster().getUUID());
    }

    public static boolean has(Player caster) {
        return AURAS.containsKey(caster.getUUID());
    }

    private static List<Player> allies(Player caster, double r) {
        return caster.level().getEntitiesOfClass(Player.class, caster.getBoundingBox().inflate(r), a -> a.isAlive() && a.distanceTo(caster) <= r);
    }

    /** The strongest aura covering {@code victim}, with its caster. */
    private static Map.Entry<Aura, ServerPlayer> covering(ServerPlayer victim) {
        Map.Entry<Aura, ServerPlayer> best = null;
        for (Aura a : AURAS.values()) {
            ServerPlayer caster = victim.level().getServer().getPlayerList().getPlayer(a.caster());
            if (caster == null || caster.level() != victim.level() || caster.distanceTo(victim) > a.radius()) continue;
            if (best == null || a.reduction() > best.getKey().reduction()) best = Map.entry(a, caster);
        }
        return best;
    }

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (AURAS.isEmpty() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        var cover = covering(victim);
        if (cover == null) return;
        float prevented = (float) (event.getAmount() * cover.getKey().reduction());
        event.setAmount(event.getAmount() - prevented);
        EchoService.onSupport(cover.getValue(), prevented);
    }

    /** Milestone 10: an ally who takes damage in the aura gets a ward shield. */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (AURAS.isEmpty() || !(event.getEntity() instanceof ServerPlayer victim) || event.getHealthDamage() <= 0 || !victim.isAlive()) return;
        var cover = covering(victim);
        if (cover == null || cover.getKey().ward() <= 0) return;
        long now = victim.level().getGameTime();
        if (now < WARD_READY.getOrDefault(victim.getUUID(), 0L)) return;
        WARD_READY.put(victim.getUUID(), now + WARD_COOLDOWN);
        Aura a = cover.getKey();
        ServerPlayer caster = cover.getValue();
        Shields.grant(victim, a.ward(), WARD_TICKS, (holder, absorbed, broken) -> {
            if (absorbed > 0) MasteryService.onHeal(caster, a.spell(), a.castId(), holder, absorbed);
        });
        EchoService.onSupport(caster, a.ward());
        Fx.MOTE.play(victim.level(), victim.getX(), victim.getY() + 1, victim.getZ(), 15, 0.4, 0.6, 0.4);
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        AURAS.clear();
        WARD_READY.clear();
    }

    private AuraBehavior() {}
}
