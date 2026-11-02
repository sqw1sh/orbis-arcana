package com.orbisarcana.network;

import com.orbisarcana.OrbisArcana;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: play a VFX effect (roadmap V4) at a point, oriented by {@code dir} (emitter forward, may be zero),
 * tinted ({@code tint} 0xRRGGBB, 0 = the effect's own colours), scaled, and attached to an entity while it lives
 * ({@code attach} -1 = none).
 */
public record VfxPlay(Identifier effect, double x, double y, double z, float dx, float dy, float dz, int tint, float scale, int attach)
        implements CustomPacketPayload {
    public static final Type<VfxPlay> TYPE = new Type<>(OrbisArcana.id("vfx_play"));
    public static final StreamCodec<ByteBuf, VfxPlay> CODEC = new StreamCodec<>() {
        @Override
        public VfxPlay decode(ByteBuf buf) {
            return new VfxPlay(Identifier.STREAM_CODEC.decode(buf), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(),
                    ByteBufCodecs.VAR_INT.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, VfxPlay v) {
            Identifier.STREAM_CODEC.encode(buf, v.effect());
            buf.writeDouble(v.x());
            buf.writeDouble(v.y());
            buf.writeDouble(v.z());
            buf.writeFloat(v.dx());
            buf.writeFloat(v.dy());
            buf.writeFloat(v.dz());
            ByteBufCodecs.VAR_INT.encode(buf, v.tint());
            buf.writeFloat(v.scale());
            ByteBufCodecs.VAR_INT.encode(buf, v.attach());
        }
    };

    @Override
    public Type<VfxPlay> type() {
        return TYPE;
    }
}
