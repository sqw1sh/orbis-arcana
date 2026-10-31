package com.orbisarcana.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.core.CastDenial;
import com.orbisarcana.data.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side view of the local player's casting: selected spell, own active cast, cooldowns, last denial.
 * Selection cycles through known spells until the radial menu (S4) replaces it.
 */
public final class ClientCastState {
    public record OwnCast(int castId, Identifier spell, long startTick, int durationTicks) {}

    public record Cooldown(long untilTick, int totalTicks) {}

    private static int selectedIndex;
    private static @Nullable OwnCast ownCast;
    private static final Map<Identifier, Cooldown> COOLDOWNS = new HashMap<>();
    private static @Nullable CastDenial lastDenial;
    private static long lastDenialTick;

    public static List<Identifier> knownSpells(Player player) {
        return player.getData(ModAttachments.KNOWLEDGE).spells().keySet().stream().sorted().toList();
    }

    public static Optional<Identifier> selected(Player player) {
        List<Identifier> known = knownSpells(player);
        if (known.isEmpty()) return Optional.empty();
        return Optional.of(known.get(Math.floorMod(selectedIndex, known.size())));
    }

    public static void cycle(int delta) {
        selectedIndex += delta;
    }

    public static @Nullable OwnCast ownCast() {
        return ownCast;
    }

    static void setOwnCast(@Nullable OwnCast cast) {
        ownCast = cast;
    }

    public static Optional<Cooldown> cooldown(Identifier spell, long now) {
        Cooldown cd = COOLDOWNS.get(spell);
        return cd != null && cd.untilTick() > now ? Optional.of(cd) : Optional.empty();
    }

    static void setCooldown(Identifier spell, Cooldown cd) {
        COOLDOWNS.put(spell, cd);
    }

    public static @Nullable CastDenial lastDenial(long now) {
        return lastDenial != null && now - lastDenialTick < 40 ? lastDenial : null;
    }

    static void deny(CastDenial reason) {
        lastDenial = reason;
        lastDenialTick = gameTime();
    }

    public static long gameTime() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    /** Called on disconnect. */
    static void reset() {
        ownCast = null;
        COOLDOWNS.clear();
        lastDenial = null;
    }

    private ClientCastState() {}
}
