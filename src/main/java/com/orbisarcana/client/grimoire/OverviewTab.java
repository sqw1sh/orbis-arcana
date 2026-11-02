package com.orbisarcana.client.grimoire;

import java.util.List;

import com.orbisarcana.client.ClientCastState;
import com.orbisarcana.client.ui.Ui;
import com.orbisarcana.core.MasteryMath;
import com.orbisarcana.core.SchoolRules;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.item.SpellScrollItem;
import com.orbisarcana.progression.KnownSpells;
import com.orbisarcana.progression.Loadout;
import com.orbisarcana.school.Attunement;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Overview tab: the mage (model following the mouse, dominant school), totals (spells learned, mastered, average
 * mastery), the resonance of every school and the current radial set. Clicking a school opens it in the Schools tab.
 */
class OverviewTab extends GrimoireTab {
    private static final int DOLL_W = 96;

    OverviewTab(GrimoireScreen screen) {
        super(screen);
    }

    @Override
    void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Attunement a = player().getData(ModAttachments.ATTUNEMENT);
        KnownSpells known = player().getData(ModAttachments.KNOWLEDGE);

        // the mage
        Ui.inset(g, x, y, DOLL_W, h);
        var dominant = a.dominant();
        dominant.ifPresent(s -> Ui.sprite(g, "glow", x + DOLL_W / 2 - 40, y + h / 2 - 50, 80, 80, 0x50000000 | (Schools.color(s) & 0xFFFFFF)));
        int dollBottom = y + h - 28;
        InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 4, y + 6, x + DOLL_W - 4, dollBottom, Math.min(48, (dollBottom - y) / 3),
                0.0625f, mouseX, mouseY, player());
        g.centeredText(font, ellipsize(player().getName().getString(), DOLL_W - 8), x + DOLL_W / 2, y + h - 24, Ui.TEXT);
        Component dom = dominant.map(s -> Component.translatable(Schools.nameKey(s)).withColor(Ui.opaque(Schools.color(s))))
                .orElse(Component.translatable("screen.orbisarcana.overview.no_school").withColor(Ui.TEXT_DIM));
        g.centeredText(font, dom, x + DOLL_W / 2, y + h - 13, -1);

        int rx = x + DOLL_W + 10, rw = w - DOLL_W - 10, cy = y;

        // totals
        int total = SpellRegistry.ids(player().registryAccess()).size();
        int learned = known.spells().size();
        int mastered = 0, levels = 0;
        for (Identifier id : known.spells().keySet()) {
            int level = known.progress(id).map(p -> p.level()).orElse(1);
            levels += level;
            if (level >= MasteryMath.MAX_LEVEL) mastered++;
        }
        int tw = (rw - 9) / 4;
        tile(g, rx, cy, tw, Component.translatable("screen.orbisarcana.overview.learned"), learned + " / " + total, Ui.TEXT);
        tile(g, rx + tw + 3, cy, tw, Component.translatable("screen.orbisarcana.overview.mastered"), Integer.toString(mastered), Ui.GOLD);
        tile(g, rx + 2 * (tw + 3), cy, tw, Component.translatable("screen.orbisarcana.overview.average"),
                learned == 0 ? "—" : String.format("%.1f", levels / (double) learned), Ui.TEXT);
        int res = dominant.map(s -> a.schools().get(s)).map(p -> p.resonance()).orElse(0);
        tile(g, rx + 3 * (tw + 3), cy, rw - 3 * (tw + 3), Component.translatable("screen.orbisarcana.overview.resonance"),
                dominant.isPresent() ? res + " / " + SchoolRules.MAX_RESONANCE : "—", dominant.map(s -> Ui.opaque(Schools.color(s))).orElse(Ui.TEXT_DIM));
        cy += 32;

        // resonance of every school, two columns
        caption(g, Component.translatable("screen.orbisarcana.overview.schools"), rx, cy, rw);
        cy += 12;
        List<Identifier> all = Schools.all();
        int colW = (rw - 8) / 2, rowH = 18;
        int rows = (all.size() + 1) / 2;
        for (int i = 0; i < all.size(); i++) {
            Identifier s = all.get(i);
            int sx = rx + (i / rows) * (colW + 8), sy = cy + (i % rows) * rowH;
            var p = a.schools().get(s);
            int r = p == null ? 0 : p.resonance();
            boolean isDom = dominant.filter(s::equals).isPresent();
            if (inside(mouseX, mouseY, sx, sy, colW, rowH - 2)) g.fill(sx, sy, sx + colW, sy + rowH - 2, 0x14FFFFFF);
            Ui.sigil(g, s, sx, sy, 16, r > 0 || isDom ? 0xFF : 0x60);
            String name = ellipsize(Component.translatable(Schools.nameKey(s)).getString(), colW - 60);
            g.text(font, name, sx + 19, sy + 4, isDom ? Ui.GOLD : r > 0 ? Ui.TEXT : Ui.TEXT_DIM, false);
            int bx = sx + colW - 38;
            g.fill(bx, sy + 6, bx + 30, sy + 9, 0xFF0B1018);
            g.fill(bx, sy + 6, bx + 30 * r / SchoolRules.MAX_RESONANCE, sy + 9, Ui.opaque(Schools.color(s)));
            g.text(font, Integer.toString(r), bx + 32, sy + 4, Ui.TEXT_DIM, false);
        }
        cy += rows * rowH + 4;

        // the current radial set
        if (cy + 34 <= y + h) {
            Loadout loadout = ClientCastState.loadout(player());
            caption(g, Component.translatable("screen.orbisarcana.overview.set", loadout.page() + 1, Loadout.PAGES), rx, cy, rw);
            cy += 12;
            for (int s = 0; s < Loadout.SLOTS; s++) {
                int sx = rx + s * 22;
                var in = loadout.get(loadout.page(), s);
                var def = in.flatMap(id -> SpellRegistry.get(player().registryAccess(), id));
                Ui.slot(g, sx, cy, 20, def.map(d -> Ui.opaque(d.rarity().color)).orElse(0));
                int sy = cy;
                def.ifPresent(d -> Ui.sigil(g, d.school(), sx + 2, sy + 2, 16, 0xFF));
                if (inside(mouseX, mouseY, sx, cy, 20, 20) && in.isPresent()) tooltip(g, List.of(SpellScrollItem.spellName(in.get())), mouseX, mouseY);
            }
        }
    }

    @Override
    boolean mouseClicked(double mx, double my, int button) {
        int rx = x + DOLL_W + 10, rw = w - DOLL_W - 10, cy = y + 44;
        List<Identifier> all = Schools.all();
        int colW = (rw - 8) / 2, rowH = 18, rows = (all.size() + 1) / 2;
        for (int i = 0; i < all.size(); i++) {
            if (inside(mx, my, rx + (i / rows) * (colW + 8), cy + (i % rows) * rowH, colW, rowH - 2)) {
                GrimoireScreen.selectedSchool = all.get(i);
                screen.open(GrimoireScreen.Tab.SCHOOLS);
                return true;
            }
        }
        return false;
    }
}
