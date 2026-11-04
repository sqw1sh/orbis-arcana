package com.orbisarcana.client.grimoire;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.client.ClientCastState;
import com.orbisarcana.client.ui.Ui;
import com.orbisarcana.core.MasteryMath;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.item.SpellScrollItem;
import com.orbisarcana.network.CastPayloads.MilestoneToggle;
import com.orbisarcana.progression.KnownSpells;
import com.orbisarcana.progression.Loadout;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.SpellDefinition;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Spells tab: the known spells (school filter, search) and the card of the selected one: stats with mastery applied,
 * the mastery bar, the milestone path 5/10/15 (reached milestones can be switched off) and assignment to the radial
 * menu. Unlearned spells are not listed, only counted in the header.
 */
class SpellsTab extends GrimoireTab {
    private static final int LIST_W = 156, ROW_H = 20;
    private static final int[] MILESTONES = SpellDefinition.MILESTONE_LEVELS;

    private static @Nullable Identifier schoolFilter;
    private static String search = "";
    private static int assignPage = -1;
    private int scroll;
    private @Nullable EditBox searchBox;
    /** Milestone under the mouse this frame (its text is shown below the path). */
    private int hoveredMilestone;

    SpellsTab(GrimoireScreen screen) {
        super(screen);
    }

    @Override
    void init() {
        if (assignPage < 0) assignPage = ClientCastState.loadout(player()).page();
        searchBox = screen.addTabWidget(new EditBox(font, x + 18, y + 26, LIST_W - 22, 10, Component.translatable("screen.orbisarcana.grimoire.search")));
        searchBox.setBordered(false);
        searchBox.setValue(search);
        searchBox.setHint(Component.translatable("screen.orbisarcana.grimoire.search"));
        searchBox.setTextColor(Ui.TEXT);
        searchBox.setResponder(text -> {
            search = text;
            scroll = 0;
        });
        List<Identifier> spells = filtered();
        if ((GrimoireScreen.selectedSpell == null || !spells.contains(GrimoireScreen.selectedSpell)) && !spells.isEmpty()) {
            GrimoireScreen.selectedSpell = spells.get(0);
        }
    }

    private List<Identifier> filtered() {
        List<Identifier> out = new ArrayList<>();
        var registries = player().registryAccess();
        for (Identifier id : player().getData(ModAttachments.KNOWLEDGE).spells().keySet()) {
            Optional<SpellDefinition> def = SpellRegistry.get(registries, id);
            if (def.isEmpty()) continue;
            if (schoolFilter != null && !def.get().school().equals(schoolFilter)) continue;
            if (!search.isBlank() && !SpellScrollItem.spellName(id).getString().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) continue;
            out.add(id);
        }
        out.sort((a, b) -> SpellScrollItem.spellName(a).getString().compareToIgnoreCase(SpellScrollItem.spellName(b).getString()));
        return out;
    }

    private int listTop() {
        return y + 42;
    }

    private int rows() {
        return Math.max(1, (y + h - listTop()) / ROW_H);
    }

    @Override
    void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        renderList(g, mouseX, mouseY);
        int cx = x + LIST_W + 10, cw = w - LIST_W - 10;
        g.fill(cx - 6, y, cx - 5, y + h, Ui.LINE);
        Identifier id = GrimoireScreen.selectedSpell;
        KnownSpells known = player().getData(ModAttachments.KNOWLEDGE);
        Optional<SpellDefinition> def = id == null ? Optional.empty() : SpellRegistry.get(player().registryAccess(), id);
        Optional<KnownSpells.SpellProgress> progress = id == null ? Optional.empty() : known.progress(id);
        if (def.isEmpty() || progress.isEmpty()) {
            wrap(g, Component.translatable("screen.orbisarcana.grimoire.empty"), cx, y + 4, cw, Ui.TEXT_DIM, 3);
            return;
        }
        renderCard(g, cx, cw, id, def.get(), progress.get(), mouseX, mouseY);
    }

    private void renderList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // school filter: < sigil name >, click the halves to cycle
        Ui.inset(g, x, y, LIST_W, 18);
        Component school = schoolFilter == null ? Component.translatable("screen.orbisarcana.grimoire.all_schools")
                : Component.translatable(Schools.nameKey(schoolFilter));
        g.text(font, "‹", x + 5, y + 5, Ui.TEXT_DIM, false);
        g.text(font, "›", x + LIST_W - 9, y + 5, Ui.TEXT_DIM, false);
        int nameW = font.width(school) + (schoolFilter == null ? 0 : 14);
        int nx = x + (LIST_W - nameW) / 2;
        if (schoolFilter != null) {
            Ui.sigil(g, schoolFilter, nx, y + 1, 16, 0xFF);
            nx += 14;
        }
        g.text(font, school, nx + 2, y + 5, schoolFilter == null ? Ui.TEXT : Ui.opaque(Schools.color(schoolFilter)), false);

        Ui.inset(g, x, y + 22, LIST_W, 16);
        Ui.icon(g, "target", x + 5, y + 25, Ui.TEXT_DIM);

        List<Identifier> spells = filtered();
        int rows = rows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, spells.size() - rows)));
        int known = player().getData(ModAttachments.KNOWLEDGE).spells().size();
        if (spells.isEmpty()) {
            wrap(g, Component.translatable(known == 0 ? "screen.orbisarcana.grimoire.empty" : "screen.orbisarcana.grimoire.nothing_found"),
                    x + 2, listTop() + 4, LIST_W - 4, Ui.TEXT_DIM, 4);
            return;
        }
        for (int i = 0; i < rows && i + scroll < spells.size(); i++) {
            Identifier id = spells.get(i + scroll);
            int ry = listTop() + i * ROW_H;
            var def = SpellRegistry.get(player().registryAccess(), id).orElseThrow();
            boolean selected = id.equals(GrimoireScreen.selectedSpell), hover = inside(mouseX, mouseY, x, ry, LIST_W, ROW_H - 1);
            if (selected) {
                g.fill(x, ry, x + LIST_W, ry + ROW_H - 1, 0x305FE3C8);
                g.fill(x, ry, x + 1, ry + ROW_H - 1, Ui.TEAL);
            } else if (hover) {
                g.fill(x, ry, x + LIST_W, ry + ROW_H - 1, 0x18FFFFFF);
            }
            int rarity = Ui.opaque(def.rarity().color);
            Ui.slot(g, x + 2, ry + 1, 18, rarity);
            Ui.sigil(g, def.school(), x + 3, ry + 2, 16, 0xFF);
            int level = player().getData(ModAttachments.KNOWLEDGE).progress(id).map(p -> p.level()).orElse(1);
            String roman = Ui.roman(level);
            g.text(font, roman, x + LIST_W - 4 - font.width(roman), ry + 6, level >= MasteryMath.MAX_LEVEL ? Ui.GOLD : Ui.TEXT_DIM, false);
            g.text(font, ellipsize(SpellScrollItem.spellName(id).getString(), LIST_W - 30 - font.width(roman)), x + 24, ry + 6, rarity, false);
        }
        // scrollbar
        if (spells.size() > rows) {
            int trackH = rows * ROW_H, thumbH = Math.max(10, trackH * rows / spells.size());
            int ty = listTop() + (trackH - thumbH) * scroll / Math.max(1, spells.size() - rows);
            g.fill(x + LIST_W + 1, listTop(), x + LIST_W + 2, listTop() + trackH, Ui.LINE);
            g.fill(x + LIST_W, ty, x + LIST_W + 3, ty + thumbH, Ui.GOLD_DARK);
        }
    }

    private void renderCard(GuiGraphicsExtractor g, int cx, int cw, Identifier id, SpellDefinition d, KnownSpells.SpellProgress p, int mouseX, int mouseY) {
        int level = p.level(), rarity = Ui.opaque(d.rarity().color);
        int cy = y;

        // header
        Ui.slot(g, cx, cy, 30, rarity);
        Ui.sigil(g, d.school(), cx + 7, cy + 7, 16, 0xFF);
        g.text(font, SpellScrollItem.spellName(id), cx + 36, cy + 3, rarity, true);
        Component sub = Component.translatable(Schools.nameKey(d.school())).withColor(Ui.opaque(Schools.color(d.school())))
                .append(Component.literal(" · ").withColor(Ui.TEXT_DIM))
                .append(Component.translatable("rarity.orbisarcana." + d.rarity().id).withColor(rarity));
        g.text(font, sub, cx + 36, cy + 13, -1, false);
        String how = Component.translatable("targeting.orbisarcana." + d.targeting().id()).getString() + " · "
                + Component.translatable("cast_mode.orbisarcana." + d.cast().mode().id()).getString();
        g.text(font, ellipsize(how, cw - 36), cx + 36, cy + 23, Ui.TEXT_DIM, false);
        cy += 36;

        // stat tiles, mastery applied
        int tw = (cw - 9) / 4;
        double cost = d.cost() * MasteryMath.costMult(level);
        double castSec = d.cast().ticks() * MasteryMath.castTimeMult(level) / 20.0;
        var params = d.paramsAt(level, l -> !p.isMilestoneEnabled(l));
        double primary = params.getOrDefault(d.primaryStat(), 0.0) * MasteryMath.primaryStatMult(level);
        tile(g, cx, cy, tw, Component.translatable("screen.orbisarcana.grimoire.cost"), String.format("%.1f", cost), Ui.MANA);
        tile(g, cx + tw + 3, cy, tw, Component.translatable("screen.orbisarcana.grimoire.cast"), String.format("%.2f s", castSec), Ui.TEXT);
        tile(g, cx + 2 * (tw + 3), cy, tw, Component.translatable("screen.orbisarcana.grimoire.cooldown"), String.format("%.1f s", d.cooldownTicks() / 20.0), Ui.TEXT);
        tile(g, cx + 3 * (tw + 3), cy, cw - 3 * (tw + 3), Component.translatable("stat.orbisarcana." + d.primaryStat()), String.format("%.1f", primary), Ui.GOLD);
        cy += 30;

        // mastery
        caption(g, Component.translatable("screen.orbisarcana.grimoire.mastery_title"), cx, cy, cw - 40);
        String lv = Ui.roman(level) + " / " + Ui.roman(MasteryMath.MAX_LEVEL);
        g.text(font, lv, cx + cw - font.width(lv), cy, Ui.GOLD, false);
        cy += 11;
        int need = MasteryMath.xpRequired(level, d.rarity());
        double frac = level >= MasteryMath.MAX_LEVEL || need <= 0 ? 1 : Math.min(1, p.xp() / need);
        Ui.bar(g, cx, cy, cw, 7, frac, "bar_fill", Ui.opaque(Schools.color(d.school())), true);
        cy += 12;

        // milestone path: a track from level 1 to 15 with nodes at 5/10/15 and a marker at the current level
        int px = cx + 8, pw = cw - 16, trackY = cy + 8;
        g.fill(px, trackY, px + pw, trackY + 1, Ui.LINE);
        int reachedX = px + (int) (pw * (Math.min(level, MasteryMath.MAX_LEVEL) - 1) / (double) (MasteryMath.MAX_LEVEL - 1));
        g.fill(px, trackY, reachedX, trackY + 1, Ui.GOLD);
        hoveredMilestone = 0;
        int focus = 0;
        for (int m : MILESTONES) {
            int nx = px + (int) (pw * (m - 1) / (double) (MasteryMath.MAX_LEVEL - 1)) - 8;
            boolean reached = level >= m, enabled = p.isMilestoneEnabled(m);
            if (inside(mouseX, mouseY, nx, trackY - 8, 16, 16)) hoveredMilestone = m;
            int frame = !reached ? Ui.LINE : enabled ? Ui.GOLD : Ui.DANGER;
            Ui.slot(g, nx, trackY - 8, 16, frame);
            String num = Integer.toString(m);
            g.text(font, num, nx + 8 - font.width(num) / 2, trackY - 4, reachedX >= nx + 8 && enabled ? Ui.GOLD : Ui.TEXT_DIM, false);
            if (focus == 0 && !reached) focus = m;
        }
        g.fill(reachedX - 1, trackY - 2, reachedX + 2, trackY + 3, Ui.TEAL);
        if (hoveredMilestone != 0) focus = hoveredMilestone;
        if (focus == 0) focus = MILESTONES[MILESTONES.length - 1];
        cy += 20;

        // the focused milestone: title, state and effect
        boolean reached = level >= focus, enabled = p.isMilestoneEnabled(focus);
        Component state = !reached ? Component.translatable("screen.orbisarcana.grimoire.milestone_locked", focus).withColor(Ui.TEXT_DIM)
                : enabled ? Component.translatable("screen.orbisarcana.grimoire.milestone_on").withColor(0xFF7CE07C)
                : Component.translatable("screen.orbisarcana.grimoire.milestone_off").withColor(Ui.DANGER);
        g.text(font, Component.translatable("screen.orbisarcana.grimoire.milestone." + focus), cx, cy, reached ? Ui.GOLD : Ui.TEXT_DIM, false);
        g.text(font, state, cx + cw - font.width(state), cy, -1, false);
        cy += 11;
        int assignY = y + h - 20;
        int maxLines = Math.max(1, (assignY - 14 - cy) / 10);
        wrap(g, Component.translatable("spell." + id.getNamespace() + "." + id.getPath() + ".m" + focus), cx, cy, cw,
                reached && enabled ? Ui.TEXT : Ui.TEXT_DIM, maxLines);

        renderAssign(g, cx, cw, assignY, id, mouseX, mouseY);
    }

    /** Radial menu assignment: set dots and the 8 slots of that set. */
    private void renderAssign(GuiGraphicsExtractor g, int cx, int cw, int ay, Identifier selected, int mouseX, int mouseY) {
        g.fill(cx, ay - 5, cx + cw, ay - 4, Ui.LINE);
        Component label = Component.translatable("screen.orbisarcana.grimoire.set");
        g.text(font, label, cx, ay + 5, Ui.TEXT_DIM, false);
        int dx = cx + font.width(label) + 4;
        for (int p = 0; p < Loadout.PAGES; p++) {
            Ui.sprite(g, p == assignPage ? "dot" : "dot_empty", dx + p * 9, ay + 5, 7, 7, p == assignPage ? Ui.TEAL : Ui.TEXT_DIM);
        }
        Loadout loadout = ClientCastState.loadout(player());
        int sx0 = cx + cw - Loadout.SLOTS * 20 + 2;
        for (int s = 0; s < Loadout.SLOTS; s++) {
            int sx = sx0 + s * 20;
            Optional<Identifier> in = loadout.get(assignPage, s);
            boolean hover = inside(mouseX, mouseY, sx, ay, 18, 18);
            boolean same = in.isPresent() && in.get().equals(selected);
            Ui.slot(g, sx, ay, 18, hover ? Ui.TEAL : same ? Ui.GOLD : in.isPresent() ? Ui.LINE : 0);
            in.flatMap(i -> SpellRegistry.get(player().registryAccess(), i)).ifPresent(d -> Ui.sigil(g, d.school(), sx + 1, ay + 1, 16, 0xFF));
            if (in.isEmpty()) g.text(font, Integer.toString(s + 1), sx + 9 - font.width(Integer.toString(s + 1)) / 2, ay + 5, Ui.LINE, false);
            if (hover) {
                List<Component> tip = new ArrayList<>();
                in.ifPresent(i -> tip.add(SpellScrollItem.spellName(i)));
                tip.add(Component.translatable(same ? "screen.orbisarcana.grimoire.slot_clear" : "screen.orbisarcana.grimoire.slot_assign").withColor(Ui.TEXT_DIM));
                tooltip(g, tip, mouseX, mouseY);
            }
        }
    }

    @Override
    boolean mouseClicked(double mx, double my, int button) {
        // school filter halves
        if (inside(mx, my, x, y, LIST_W, 18)) {
            List<Identifier> all = Schools.all();
            int i = schoolFilter == null ? -1 : all.indexOf(schoolFilter);
            int step = mx < x + LIST_W / 2.0 ? -1 : 1;
            int next = Math.floorMod(i + 1 + step, all.size() + 1) - 1;
            schoolFilter = next < 0 ? null : all.get(next);
            scroll = 0;
            return true;
        }
        // list rows
        List<Identifier> spells = filtered();
        for (int i = 0; i < rows() && i + scroll < spells.size(); i++) {
            if (inside(mx, my, x, listTop() + i * ROW_H, LIST_W, ROW_H - 1)) {
                GrimoireScreen.selectedSpell = spells.get(i + scroll);
                com.orbisarcana.client.sound.CastSounds.ui(com.orbisarcana.registry.ModSounds.UI_CLICK.get(), 1.2f);
                return true;
            }
        }
        Identifier id = GrimoireScreen.selectedSpell;
        if (id == null) return false;
        int cx = x + LIST_W + 10, cw = w - LIST_W - 10, ay = y + h - 20;
        // milestone switch: only reached milestones
        if (hoveredMilestone != 0) {
            var progress = player().getData(ModAttachments.KNOWLEDGE).progress(id);
            if (progress.isPresent() && progress.get().level() >= hoveredMilestone) {
                boolean disable = progress.get().isMilestoneEnabled(hoveredMilestone);
                // predicted locally; the server confirms through the knowledge sync
                progress.get().setMilestoneDisabled(hoveredMilestone, disable);
                ClientPacketDistributor.sendToServer(new MilestoneToggle(id, hoveredMilestone, disable));
                return true;
            }
        }
        // set dots
        int dx = cx + font.width(Component.translatable("screen.orbisarcana.grimoire.set")) + 4;
        for (int p = 0; p < Loadout.PAGES; p++) {
            if (inside(mx, my, dx + p * 9 - 1, ay + 3, 9, 11)) {
                assignPage = p;
                return true;
            }
        }
        // slots: assign, or clear when the slot already holds this spell
        int sx0 = cx + cw - Loadout.SLOTS * 20 + 2;
        Loadout loadout = ClientCastState.loadout(player());
        for (int s = 0; s < Loadout.SLOTS; s++) {
            if (inside(mx, my, sx0 + s * 20, ay, 18, 18)) {
                Optional<Identifier> in = loadout.get(assignPage, s);
                boolean same = in.isPresent() && in.get().equals(id);
                ClientCastState.assign(player(), assignPage, s, same ? Optional.empty() : Optional.of(id));
                com.orbisarcana.client.sound.CastSounds.ui(com.orbisarcana.registry.ModSounds.UI_ASSIGN.get(), same ? 0.8f : 1f);
                return true;
            }
        }
        return false;
    }

    @Override
    boolean mouseScrolled(double mx, double my, double delta) {
        if (!inside(mx, my, x, listTop(), LIST_W + 4, rows() * ROW_H)) return false;
        scroll = Math.max(0, scroll + (delta > 0 ? -1 : 1));
        return true;
    }
}
