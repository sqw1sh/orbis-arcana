package com.orbisarcana.progression;

import com.orbisarcana.data.ModAttachments;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Server-side changes to a player's spell knowledge; every change is synced to the owner. */
public final class KnowledgeService {
    public static KnownSpells knowledge(ServerPlayer player) {
        return player.getData(ModAttachments.KNOWLEDGE);
    }

    public static boolean learn(ServerPlayer player, Identifier spell) {
        boolean learned = knowledge(player).learn(spell);
        if (learned) player.syncData(ModAttachments.KNOWLEDGE);
        return learned;
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
