package com.orbisarcana.spell;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.network.Net;
import com.orbisarcana.network.VfxPlay;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the mod's VFX (roadmap V4): tells the players near a point to play an effect. Effects are client
 * resources ({@code assets/<ns>/orbisarcana/vfx/<name>.json}); the server only names them.
 */
public final class Vfx {
    /** Players farther than this do not get the packet (the client also culls at 96 blocks). */
    private static final double RANGE = 96;

    public static Identifier id(String name) {
        return OrbisArcana.id(name);
    }

    /** Plays {@code effect} at {@code pos}, oriented by {@code dir}, tinted with {@code tint} (0 = own colours). */
    public static void play(ServerLevel level, Identifier effect, Vec3 pos, Vec3 dir, int tint, float scale) {
        send(level, pos, new VfxPlay(effect, pos.x, pos.y, pos.z, (float) dir.x, (float) dir.y, (float) dir.z, tint & 0xFFFFFF, scale, -1));
    }

    public static void play(ServerLevel level, Identifier effect, Vec3 pos, int tint) {
        play(level, effect, pos, Vec3.ZERO, tint, 1f);
    }

    /** Plays {@code effect} following {@code entity} while it lives (trails, auras). */
    public static void attach(Entity entity, Identifier effect, int tint, float scale) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        Vec3 pos = entity.position().add(0, entity.getBbHeight() / 2, 0);
        Vec3 dir = entity.getDeltaMovement();
        send(level, pos, new VfxPlay(effect, pos.x, pos.y, pos.z, (float) dir.x, (float) dir.y, (float) dir.z, tint & 0xFFFFFF, scale, entity.getId()));
    }

    private static void send(ServerLevel level, Vec3 pos, VfxPlay payload) {
        for (ServerPlayer p : level.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(net.minecraft.core.BlockPos.containing(pos)), false)) {
            if (p.position().distanceToSqr(pos) <= RANGE * RANGE) Net.toPlayer(p, payload);
        }
    }

    private Vfx() {}
}
