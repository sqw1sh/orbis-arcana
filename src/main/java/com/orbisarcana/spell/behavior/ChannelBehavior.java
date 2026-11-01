package com.orbisarcana.spell.behavior;

/**
 * A behavior for CHANNEL spells (§5.3): {@link #channelTick} runs every tick while the key is held;
 * {@link #release} runs once when the channel ends (key released, out of mana or max duration).
 */
public interface ChannelBehavior extends SpellBehavior {
    void channelTick(CastContext ctx, int elapsedTicks);
}
