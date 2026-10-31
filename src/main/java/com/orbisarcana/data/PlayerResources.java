package com.orbisarcana.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * {@code resources} attachment (§15.3): current mana and spell cooldowns
 * ({@code spellId -> gameTime when the cooldown ends}).
 */
public final class PlayerResources {
    public static final MapCodec<PlayerResources> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("mana").forGetter(r -> r.mana),
            Codec.unboundedMap(Identifier.CODEC, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(r -> r.cooldowns)
    ).apply(i, PlayerResources::new));

    /** Only mana goes to the client here; cooldowns get their own packet in S2. */
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerResources> SYNC_CODEC = ByteBufCodecs.DOUBLE
            .<PlayerResources>map(m -> new PlayerResources(m, Map.of()), r -> r.mana).cast();

    private double mana;
    private final Map<Identifier, Long> cooldowns;

    /** Not saved: last game tick the player dealt or took damage. */
    private long lastCombatTick = Long.MIN_VALUE / 2;
    /** Not saved: mana value last sent to the client. */
    private double lastSyncedMana = -1;

    public PlayerResources() {
        this(-1, Map.of());
    }

    private PlayerResources(double mana, Map<Identifier, Long> cooldowns) {
        this.mana = mana;
        this.cooldowns = new HashMap<>(cooldowns);
    }

    /** Mana is -1 until the first server tick initialises it to max. */
    public double mana() {
        return mana;
    }

    public void setMana(double mana) {
        this.mana = mana;
    }

    public Map<Identifier, Long> cooldowns() {
        return cooldowns;
    }

    public long lastCombatTick() {
        return lastCombatTick;
    }

    public void markCombat(long gameTime) {
        this.lastCombatTick = gameTime;
    }

    double lastSyncedMana() {
        return lastSyncedMana;
    }

    void markSynced() {
        this.lastSyncedMana = mana;
    }
}
