package com.orbisarcana.progression;

import java.util.Optional;

import com.orbisarcana.data.ModAttachments;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Server-side changes to a player's spell knowledge; every change is synced to the owner. */
public final class KnowledgeService {
    public static KnownSpells knowledge(ServerPlayer player) {
        return player.getData(ModAttachments.KNOWLEDGE);
    }

    /** Learns the spell and places it into the first free radial-menu slot. */
    public static boolean learn(ServerPlayer player, Identifier spell) {
        boolean learned = knowledge(player).learn(spell);
        if (learned) {
            player.syncData(ModAttachments.KNOWLEDGE);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.orbisarcana.api.event.SpellLearnEvent(player, spell));
            if (player.getData(ModAttachments.LOADOUT).assignToFirstEmpty(spell)) player.syncData(ModAttachments.LOADOUT);
        }
        return learned;
    }

    /** Puts a known spell into a radial slot, or clears the slot (empty {@code spell}). */
    public static boolean setSlot(ServerPlayer player, int page, int slot, Optional<Identifier> spell) {
        if (page < 0 || page >= Loadout.PAGES || slot < 0 || slot >= Loadout.SLOTS) return false;
        if (spell.isPresent() && !knowledge(player).knows(spell.get())) return false;
        Loadout loadout = player.getData(ModAttachments.LOADOUT);
        spell.ifPresent(loadout::removeEverywhere); // one slot per spell
        loadout.set(page, slot, spell);
        player.syncData(ModAttachments.LOADOUT);
        return true;
    }

    public static void select(ServerPlayer player, int page, int slot) {
        if (page < 0 || page >= Loadout.PAGES || slot < 0 || slot >= Loadout.SLOTS) return;
        Loadout loadout = player.getData(ModAttachments.LOADOUT);
        loadout.setPage(page);
        loadout.setSelected(slot);
        player.syncData(ModAttachments.LOADOUT);
    }

    /** Disables or re-enables a milestone the player has reached; regular-level stats stay (§7.5). */
    public static boolean setMilestoneDisabled(ServerPlayer player, Identifier spell, int level, boolean disabled) {
        var progress = knowledge(player).progress(spell);
        if (progress.isEmpty() || !com.orbisarcana.core.MasteryMath.isMilestone(level) || progress.get().level() < level) return false;
        progress.get().setMilestoneDisabled(level, disabled);
        player.syncData(ModAttachments.KNOWLEDGE);
        return true;
    }

    /** Forgets the spell and clears it from the radial menu. */
    public static boolean forget(ServerPlayer player, Identifier spell) {
        if (!knowledge(player).forget(spell)) return false;
        player.getData(ModAttachments.LOADOUT).removeEverywhere(spell);
        player.syncData(ModAttachments.KNOWLEDGE);
        player.syncData(ModAttachments.LOADOUT);
        return true;
    }

    public static boolean setMastery(ServerPlayer player, Identifier spell, int level) {
        var progress = knowledge(player).progress(spell);
        if (progress.isEmpty()) return false;
        progress.get().setLevel(level);
        player.syncData(ModAttachments.KNOWLEDGE);
        return true;
    }

    private KnowledgeService() {}
}
