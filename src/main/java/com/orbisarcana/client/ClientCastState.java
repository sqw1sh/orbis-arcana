package com.orbisarcana.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.core.CastDenial;
import com.orbisarcana.core.Charges;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.network.CastPayloads.LoadoutSelect;
import com.orbisarcana.network.CastPayloads.LoadoutSet;
import com.orbisarcana.progression.Loadout;
import com.orbisarcana.spell.SpellRegistry;

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

    /** {@code fullAt}: tick when all charges are back; one charge per {@code cooldownTicks}. */
    public record Cooldown(long fullAt, int cooldownTicks) {}

    /**
     * Charges of a spell right now: {@code readyAt} is when at least one charge is available,
     * {@code nextChargeAt} when the next missing charge returns (-1 when all are full).
     */
    public record ChargeState(int max, int available, long readyAt, long nextChargeAt, int cooldownTicks) {
        public boolean blocked(long now) {
            return available <= 0 && readyAt > now;
        }
    }

    /** Echo stacks of the dominant school for the HUD, and when they last changed. */
    public record Echo(Identifier school, float stacks, int max, long tick) {}

    private static @Nullable OwnCast ownCast;
    private static @Nullable Echo echo;
    private static final Map<Identifier, Cooldown> COOLDOWNS = new HashMap<>();
    private static @Nullable CastDenial lastDenial;
    private static long lastDenialTick;
    /** Own toggles that are on, in the order they were turned on. */
    private static final java.util.Set<Identifier> TOGGLES = new java.util.LinkedHashSet<>();

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

    public static @Nullable Echo echo() {
        return echo;
    }

    static void setEcho(Identifier school, float stacks, int max) {
        echo = new Echo(school, stacks, max, gameTime());
    }

    public static java.util.Set<Identifier> ownToggles() {
        return TOGGLES;
    }

    static void setToggle(int playerId, Identifier spell, boolean on) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getId() != playerId) return;
        if (on) TOGGLES.add(spell);
        else TOGGLES.remove(spell);
    }

    public static @Nullable OwnCast ownCast() {
        return ownCast;
    }

    static void setOwnCast(@Nullable OwnCast cast) {
        ownCast = cast;
    }

    public static ChargeState charges(Player player, Identifier spell, long now) {
        int max = maxCharges(player, spell);
        Cooldown cd = COOLDOWNS.get(spell);
        if (cd == null || cd.fullAt() <= now || cd.cooldownTicks() <= 0) return new ChargeState(max, max, now, -1, 0);
        int available = Charges.available(cd.fullAt(), now, max, cd.cooldownTicks());
        int missing = max - available;
        long next = missing <= 0 ? -1 : cd.fullAt() - (long) (missing - 1) * cd.cooldownTicks();
        return new ChargeState(max, available, Charges.readyAt(cd.fullAt(), max, cd.cooldownTicks()), next, cd.cooldownTicks());
    }

    /** Charges from the synced spell definition, own mastery and enabled milestones (same rule as the server). */
    public static int maxCharges(Player player, Identifier spell) {
        var def = SpellRegistry.get(player.registryAccess(), spell);
        var progress = player.getData(ModAttachments.KNOWLEDGE).progress(spell);
        if (def.isEmpty() || progress.isEmpty()) return 1;
        var p = progress.get();
        double charges = def.get().paramsAt(p.level(), l -> !p.isMilestoneEnabled(l)).getOrDefault("charges", 1.0);
        return Math.max(1, (int) Math.round(charges));
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
        echo = null;
        COOLDOWNS.clear();
        lastDenial = null;
        TOGGLES.clear();
    }

    private ClientCastState() {}
}
