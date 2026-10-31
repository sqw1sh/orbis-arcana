package com.orbisarcana.mana;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.ManaRegen;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.data.PlayerResources;
import com.orbisarcana.data.ResourcesSync;
import com.orbisarcana.registry.ModAttributes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-side mana: regeneration, combat tracking, death penalty (§8.1). */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class ManaService {
    public static double maxMana(Player player) {
        return player.getAttributeValue(ModAttributes.MAX_MANA);
    }

    /** Works on both sides; on the client it reads the last synced value. */
    public static double mana(Player player) {
        PlayerResources res = player.getData(ModAttachments.RESOURCES);
        return res.mana() < 0 ? maxMana(player) : res.mana();
    }

    public static void setMana(ServerPlayer player, double value) {
        player.getData(ModAttachments.RESOURCES).setMana(Math.max(0, Math.min(maxMana(player), value)));
        ResourcesSync.syncNow(player);
    }

    /** Spends mana if there is enough; returns false (and spends nothing) otherwise. */
    public static boolean tryConsume(ServerPlayer player, double amount) {
        double current = mana(player);
        if (current < amount) return false;
        setMana(player, current - amount);
        return true;
    }

    public static boolean inCombat(Player player) {
        long since = player.level().getGameTime() - player.getData(ModAttachments.RESOURCES).lastCombatTick();
        return ManaRegen.inCombat(since);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
        PlayerResources res = player.getData(ModAttachments.RESOURCES);
        double max = maxMana(player);
        double mana = res.mana() < 0 ? max : res.mana();
        if (mana < max) {
            long since = player.level().getGameTime() - res.lastCombatTick();
            mana += ManaRegen.perTick(since, player.getAttributeValue(ModAttributes.MANA_REGEN));
        }
        res.setMana(Math.min(mana, max));
        ResourcesSync.syncThrottled(player);
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (event.getHealthDamage() <= 0) return;
        if (event.getEntity() instanceof ServerPlayer victim) {
            victim.getData(ModAttachments.RESOURCES).markCombat(victim.level().getGameTime());
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            attacker.getData(ModAttachments.RESOURCES).markCombat(attacker.level().getGameTime());
        }
    }

    /**
     * Death penalty. Not done in {@code PlayerEvent.Clone}: NeoForge copies attachments in its own
     * Clone listener, which may run after ours and overwrite the value. Respawn fires after the copy.
     */
    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        setMana(player, maxMana(player) * ManaRegen.DEATH_KEEP_FRACTION);
    }

    private ManaService() {}
}
