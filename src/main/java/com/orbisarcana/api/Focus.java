package com.orbisarcana.api;

import org.jetbrains.annotations.ApiStatus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbisarcana.core.FocusRules;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data component {@code orbisarcana:focus} (§11.1, §16): any item that carries it works as a focus of that type and
 * tier (1-3) while held, so another mod can turn its own sword into a wand.
 */
@ApiStatus.Experimental
public record Focus(FocusRules.Type type, int tier) {
    private static final Codec<FocusRules.Type> TYPE_CODEC = Codec.STRING.xmap(
            s -> FocusRules.Type.valueOf(s.toUpperCase(java.util.Locale.ROOT)), t -> t.name().toLowerCase(java.util.Locale.ROOT));

    public static final Codec<Focus> CODEC = RecordCodecBuilder.create(i -> i.group(
            TYPE_CODEC.fieldOf("type").forGetter(Focus::type),
            Codec.intRange(1, FocusRules.TIERS).fieldOf("tier").forGetter(Focus::tier)).apply(i, Focus::new));

    public static final StreamCodec<ByteBuf, Focus> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> FocusRules.Type.values()[i], FocusRules.Type::ordinal), Focus::type,
            ByteBufCodecs.VAR_INT, Focus::tier, Focus::new);
}
