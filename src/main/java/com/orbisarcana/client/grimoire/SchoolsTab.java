package com.orbisarcana.client.grimoire;

import java.util.List;
import java.util.Set;

import com.orbisarcana.client.ui.OrbisButton;
import com.orbisarcana.client.ui.Ui;
import com.orbisarcana.core.SchoolRules;
import com.orbisarcana.core.TalentRules;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.school.Attunement;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Schools tab: every school as a card (resonance, dominant, frozen) and the selected school in detail: resonance and
 * its progress, talent points, Echo tier, the Echo and the root passive; a button leads to its talent tree.
 */
class SchoolsTab extends GrimoireTab {
    private static final int GRID_W = 204, CARD_H = 28;

    SchoolsTab(GrimoireScreen screen) {
        super(screen);
    }

    static Identifier selected(Attunement a) {
        if (GrimoireScreen.selectedSchool == null || !Schools.exists(GrimoireScreen.selectedSchool)) {
            GrimoireScreen.selectedSchool = a.dominant().orElse(Schools.all().get(0));
        }
        return GrimoireScreen.selectedSchool;
    }

    @Override
    void init() {
        int rx = x + GRID_W + 10, rw = w - GRID_W - 10;
        screen.addTabWidget(new OrbisButton(rx + rw - 110, y + h - 18, 110, 18, Component.translatable("screen.orbisarcana.schools.open_talents"),
                Ui.ButtonStyle.PRIMARY, b -> screen.open(GrimoireScreen.Tab.TALENTS)));
    }

    private int cardW() {
        return (GRID_W - 4) / 2;
    }

    private int rowH() {
        int rows = (Schools.all().size() + 1) / 2;
        return Math.min(CARD_H + 3, Math.max(20, h / Math.max(1, rows)));
    }

    @Override
    void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Attunement a = player().getData(ModAttachments.ATTUNEMENT);
        Identifier selected = selected(a);
        List<Identifier> all = Schools.all();
        int cw = cardW(), rh = rowH();
        for (int i = 0; i < all.size(); i++) {
            Identifier s = all.get(i);
            int cx = x + (i % 2) * (cw + 4), cy = y + (i / 2) * rh;
            var p = a.schools().get(s);
            int resonance = p == null ? 0 : p.resonance();
            boolean dominant = a.dominant().filter(s::equals).isPresent();
            boolean hover = inside(mouseX, mouseY, cx, cy, cw, rh - 3);
            Ui.inset(g, cx, cy, cw, rh - 3);
            if (hover) g.fill(cx + 1, cy + 1, cx + cw - 1, cy + rh - 4, 0x14FFFFFF);
            if (s.equals(selected)) Ui.sprite(g, "frame", cx, cy, cw, rh - 3, Ui.TEAL);
            else if (dominant) Ui.sprite(g, "frame", cx, cy, cw, rh - 3, Ui.GOLD);
            int color = Ui.opaque(Schools.color(s));
            Ui.sigil(g, s, cx + 4, cy + (rh - 3 - 16) / 2, 16, resonance > 0 || dominant ? 0xFF : 0x70);
            String name = Component.translatable(Schools.nameKey(s)).getString();
            g.text(font, ellipsize(name, cw - 46), cx + 23, cy + 4, resonance > 0 || dominant ? color : Ui.TEXT_DIM, false);
            String r = resonance + "/" + SchoolRules.MAX_RESONANCE;
            g.text(font, r, cx + cw - 4 - font.width(r), cy + 4, dominant ? Ui.GOLD : Ui.TEXT_DIM, false);
            if (rh >= 26) {
                int bw = cw - 28;
                g.fill(cx + 23, cy + 15, cx + 23 + bw, cy + 18, 0xFF0B1018);
                g.fill(cx + 23, cy + 15, cx + 23 + bw * resonance / SchoolRules.MAX_RESONANCE, cy + 18, color);
            }
        }
        renderDetail(g, a, selected, x + GRID_W + 10, w - GRID_W - 10);
    }

    private void renderDetail(GuiGraphicsExtractor g, Attunement a, Identifier s, int rx, int rw) {
        g.fill(rx - 6, y, rx - 5, y + h, Ui.LINE);
        var p = a.schools().get(s);
        int resonance = p == null ? 0 : p.resonance();
        Set<String> picked = p == null ? Set.of() : p.talents();
        boolean dominant = a.dominant().filter(s::equals).isPresent();
        int color = Ui.opaque(Schools.color(s));
        int cy = y;

        Ui.slot(g, rx, cy, 30, color);
        Ui.sigil(g, s, rx + 7, cy + 7, 16, 0xFF);
        g.text(font, Component.translatable(Schools.nameKey(s)), rx + 36, cy + 3, color, true);
        Component state = dominant ? Component.translatable("screen.orbisarcana.schools.state.dominant").withColor(Ui.GOLD)
                : resonance > 0 ? Component.translatable("screen.orbisarcana.schools.state.frozen").withColor(Ui.TEXT_DIM)
                : Component.translatable("screen.orbisarcana.schools.state.none").withColor(Ui.TEXT_DIM);
        if (Schools.isHigher(s)) state = state.copy().append(Component.literal(" · ").withColor(Ui.TEXT_DIM))
                .append(Component.translatable("screen.orbisarcana.schools.higher").withColor(0xFFE0C8FF));
        g.text(font, state, rx + 36, cy + 16, -1, false);
        cy += 36;

        long known = player().getData(ModAttachments.KNOWLEDGE).spells().keySet().stream()
                .filter(id -> SpellRegistry.get(player().registryAccess(), id).filter(d -> d.school().equals(s)).isPresent()).count();
        int tw = (rw - 6) / 3;
        tile(g, rx, cy, tw, Component.translatable("screen.orbisarcana.schools.tile.resonance"), resonance + " / " + SchoolRules.MAX_RESONANCE, color);
        tile(g, rx + tw + 3, cy, tw, Component.translatable("screen.orbisarcana.schools.tile.points"), Integer.toString(TalentRules.pointsLeft(resonance, picked)), Ui.TEXT);
        tile(g, rx + 2 * (tw + 3), cy, rw - 2 * (tw + 3), Component.translatable("screen.orbisarcana.schools.tile.spells"), Long.toString(known), Ui.TEXT);
        cy += 30;

        int need = SchoolRules.resonanceXpRequired(resonance);
        double frac = resonance >= SchoolRules.MAX_RESONANCE || need <= 0 ? 1 : Math.min(1, (p == null ? 0 : p.xp()) / need);
        Ui.bar(g, rx, cy, rw, 7, frac, "bar_fill", color, true);
        cy += 13;

        int bottom = y + h - 24;
        String key = "talent.orbisarcana." + s.getPath();
        caption(g, Component.translatable("screen.orbisarcana.schools.echo_title").append(" " + Ui.roman(Math.max(1, SchoolRules.echoTier(dominant ? resonance : 0))) + ": ")
                .append(Component.translatable(key + ".echo")), rx, cy, rw);
        cy = wrap(g, Component.translatable(key + ".echo.desc"), rx, cy + 11, rw, dominant && resonance >= 1 ? Ui.TEXT : Ui.TEXT_DIM, Math.max(1, Math.min(3, (bottom - cy - 34) / 10))) + 4;
        if (cy + 20 < bottom) {
            caption(g, Component.translatable("screen.orbisarcana.schools.root_title").append(": ").append(Component.translatable(key + ".root")), rx, cy, rw);
            cy = wrap(g, Component.translatable(key + ".root.desc"), rx, cy + 11, rw, dominant && TalentRules.rootActive(resonance) ? Ui.TEXT : Ui.TEXT_DIM,
                    Math.max(1, Math.min(2, (bottom - cy - 11) / 10)));
        }
        // under the texts, above the button: why the talents do not work
        if (!dominant && cy + 14 < bottom) {
            wrap(g, Component.translatable("screen.orbisarcana.schools.not_dominant"), rx, cy + 4, rw, 0xFFB09060, (bottom - cy - 4) / 10);
        }
    }

    @Override
    boolean mouseClicked(double mx, double my, int button) {
        List<Identifier> all = Schools.all();
        int cw = cardW(), rh = rowH();
        for (int i = 0; i < all.size(); i++) {
            if (inside(mx, my, x + (i % 2) * (cw + 4), y + (i / 2) * rh, cw, rh - 3)) {
                GrimoireScreen.selectedSchool = all.get(i);
                return true;
            }
        }
        return false;
    }
}
