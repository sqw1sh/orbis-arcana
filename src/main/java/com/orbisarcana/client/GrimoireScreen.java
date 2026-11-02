package com.orbisarcana.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.core.MasteryMath;
import com.orbisarcana.item.SpellScrollItem;
import com.orbisarcana.network.CastPayloads.MilestoneToggle;
import com.orbisarcana.progression.KnownSpells;
import com.orbisarcana.progression.Loadout;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.SpellDefinition;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Grimoire (§13.4), opened with G: known spells with school filter and search, the spell card
 * (numbers include mastery), milestones 5/10/15, and assignment to the radial menu.
 * Unlearned spells are not shown, only a counter.
 */
public class GrimoireScreen extends Screen {
    private static final int LIST_W = 150, ROW_H = 16, CARD_W = 230;
    private static final int PANEL = 0xC0101018, TEXT = 0xFFE8E8F0, DIM = 0xFF8A8A9A, GOOD = 0xFF7CE07C, OFF = 0xFFE07070;

    /** Clickable milestone switch, rebuilt every frame by {@link #drawCard}. */
    private record Toggle(int x, int y, int w, int h, Identifier spell, int level, boolean enabled) {}

    private final List<Toggle> toggles = new ArrayList<>();

    private @Nullable Identifier schoolFilter;
    private String search = "";
    private @Nullable Identifier selected;
    private int scroll;
    private int assignPage;
    private @Nullable EditBox searchBox;
    private String lastListedSearch = "";

    public GrimoireScreen() {
        super(Component.translatable("screen.orbisarcana.grimoire"));
    }

    @Override
    protected void init() {
        if (minecraft.player == null) return;
        assignPage = ClientCastState.loadout(minecraft.player).page();
        SchoolsScreen.addTabs(this, this::addRenderableWidget);
        int left = (width - LIST_W - CARD_W - 10) / 2;
        int top = 30;

        addRenderableWidget(Button.builder(schoolLabel(), b -> {
            int i = schoolFilter == null ? -1 : Schools.all().indexOf(schoolFilter);
            schoolFilter = i + 1 >= Schools.all().size() ? null : Schools.all().get(i + 1);
            scroll = 0;
            rebuildWidgets();
        }).bounds(left, top, LIST_W, 18).build());

        searchBox = new EditBox(font, left, top + 22, LIST_W, 16, Component.translatable("screen.orbisarcana.grimoire.search"));
        searchBox.setValue(search);
        searchBox.setHint(Component.translatable("screen.orbisarcana.grimoire.search"));
        searchBox.setResponder(text -> {
            search = text;
            scroll = 0;
        });
        addRenderableWidget(searchBox);

        List<Identifier> spells = filtered();
        if (selected == null && !spells.isEmpty()) selected = spells.get(0);
        int listTop = top + 44;
        int rows = Math.max(1, (height - listTop - 20) / ROW_H);
        for (int i = 0; i < rows && i + scroll < spells.size(); i++) {
            Identifier id = spells.get(i + scroll);
            addRenderableWidget(Button.builder(SpellScrollItem.spellName(id), b -> {
                selected = id;
                rebuildWidgets();
            }).bounds(left, listTop + i * ROW_H, LIST_W, ROW_H - 1).build());
        }

        // Assignment row: set buttons + 8 slots
        int cardX = left + LIST_W + 10;
        int slotY = height - 44;
        for (int p = 0; p < Loadout.PAGES; p++) {
            int page = p;
            addRenderableWidget(Button.builder(Component.literal((page == assignPage ? "▸" : "") + (page + 1)), b -> {
                assignPage = page;
                rebuildWidgets();
            }).bounds(cardX + p * 22, slotY - 20, 20, 16).build());
        }
        Loadout loadout = ClientCastState.loadout(minecraft.player);
        int slotW = (CARD_W - 7 * 2) / Loadout.SLOTS;
        for (int s = 0; s < Loadout.SLOTS; s++) {
            int slot = s;
            Optional<Identifier> inSlot = loadout.get(assignPage, slot);
            Component label = Component.literal(inSlot.map(id -> shortName(id, slotW - 4)).orElse(String.valueOf(slot + 1)));
            addRenderableWidget(Button.builder(label, b -> {
                // Clicking a slot that already holds the selected spell clears it
                boolean same = selected != null && inSlot.isPresent() && inSlot.get().equals(selected);
                ClientCastState.assign(minecraft.player, assignPage, slot, same ? Optional.empty() : Optional.ofNullable(selected));
                rebuildWidgets();
            }).bounds(cardX + s * (slotW + 2), slotY, slotW, 18).build());
        }
    }

    private Component schoolLabel() {
        return schoolFilter == null
                ? Component.translatable("screen.orbisarcana.grimoire.all_schools")
                : Component.translatable(Schools.nameKey(schoolFilter));
    }

    private List<Identifier> filtered() {
        List<Identifier> out = new ArrayList<>();
        var registries = minecraft.player.registryAccess();
        for (Identifier id : minecraft.player.getData(com.orbisarcana.data.ModAttachments.KNOWLEDGE).spells().keySet()) {
            Optional<SpellDefinition> def = SpellRegistry.get(registries, id);
            if (def.isEmpty()) continue;
            if (schoolFilter != null && !def.get().school().equals(schoolFilter)) continue;
            if (!search.isBlank() && !SpellScrollItem.spellName(id).getString().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) continue;
            out.add(id);
        }
        out.sort((a, b) -> SpellScrollItem.spellName(a).getString().compareToIgnoreCase(SpellScrollItem.spellName(b).getString()));
        return out;
    }

    private String shortName(Identifier id, int maxWidth) {
        String s = SpellScrollItem.spellName(id).getString();
        if (font.width(s) <= maxWidth) return s;
        while (s.length() > 1 && font.width(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, filtered().size() - 1);
        int next = Math.max(0, Math.min(max, scroll + (scrollY > 0 ? -1 : 1)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void tick() {
        // Search typed: refresh the list without losing focus every keystroke
        if (searchBox != null && !searchBox.getValue().equals(lastListedSearch)) {
            lastListedSearch = searchBox.getValue();
            boolean focused = searchBox.isFocused();
            rebuildWidgets();
            if (focused && searchBox != null) setFocused(searchBox);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Toggle t : toggles) {
            if (event.x() >= t.x() && event.x() < t.x() + t.w() && event.y() >= t.y() - 1 && event.y() < t.y() + t.h()) {
                // Predict locally; the server confirms through the knowledge sync
                minecraft.player.getData(com.orbisarcana.data.ModAttachments.KNOWLEDGE).progress(t.spell())
                        .ifPresent(sp -> sp.setMilestoneDisabled(t.level(), t.enabled()));
                ClientPacketDistributor.sendToServer(new MilestoneToggle(t.spell(), t.level(), t.enabled()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (minecraft.player == null) return;
        toggles.clear();
        int left = (width - LIST_W - CARD_W - 10) / 2;
        int cardX = left + LIST_W + 10;
        g.fill(left - 4, 26, left + LIST_W + 4, height - 16, PANEL);
        g.fill(cardX - 4, 26, cardX + CARD_W + 4, height - 16, PANEL);
        super.extractRenderState(g, mouseX, mouseY, partial);

        KnownSpells known = minecraft.player.getData(com.orbisarcana.data.ModAttachments.KNOWLEDGE);
        int total = SpellRegistry.ids(minecraft.player.registryAccess()).size();
        g.centeredText(font, Component.translatable("screen.orbisarcana.grimoire.title", known.spells().size(), total), width / 2 + 40, 10, TEXT);
        g.text(font, Component.translatable("screen.orbisarcana.grimoire.assign"), cardX, height - 76, DIM, false);

        if (selected == null) {
            g.text(font, Component.translatable("screen.orbisarcana.grimoire.empty"), cardX, 34, DIM, false);
            return;
        }
        Optional<SpellDefinition> def = SpellRegistry.get(minecraft.player.registryAccess(), selected);
        Optional<KnownSpells.SpellProgress> progress = known.progress(selected);
        if (def.isEmpty() || progress.isEmpty()) return;
        drawCard(g, cardX, 32, selected, def.get(), progress.get());
    }

    private void drawCard(GuiGraphicsExtractor g, int x, int y, Identifier id, SpellDefinition d, KnownSpells.SpellProgress p) {
        int level = p.level();
        g.text(font, SpellScrollItem.spellName(id), x, y, 0xFF000000 | d.rarity().color, true);
        y += 12;
        g.text(font, Component.translatable(Schools.nameKey(d.school())).append(" · ")
                .append(Component.translatable("rarity.orbisarcana." + d.rarity().id)), x, y, DIM, false);
        y += 11;
        g.text(font, Component.translatable("targeting.orbisarcana." + d.targeting().id()).append(" · ")
                .append(Component.translatable("cast_mode.orbisarcana." + d.cast().mode().id())), x, y, DIM, false);
        y += 14;

        double cost = d.cost() * MasteryMath.costMult(level);
        double castSec = d.cast().ticks() * MasteryMath.castTimeMult(level) / 20.0;
        g.text(font, Component.translatable("screen.orbisarcana.grimoire.stats",
                String.format("%.1f", cost), String.format("%.2f", castSec), String.format("%.1f", d.cooldownTicks() / 20.0)), x, y, TEXT, false);
        y += 11;
        var params = d.paramsAt(level, l -> !p.isMilestoneEnabled(l));
        double primary = params.getOrDefault(d.primaryStat(), 0.0) * MasteryMath.primaryStatMult(level);
        g.text(font, Component.translatable("screen.orbisarcana.grimoire.primary",
                Component.translatable("stat.orbisarcana." + d.primaryStat()), String.format("%.1f", primary)), x, y, TEXT, false);
        y += 16;

        // Mastery bar
        g.text(font, Component.translatable("screen.orbisarcana.grimoire.mastery", level, MasteryMath.MAX_LEVEL), x, y, TEXT, false);
        y += 11;
        int need = MasteryMath.xpRequired(level, d.rarity());
        float frac = need <= 0 ? 1f : (float) Math.min(1.0, p.xp() / need);
        g.fill(x, y, x + CARD_W, y + 4, 0xFF2A2A40);
        g.fill(x, y, x + (int) (CARD_W * frac), y + 4, 0xFF7A8CFF);
        y += 10;

        // Milestones (§7.5): shown before they are reached; reached ones can be switched off.
        // Clipped above the radial-menu block.
        int bottom = height - 84;
        int marker = font.width("[✔] ");
        for (int m : SpellDefinition.MILESTONE_LEVELS) {
            boolean reached = level >= m;
            boolean enabled = p.isMilestoneEnabled(m);
            if (y + 9 > bottom) {
                g.text(font, Component.literal("…"), x, y - 2, DIM, false);
                return;
            }
            if (reached) {
                g.text(font, Component.literal(enabled ? "[✔]" : "[✖]"), x, y, enabled ? GOOD : OFF, false);
                toggles.add(new Toggle(x, y, marker, 9, id, m, enabled));
            }
            String key = "spell." + id.getNamespace() + "." + id.getPath() + ".m" + m;
            Component line = Component.translatable("screen.orbisarcana.grimoire.milestone." + m).append(": ").append(Component.translatable(key));
            int color = !reached ? DIM : enabled ? GOOD : OFF;
            for (var part : font.split(line, CARD_W - marker)) {
                if (y + 9 > bottom) {
                    g.text(font, Component.literal("…"), x + marker, y - 2, DIM, false);
                    return;
                }
                g.text(font, part, x + marker, y, color, false);
                y += 10;
            }
            y += 2;
        }
    }
}
