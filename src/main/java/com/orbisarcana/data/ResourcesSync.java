package com.orbisarcana.data;

import net.minecraft.server.level.ServerPlayer;

/** Sends the {@code resources} attachment to its owner at most 4 times per second (§15.3). */
public final class ResourcesSync {
    private static final int INTERVAL_TICKS = 5;

    public static void syncThrottled(ServerPlayer player) {
        PlayerResources res = player.getData(ModAttachments.RESOURCES);
        if (res.mana() == res.lastSyncedMana()) return;
        if (player.tickCount % INTERVAL_TICKS != 0) return;
        syncNow(player);
    }

    public static void syncNow(ServerPlayer player) {
        player.syncData(ModAttachments.RESOURCES);
        player.getData(ModAttachments.RESOURCES).markSynced();
    }

    private ResourcesSync() {}
}
