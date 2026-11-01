package com.orbisarcana.network;

import java.util.Optional;

import com.orbisarcana.OrbisArcana;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
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

    /** Assign a known spell to a radial slot, or clear it (§13.3). */
    public record LoadoutSet(int page, int slot, Optional<Identifier> spell) implements CustomPacketPayload {
        public static final Type<LoadoutSet> TYPE = new Type<>(OrbisArcana.id("loadout_set"));
        public static final StreamCodec<ByteBuf, LoadoutSet> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LoadoutSet::page,
                ByteBufCodecs.VAR_INT, LoadoutSet::slot,
                ByteBufCodecs.optional(Identifier.STREAM_CODEC), LoadoutSet::spell,
                LoadoutSet::new);

        @Override
        public Type<LoadoutSet> type() {
            return TYPE;
        }
    }

    /** Select page + slot as the active spell. */
    public record LoadoutSelect(int page, int slot) implements CustomPacketPayload {
        public static final Type<LoadoutSelect> TYPE = new Type<>(OrbisArcana.id("loadout_select"));
        public static final StreamCodec<ByteBuf, LoadoutSelect> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LoadoutSelect::page,
                ByteBufCodecs.VAR_INT, LoadoutSelect::slot,
                LoadoutSelect::new);

        @Override
        public Type<LoadoutSelect> type() {
            return TYPE;
        }
    }

    /** Turn a reached milestone of a known spell off or back on (§7.5). */
    public record MilestoneToggle(Identifier spell, int level, boolean disabled) implements CustomPacketPayload {
        public static final Type<MilestoneToggle> TYPE = new Type<>(OrbisArcana.id("milestone_toggle"));
        public static final StreamCodec<ByteBuf, MilestoneToggle> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, MilestoneToggle::spell,
                ByteBufCodecs.VAR_INT, MilestoneToggle::level,
                ByteBufCodecs.BOOL, MilestoneToggle::disabled,
                MilestoneToggle::new);

        @Override
        public Type<MilestoneToggle> type() {
            return TYPE;
        }
    }

    /** Altar action (§10.6): bind to a school or unbind; the server checks distance, conditions and cost. */
    public record AltarAction(BlockPos altar, int action, Optional<Identifier> school) implements CustomPacketPayload {
        public static final Type<AltarAction> TYPE = new Type<>(OrbisArcana.id("altar_action"));
        public static final StreamCodec<ByteBuf, AltarAction> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, AltarAction::altar,
                ByteBufCodecs.VAR_INT, AltarAction::action,
                ByteBufCodecs.optional(Identifier.STREAM_CODEC), AltarAction::school,
                AltarAction::new);

        @Override
        public Type<AltarAction> type() {
            return TYPE;
        }
    }

    /** Spend a talent point of the dominant school on {@code node} (§10.4). */
    public record TalentPick(Identifier school, String node) implements CustomPacketPayload {
        public static final Type<TalentPick> TYPE = new Type<>(OrbisArcana.id("talent_pick"));
        public static final StreamCodec<ByteBuf, TalentPick> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, TalentPick::school,
                ByteBufCodecs.stringUtf8(8), TalentPick::node,
                TalentPick::new);

        @Override
        public Type<TalentPick> type() {
            return TYPE;
        }
    }

    // ---- server -> client ----

    /**
     * Sent to the caster and nearby players; drives cast bars and magic circles. Circles are animated
     * client-side from {@code startTick}/{@code durationTicks}; positions are never streamed (§9.7).
     * {@code groundPoint} is set for GROUND spells, {@code targetId} (-1 if none) for TARGET spells,
     * {@code seal} is the dominant seal level (0 none, 1, 2 full).
     */
    public record CastStarted(int castId, int casterId, Identifier spell, long startTick, int durationTicks,
                              int mastery, Optional<Vec3> groundPoint, int targetId, int seal) implements CustomPacketPayload {
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
                ByteBufCodecs.VAR_INT, CastStarted::seal,
                CastStarted::new);

        @Override
        public Type<CastStarted> type() {
            return TYPE;
        }
    }

    /** Altar ritual started: a large world seal around the altar (§10.6). Ends with {@link CastEnded} of the same id. */
    public record RitualCircle(int circleId, int casterId, BlockPos altar, Identifier school, long startTick, int durationTicks) implements CustomPacketPayload {
        public static final Type<RitualCircle> TYPE = new Type<>(OrbisArcana.id("ritual_circle"));
        public static final StreamCodec<ByteBuf, RitualCircle> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RitualCircle::circleId,
                ByteBufCodecs.VAR_INT, RitualCircle::casterId,
                BlockPos.STREAM_CODEC, RitualCircle::altar,
                Identifier.STREAM_CODEC, RitualCircle::school,
                ByteBufCodecs.VAR_LONG, RitualCircle::startTick,
                ByteBufCodecs.VAR_INT, RitualCircle::durationTicks,
                RitualCircle::new);

        @Override
        public Type<RitualCircle> type() {
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

    /** Fire Heat stacks on an entity, shown above it to nearby players (0 clears). */
    /** A player's TOGGLE spell turned on or off (small circle under the feet while on, §5.3). */
    public record ToggleState(int playerId, Identifier spell, boolean on, int mastery) implements CustomPacketPayload {
        public static final Type<ToggleState> TYPE = new Type<>(OrbisArcana.id("toggle_state"));
        public static final StreamCodec<ByteBuf, ToggleState> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ToggleState::playerId,
                Identifier.STREAM_CODEC, ToggleState::spell,
                ByteBufCodecs.BOOL, ToggleState::on,
                ByteBufCodecs.VAR_INT, ToggleState::mastery,
                ToggleState::new);

        @Override
        public Type<ToggleState> type() {
            return TYPE;
        }
    }

    /** Echo stacks shown above a mob (Fire Heat, Ice Frost); {@code school} gives the colour. */
    public record HeatMark(int entityId, float stacks, Identifier school) implements CustomPacketPayload {
        public static final Type<HeatMark> TYPE = new Type<>(OrbisArcana.id("heat_mark"));
        public static final StreamCodec<ByteBuf, HeatMark> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HeatMark::entityId,
                ByteBufCodecs.FLOAT, HeatMark::stacks,
                Identifier.STREAM_CODEC, HeatMark::school,
                HeatMark::new);

        @Override
        public Type<HeatMark> type() {
            return TYPE;
        }
    }

    /** Echo of the dominant school for the HUD: {@code stacks} of {@code max} (§13.2). */
    public record EchoState(Identifier school, float stacks, int max) implements CustomPacketPayload {
        public static final Type<EchoState> TYPE = new Type<>(OrbisArcana.id("echo_state"));
        public static final StreamCodec<ByteBuf, EchoState> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, EchoState::school,
                ByteBufCodecs.FLOAT, EchoState::stacks,
                ByteBufCodecs.VAR_INT, EchoState::max,
                EchoState::new);

        @Override
        public Type<EchoState> type() {
            return TYPE;
        }
    }

    /**
     * Spell cooldown/charges state: {@code fullAt} is the server tick when all charges are back
     * ({@code core.Charges}); one charge returns per {@code cooldownTicks}.
     */
    public record CooldownSet(Identifier spell, long fullAt, int cooldownTicks) implements CustomPacketPayload {
        public static final Type<CooldownSet> TYPE = new Type<>(OrbisArcana.id("cooldown_set"));
        public static final StreamCodec<ByteBuf, CooldownSet> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, CooldownSet::spell,
                ByteBufCodecs.VAR_LONG, CooldownSet::fullAt,
                ByteBufCodecs.VAR_INT, CooldownSet::cooldownTicks,
                CooldownSet::new);

        @Override
        public Type<CooldownSet> type() {
            return TYPE;
        }
    }

    private CastPayloads() {}
}
