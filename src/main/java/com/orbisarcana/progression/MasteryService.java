package com.orbisarcana.progression;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.OrbisConfig;
import com.orbisarcana.core.MasteryMath;
import com.orbisarcana.core.XpRules;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.item.SpellScrollItem;
import com.orbisarcana.mana.ManaService;
import com.orbisarcana.school.AttunementService;
import com.orbisarcana.spell.SpellDefinition;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Mastery XP (§7): behaviors report results (hits, kills, healing, movement) and this service turns
 * them into XP with the anti-farm rules, levels spells up and announces milestones.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class MasteryService {
    private static final long CAST_MEMORY_TICKS = 20 * 30;

    /** Targets already hit per (player, cast), so extra targets give reduced XP. */
    private record CastKey(UUID player, int castId) {}

    private static final class CastHits {
        final Set<UUID> targets = new HashSet<>();
        double healGranted;
        long lastTick;
    }

    private static final Map<CastKey, CastHits> CASTS = new HashMap<>();
    /** player -> target -> recent hit times (for the same-target decay). */
    private static final Map<UUID, Map<UUID, Deque<Long>>> RECENT = new HashMap<>();
    /** player -> other player -> {hour, xp granted} (PvP hourly cap). */
    private static final Map<UUID, Map<UUID, double[]>> PVP = new HashMap<>();
    /** player -> {day, calm mobility xp granted}. */
    private static final Map<UUID, double[]> CALM_MOBILITY = new HashMap<>();

    // ---- results reported by behaviors ----

    /** A spell damaged {@code target}; {@code killed} if it died from it. */
    public static void onHit(ServerPlayer player, Identifier spell, int castId, LivingEntity target, boolean killed) {
        double antiFarm = targetMultiplier(player, target);
        if (antiFarm <= 0) return;
        CastHits hits = hits(player, castId);
        double weight = 0;
        if (hits.targets.add(target.getUUID())) weight += XpRules.attackWeight(hits.targets.size() - 1);
        if (killed) weight += XpRules.KILL_BONUS;
        weight *= antiFarm * XpRules.sameTargetMult(recordHit(player, target));
        weight = applyPvpCap(player, target, weight, spell);
        award(player, spell, weight);
    }

    /** A control effect landed on a target that is not immune (§7.2: weight 1.0, once per target per cast). */
    public static void onControl(ServerPlayer player, Identifier spell, int castId, LivingEntity target) {
        double antiFarm = targetMultiplier(player, target);
        if (antiFarm <= 0) return;
        double weight = XpRules.CONTROL * antiFarm * XpRules.sameTargetMult(recordHit(player, target));
        award(player, spell, applyPvpCap(player, target, weight, spell));
    }

    /** A spell restored {@code healed} HP (overheal excluded) on {@code target}. */
    public static void onHeal(ServerPlayer player, Identifier spell, int castId, LivingEntity target, float healed) {
        if (healed <= 0 || target instanceof ArmorStand) return;
        CastHits hits = hits(player, castId);
        double weight = XpRules.healWeight(healed, target.getMaxHealth(), hits.healGranted);
        hits.healGranted += weight;
        award(player, spell, weight);
    }

    /** A movement spell carried the player {@code distance} blocks. */
    public static void onMove(ServerPlayer player, Identifier spell, double distance) {
        boolean combat = ManaService.inCombat(player);
        double weight = XpRules.mobilityWeight(distance, combat);
        if (weight <= 0) return;
        if (!combat) {
            long day = player.level().getGameTime() / 24000L; // 24000 ticks = one in-game day
            double[] calm = CALM_MOBILITY.computeIfAbsent(player.getUUID(), u -> new double[] {day, 0});
            if (calm[0] != day) {
                calm[0] = day;
                calm[1] = 0;
            }
            double xp = xpFor(player, spell, weight);
            double allowed = Math.max(0, XpRules.MOBILITY_CALM_DAILY_CAP - calm[1]);
            if (allowed <= 0) return;
            if (xp > allowed) weight *= allowed / xp;
            calm[1] += Math.min(xp, allowed);
        }
        award(player, spell, weight);
    }

    // ---- XP ----

    /** Converts a result weight into XP for the spell and applies it (§7.3). */
    public static void award(ServerPlayer player, Identifier spellId, double weight) {
        if (weight <= 0) return;
        Optional<SpellDefinition> spell = SpellRegistry.get(player.registryAccess(), spellId);
        var progress = player.getData(ModAttachments.KNOWLEDGE).progress(spellId);
        if (spell.isEmpty() || progress.isEmpty()) return;
        var p = progress.get();
        if (p.level() >= MasteryMath.MAX_LEVEL) return;

        double gained = xpFor(player, spellId, weight);
        int before = p.level();
        var result = MasteryMath.addXp(before, p.xp(), gained, spell.get().rarity(), MasteryMath.MAX_LEVEL);
        p.setLevel(result.level());
        p.setXp(result.xp());
        player.syncData(ModAttachments.KNOWLEDGE);
        AttunementService.onMasteryXp(player, spell.get().school(), gained);
        for (int level = before + 1; level <= result.level(); level++) {
            announce(player, spellId, spell.get(), level);
            var bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
            bus.post(new com.orbisarcana.api.event.MasteryLevelUpEvent(player, spellId, level));
            if (MasteryMath.isMilestone(level)) bus.post(new com.orbisarcana.api.event.MasteryLevelUpEvent.Milestone(player, spellId, level));
        }
    }

    private static double xpFor(ServerPlayer player, Identifier spellId, double weight) {
        return SpellRegistry.get(player.registryAccess(), spellId)
                .map(d -> MasteryMath.xpGain(weight, d.cast().ticks() / 20.0, d.cooldownTicks() / 20.0, OrbisConfig.MASTERY_XP_RATE.get())
                        * player.getAttributeValue(com.orbisarcana.registry.ModAttributes.MASTERY_XP))
                .orElse(0.0);
    }

    private static void announce(ServerPlayer player, Identifier spellId, SpellDefinition spell, int level) {
        Component name = SpellScrollItem.spellName(spellId).copy().withColor(spell.rarity().color);
        if (MasteryMath.isMilestone(level)) {
            player.sendSystemMessage(Component.translatable("message.orbisarcana.milestone", name, level,
                    Component.translatable("screen.orbisarcana.grimoire.milestone." + level),
                    Component.translatable("spell." + spellId.getNamespace() + "." + spellId.getPath() + ".m" + level)).withStyle(ChatFormatting.GOLD));
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
        } else {
            player.sendOverlayMessage(Component.translatable("message.orbisarcana.level_up", name, level).withStyle(ChatFormatting.AQUA));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.4f);
        }
    }

    // ---- anti-farm ----

    /** 0 for targets that must not give XP, 0.5 for spawner mobs, 0.25 for players, otherwise 1 (§7.2). */
    static double targetMultiplier(ServerPlayer player, LivingEntity target) {
        if (target == player || target instanceof ArmorStand || !target.isAttackable()) return 0;
        if (target instanceof OwnableEntity owned && owned.getOwner() == player) return 0;
        if (target instanceof Player) return XpRules.PVP_MULT;
        if (target instanceof Mob mob && mob.getSpawnType() == EntitySpawnReason.SPAWNER) return XpRules.SPAWNER_MULT;
        return 1.0;
    }

    private static int recordHit(ServerPlayer player, LivingEntity target) {
        long now = player.level().getGameTime();
        Deque<Long> times = RECENT.computeIfAbsent(player.getUUID(), u -> new HashMap<>())
                .computeIfAbsent(target.getUUID(), u -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() > XpRules.SAME_TARGET_WINDOW_TICKS) times.pollFirst();
        int before = times.size();
        times.addLast(now);
        return before;
    }

    private static double applyPvpCap(ServerPlayer player, LivingEntity target, double weight, Identifier spell) {
        if (!(target instanceof Player) || weight <= 0) return weight;
        long hour = player.level().getGameTime() / (20L * 60 * 60);
        double[] used = PVP.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).computeIfAbsent(target.getUUID(), u -> new double[] {hour, 0});
        if (used[0] != hour) {
            used[0] = hour;
            used[1] = 0;
        }
        double xp = xpFor(player, spell, weight);
        double allowed = Math.max(0, XpRules.PVP_HOURLY_CAP - used[1]);
        used[1] += Math.min(xp, allowed);
        return xp <= 0 ? 0 : weight * Math.min(1.0, allowed / xp);
    }

    private static CastHits hits(ServerPlayer player, int castId) {
        long now = player.level().getGameTime();
        CASTS.values().removeIf(h -> now - h.lastTick > CAST_MEMORY_TICKS);
        CastHits hits = CASTS.computeIfAbsent(new CastKey(player.getUUID(), castId), k -> new CastHits());
        hits.lastTick = now;
        return hits;
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        RECENT.remove(id);
        PVP.remove(id);
        CALM_MOBILITY.remove(id);
        CASTS.keySet().removeIf(k -> k.player().equals(id));
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        CASTS.clear();
        RECENT.clear();
        PVP.clear();
        CALM_MOBILITY.clear();
    }

    private MasteryService() {}
}
