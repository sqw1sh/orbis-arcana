package com.orbisarcana.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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

    /** Everyone with the entity chunk loaded. */
    public static void toNearby(Entity entity, CustomPacketPayload payload) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        for (ServerPlayer p : level.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(entity.blockPosition()), false)) {
            toPlayer(p, payload);
        }
    }

    private Net() {}
}
