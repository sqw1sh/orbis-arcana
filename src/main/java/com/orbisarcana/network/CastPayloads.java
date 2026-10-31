package com.orbisarcana.network;

import java.util.Optional;

import com.orbisarcana.OrbisArcana;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Cast packets (§15.6). Clients only send intents; everything is decided on the server. */
public final class CastPayloads {
    // ---- client -> server ----

    /** Player wants to start casting {@code spell}. */
    public record CastRequest(Identifier spell) implements CustomPacketPayload {
        public static final Type<CastRequest> TYPE = new Type<>(OrbisArcana.id("cast_request"));
        public static final StreamCodec<ByteBuf, CastRequest> CODEC = Identifier.STREAM_CODEC.map(CastRequest::new, CastRequest::spell);

        @Override
        public Type<CastRequest> type() {
            return TYPE;
        }
    }

    /** Cast key released ({@code cancel = false}) or cast cancelled by the player. */
    public record CastStop(int castId, boolean cancel) implements CustomPacketPayload {
        public static final Type<CastStop> TYPE = new Type<>(OrbisArcana.id("cast_stop"));
        public static final StreamCodec<ByteBuf, CastStop> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CastStop::castId,
                ByteBufCodecs.BOOL, CastStop::cancel,
                CastStop::new);

        @Override
        public Type<CastStop> type() {
            return TYPE;
        }
    }

    // ---- server -> client ----

    /**
     * Sent to the caster and nearby players; drives cast bars and magic circles. Circles are animated
     * client-side from {@code startTick}/{@code durationTicks}; positions are never streamed (§9.7).
     * {@code groundPoint} is set for GROUND spells, {@code targetId} (-1 if none) for TARGET spells.
     */
    public record CastStarted(int castId, int casterId, Identifier spell, long startTick, int durationTicks,
                              int mastery, Optional<Vec3> groundPoint, int targetId) implements CustomPacketPayload {
        public static final Type<CastStarted> TYPE = new Type<>(OrbisArcana.id("cast_started"));
        public static final StreamCodec<ByteBuf, CastStarted> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CastStarted::castId,
                ByteBufCodecs.VAR_INT, CastStarted::casterId,
                Identifier.STREAM_CODEC, CastStarted::spell,
                ByteBufCodecs.VAR_LONG, CastStarted::startTick,
                ByteBufCodecs.VAR_INT, CastStarted::durationTicks,
                ByteBufCodecs.VAR_INT, CastStarted::mastery,
                ByteBufCodecs.optional(Vec3.STREAM_CODEC), CastStarted::groundPoint,
                ByteBufCodecs.VAR_INT, CastStarted::targetId,
                CastStarted::new);

        @Override
        public Type<CastStarted> type() {
            return TYPE;
        }
    }

    /** End of a cast; {@code outcome} is an {@code ActiveCast.Outcome} ordinal. */
    public record CastEnded(int castId, int casterId, int outcome) implements CustomPacketPayload {
        public static final Type<CastEnded> TYPE = new Type<>(OrbisArcana.id("cast_ended"));
        public static final StreamCodec<ByteBuf, CastEnded> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CastEnded::castId,
                ByteBufCodecs.VAR_INT, CastEnded::casterId,
                ByteBufCodecs.VAR_INT, CastEnded::outcome,
                CastEnded::new);

        @Override
        public Type<CastEnded> type() {
            return TYPE;
        }
    }

    /** Why a cast request was refused; {@code reason} is a {@code CastDenial} ordinal. */
    public record CastDenied(Identifier spell, int reason) implements CustomPacketPayload {
        public static final Type<CastDenied> TYPE = new Type<>(OrbisArcana.id("cast_denied"));
        public static final StreamCodec<ByteBuf, CastDenied> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, CastDenied::spell,
                ByteBufCodecs.VAR_INT, CastDenied::reason,
                CastDenied::new);

        @Override
        public Type<CastDenied> type() {
            return TYPE;
        }
    }

    /** Spell cooldown: ready at {@code untilTick} (server game time), lasting {@code totalTicks}. */
    public record CooldownSet(Identifier spell, long untilTick, int totalTicks) implements CustomPacketPayload {
        public static final Type<CooldownSet> TYPE = new Type<>(OrbisArcana.id("cooldown_set"));
        public static final StreamCodec<ByteBuf, CooldownSet> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, CooldownSet::spell,
                ByteBufCodecs.VAR_LONG, CooldownSet::untilTick,
                ByteBufCodecs.VAR_INT, CooldownSet::totalTicks,
                CooldownSet::new);

        @Override
        public Type<CooldownSet> type() {
            return TYPE;
        }
    }

    private CastPayloads() {}
}
