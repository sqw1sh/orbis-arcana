package com.orbisarcana.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.core.CastDenial;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.network.CastPayloads.LoadoutSelect;
import com.orbisarcana.network.CastPayloads.LoadoutSet;
import com.orbisarcana.progression.Loadout;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Client-side view of the local player's casting: the selected loadout slot, own active cast,
 * cooldowns and the last denial. Loadout changes are applied locally at once and confirmed by the
 * server's attachment sync.
 */
public final class ClientCastState {
    public record OwnCast(int castId, Identifier spell, long startTick, int durationTicks) {}

    public record Cooldown(long untilTick, int totalTicks) {}

    private static @Nullable OwnCast ownCast;
    private static final Map<Identifier, Cooldown> COOLDOWNS = new HashMap<>();
    private static @Nullable CastDenial lastDenial;
    private static long lastDenialTick;

    public static Loadout loadout(Player player) {
        return player.getData(ModAttachments.LOADOUT);
    }

    public static Optional<Identifier> selected(Player player) {
        return loadout(player).selectedSpell();
    }

    /** Selects a slot (prediction + server request). */
    public static void select(Player player, int page, int slot) {
        Loadout l = loadout(player);
        l.setPage(page);
        l.setSelected(slot);
        ClientPacketDistributor.sendToServer(new LoadoutSelect(l.page(), l.selected()));
    }

    /** Moves to the next/previous filled slot on the current page. */
    public static void cycle(Player player, int delta) {
        Loadout l = loadout(player);
        for (int i = 1; i <= Loadout.SLOTS; i++) {
            int slot = Math.floorMod(l.selected() + delta * i, Loadout.SLOTS);
            if (l.get(l.page(), slot).isPresent()) {
                select(player, l.page(), slot);
                return;
            }
        }
    }

    /** Switches page, keeping the slot if it is filled there, otherwise the first filled slot. */
    public static void selectPage(Player player, int page) {
        Loadout l = loadout(player);
        int slot = l.selected();
        if (l.get(page, slot).isEmpty()) {
            for (int s = 0; s < Loadout.SLOTS; s++) {
                if (l.get(page, s).isPresent()) {
                    slot = s;
                    break;
                }
            }
        }
        select(player, page, slot);
    }

    /** Assigns (or clears with empty) a slot (prediction + server request). */
    public static void assign(Player player, int page, int slot, Optional<Identifier> spell) {
        Loadout l = loadout(player);
        spell.ifPresent(l::removeEverywhere);
        l.set(page, slot, spell);
        ClientPacketDistributor.sendToServer(new LoadoutSet(page, slot, spell));
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
