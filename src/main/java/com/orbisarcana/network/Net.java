package com.orbisarcana.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends payloads only to players whose connection negotiated our channels. NeoForge throws when sending
 * to a connection without the channel (mock players in GameTests, clients without the mod).
 */
public final class Net {
    public static void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload)) PacketDistributor.sendToPlayer(player, payload);
    }

    /** The player plus everyone who has the player's chunk loaded. */
    public static void toPlayerAndNearby(ServerPlayer player, CustomPacketPayload payload) {
        toPlayer(player, payload);
        ChunkPos chunk = ChunkPos.containing(player.blockPosition());
        for (ServerPlayer other : player.level().getChunkSource().chunkMap.getPlayers(chunk, false)) {
            if (other != player) toPlayer(other, payload);
        }
    }

    private Net() {}
}
