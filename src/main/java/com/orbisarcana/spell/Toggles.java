package com.orbisarcana.spell;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.mana.ManaService;
import com.orbisarcana.network.CastPayloads.ToggleState;
import com.orbisarcana.network.Net;
import com.orbisarcana.spell.behavior.CastContext;
import com.orbisarcana.spell.behavior.SpellBehaviors;
import com.orbisarcana.spell.behavior.ToggleBehavior;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * TOGGLE spells (§5.3): a press turns a lasting effect on, the next press turns it off. They do not occupy the
 * cast slot; {@code cost} is mana per second, drained every tick; out of mana turns them off. The cooldown starts
 * when a toggle ends. Session state: toggles end on death, logout and server stop.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class Toggles {
    /** Snapshot taken when enabled, like a cast (§8.2). */
    record Active(Identifier spellId, SpellDefinition spell, int castId, int mastery, Map<String, Double> params,
            double power, double drainPerTick, long startTick) {}

    private static final Map<UUID, Map<Identifier, Active>> ACTIVE = new HashMap<>();

    public static boolean isOn(ServerPlayer player, Identifier spellId) {
        var map = ACTIVE.get(player.getUUID());
        return map != null && map.containsKey(spellId);
    }

    static void enable(ServerPlayer player, Active toggle) {
        ACTIVE.computeIfAbsent(player.getUUID(), u -> new LinkedHashMap<>()).put(toggle.spellId(), toggle);
        behavior(toggle).ifPresent(b -> b.release(context(player, toggle)));
        Net.toPlayerAndNearby(player, new ToggleState(player.getId(), toggle.spellId(), true, toggle.mastery()));
    }

    /** Turns a toggle off; {@code manual} if the player pressed it again. */
    public static void disable(ServerPlayer player, Identifier spellId, boolean manual) {
        var map = ACTIVE.get(player.getUUID());
        Active toggle = map == null ? null : map.remove(spellId);
        if (toggle == null) return;
        if (map.isEmpty()) ACTIVE.remove(player.getUUID());
        int ticks = (int) (player.level().getGameTime() - toggle.startTick());
        behavior(toggle).ifPresent(b -> b.disable(context(player, toggle), ticks, manual));
        CastService.startCooldown(player, toggle.spellId(), toggle.spell());
        Net.toPlayerAndNearby(player, new ToggleState(player.getId(), toggle.spellId(), false, toggle.mastery()));
    }

    public static void disableAll(ServerPlayer player) {
        var map = ACTIVE.get(player.getUUID());
        if (map != null) for (Identifier id : List.copyOf(map.keySet())) disable(player, id, false);
    }

    private static java.util.Optional<ToggleBehavior> behavior(Active t) {
        return SpellBehaviors.get(t.spell().behavior()).filter(ToggleBehavior.class::isInstance).map(ToggleBehavior.class::cast);
    }

    private static CastContext context(ServerPlayer player, Active t) {
        return new CastContext(player, t.spellId(), t.castId(), t.spell(), t.mastery(), t.params(), t.power(), null, null);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        for (UUID id : List.copyOf(ACTIVE.keySet())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null) {
                ACTIVE.remove(id);
                continue;
            }
            for (Active t : List.copyOf(ACTIVE.get(id).values())) {
                double mana = ManaService.mana(player);
                if (mana < t.drainPerTick()) {
                    disable(player, t.spellId(), false);
                    continue;
                }
                ManaService.setMana(player, mana - t.drainPerTick());
                int ticks = (int) (player.level().getGameTime() - t.startTick());
                behavior(t).ifPresent(b -> b.tick(context(player, t), ticks));
            }
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) disableAll(player);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) disableAll(player);
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.clear();
    }

    private Toggles() {}
}
