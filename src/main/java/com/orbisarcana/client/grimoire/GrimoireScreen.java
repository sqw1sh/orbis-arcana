package com.orbisarcana.client.grimoire;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.client.ui.Ui;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The grimoire (§13.4), opened with G: one window with the tabs Overview, Spells, Schools, Talents and Atlas
 * (docs/ROADMAP.md V2). The last tab, spell and school are remembered while the game runs.
 */
public class GrimoireScreen extends Screen {
    public enum Tab {
        OVERVIEW("overview", OverviewTab::new),
        SPELLS("spells", SpellsTab::new),
        SCHOOLS("schools", SchoolsTab::new),
        TALENTS("talents", TalentsTab::new),
        ATLAS("atlas", AtlasTab::new);

        final String id;
        final Function<GrimoireScreen, GrimoireTab> factory;

        Tab(String id, Function<GrimoireScreen, GrimoireTab> factory) {
            this.id = id;
            this.factory = factory;
        }

        Component title() {
            return Component.translatable("screen.orbisarcana.tab." + id);
        }
    }

    private static final int MAX_W = 460, MAX_H = 280, HEADER = 24;

    /** Shared between tabs and kept between openings. */
    static Tab lastTab = Tab.OVERVIEW;
    static @Nullable Identifier selectedSpell;
    static @Nullable Identifier selectedSchool;

    private final List<AbstractWidget> tabWidgets = new ArrayList<>();
    private GrimoireTab tab;
    private int left, top, winW, winH;

    public GrimoireScreen() {
        this(lastTab);
    }

    public GrimoireScreen(Tab tab) {
        super(Component.translatable("screen.orbisarcana.grimoire"));
        lastTab = tab;
        this.tab = tab.factory.apply(this);
    }

    /** Switches tab (and remembers it). */
    void open(Tab next) {
        if (next != lastTab) com.orbisarcana.client.sound.CastSounds.ui(com.orbisarcana.registry.ModSounds.UI_TAB.get(), 1f);
        lastTab = next;
        tab = next.factory.apply(this);
        rebuildWidgets();
    }

    <T extends AbstractWidget> T addTabWidget(T widget) {
        tabWidgets.add(widget);
        return addRenderableWidget(widget);
    }

    @Override
    protected void init() {
        if (minecraft.player == null) return;
        tabWidgets.clear();
        winW = Math.min(width - 16, MAX_W);
        winH = Math.min(height - 16, MAX_H);
        left = (width - winW) / 2;
        top = (height - winH) / 2;
        tab.layout(left + 10, top + HEADER + 8, winW - 20, winH - HEADER - 16);
        tab.init();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        tab.tick();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xA0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (minecraft.player == null) return;
        Ui.panel(g, left, top, winW, winH);

        // header: title and progress on the left, tabs on the right, a gold rule under them
        int known = minecraft.player.getData(ModAttachments.KNOWLEDGE).spells().size();
        int total = SpellRegistry.ids(minecraft.player.registryAccess()).size();
        int tabsW = 0;
        for (Tab t : Tab.values()) tabsW += font.width(t.title()) + 16;
        g.text(font, title, left + 10, top + 9, Ui.GOLD, true);
        // the counter goes in full, short, or not at all, whatever fits before the tabs
        int room = winW - 8 - tabsW - (14 + font.width(title)) - 6;
        Component counter = Component.translatable("screen.orbisarcana.grimoire.known", known, total);
        if (font.width(counter) > room) counter = Component.literal(known + "/" + total);
        if (font.width(counter) <= room) g.text(font, counter, left + 14 + font.width(title), top + 9, Ui.TEXT_DIM, false);
        int tx = left + winW - 8;
        for (int i = Tab.values().length - 1; i >= 0; i--) {
            Tab t = Tab.values()[i];
            int tw = font.width(t.title()) + 14;
            tx -= tw;
            boolean selected = t == lastTab, hover = inside(mouseX, mouseY, tx, top + 6, tw, HEADER - 4);
            Ui.tab(g, tx, top + 6, tw, HEADER - 4, selected, hover);
            g.centeredText(font, t.title(), tx + tw / 2, top + 12, selected ? Ui.GOLD : hover ? Ui.TEXT : Ui.TEXT_DIM);
            tx -= 2;
        }
        g.fill(left + 6, top + HEADER + 2, left + winW - 6, top + HEADER + 3, Ui.GOLD_DARK);

        tab.render(g, mouseX, mouseY, partial);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int tx = left + winW - 8;
        for (int i = Tab.values().length - 1; i >= 0; i--) {
            Tab t = Tab.values()[i];
            int tw = font.width(t.title()) + 14;
            tx -= tw;
            if (inside(event.x(), event.y(), tx, top + 6, tw, HEADER - 4)) {
                if (t != lastTab) open(t);
                return true;
            }
            tx -= 2;
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        return tab.mouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return tab.mouseDragged(event.x(), event.y(), dx, dy) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        return tab.mouseScrolled(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
