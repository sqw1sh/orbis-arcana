package com.orbisarcana.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/** {@code loadout} attachment (§13.3): 3 radial-menu pages of 8 slots, plus the current selection. */
public final class Loadout {
    public static final int PAGES = 3;
    public static final int SLOTS = 8;

    private record Entry(int page, int slot, Identifier spell) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, PAGES - 1).fieldOf("page").forGetter(Entry::page),
                Codec.intRange(0, SLOTS - 1).fieldOf("slot").forGetter(Entry::slot),
                Identifier.CODEC.fieldOf("spell").forGetter(Entry::spell)
        ).apply(i, Entry::new));
    }

    public static final MapCodec<Loadout> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Entry.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(Loadout::entries),
            Codec.intRange(0, PAGES - 1).optionalFieldOf("page", 0).forGetter(l -> l.page),
            Codec.intRange(0, SLOTS - 1).optionalFieldOf("selected", 0).forGetter(l -> l.selected)
    ).apply(i, Loadout::new));

    private final Identifier[][] slots = new Identifier[PAGES][SLOTS];
    private int page;
    private int selected;

    public Loadout() {
        this(List.of(), 0, 0);
    }

    private Loadout(List<Entry> entries, int page, int selected) {
        entries.forEach(e -> slots[e.page()][e.slot()] = e.spell());
        this.page = page;
        this.selected = selected;
    }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (int p = 0; p < PAGES; p++) {
            for (int s = 0; s < SLOTS; s++) {
                if (slots[p][s] != null) out.add(new Entry(p, s, slots[p][s]));
            }
        }
        return out;
    }

    public Optional<Identifier> get(int page, int slot) {
        return Optional.ofNullable(slots[page][slot]);
    }

    public void set(int page, int slot, Optional<Identifier> spell) {
        slots[page][slot] = spell.orElse(null);
    }

    /** Clears every slot holding this spell (e.g. after it was forgotten). */
    public void removeEverywhere(Identifier spell) {
        for (Identifier[] pageSlots : slots) {
            for (int s = 0; s < SLOTS; s++) {
                if (spell.equals(pageSlots[s])) pageSlots[s] = null;
            }
        }
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.floorMod(page, PAGES);
    }

    public int selected() {
        return selected;
    }

    public void setSelected(int slot) {
        this.selected = Math.floorMod(slot, SLOTS);
    }

    public Optional<Identifier> selectedSpell() {
        return get(page, selected);
    }
}
