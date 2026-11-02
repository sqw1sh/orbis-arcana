package com.orbisarcana.client.grimoire;

import java.util.List;
import java.util.Optional;

import com.orbisarcana.client.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/**
 * One tab of the grimoire: draws into the content area the screen gives it and handles its own clicks. Widgets
 * (search box, buttons) go through {@link GrimoireScreen#addTabWidget} and are rebuilt with the tab.
 */
abstract class GrimoireTab {
    protected final GrimoireScreen screen;
    protected final Minecraft mc = Minecraft.getInstance();
    protected final Font font = mc.font;
    /** Content area, set before {@link #init()}. */
    protected int x, y, w, h;

    GrimoireTab(GrimoireScreen screen) {
        this.screen = screen;
    }

    void layout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    protected LocalPlayer player() {
        return mc.player;
    }

    /** Adds widgets; called on every rebuild of the screen. */
    void init() {}

    abstract void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial);

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY, double dx, double dy) {
        return false;
    }

    void tick() {}

    // ---- shared drawing helpers ----

    protected static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Wrapped text, at most {@code maxLines}; returns the y below it. */
    protected int wrap(GuiGraphicsExtractor g, FormattedText text, int x, int y, int width, int color, int maxLines) {
        var lines = font.split(text, width);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            g.text(font, lines.get(i), x, y, color, false);
            y += 10;
        }
        return y;
    }

    protected String ellipsize(String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        while (s.length() > 1 && font.width(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** Small gold section caption with a thin rule after it. */
    protected void caption(GuiGraphicsExtractor g, Component text, int x, int y, int width) {
        g.text(font, text, x, y, Ui.GOLD, false);
        int tw = font.width(text) + 4;
        if (tw < width) g.fill(x + tw, y + 4, x + width, y + 5, Ui.LINE);
    }

    /** Stat tile: dim label over a value, in a recessed well. */
    protected void tile(GuiGraphicsExtractor g, int x, int y, int w, Component label, String value, int valueColor) {
        Ui.inset(g, x, y, w, 24);
        g.text(font, ellipsize(label.getString(), w - 6), x + 4, y + 3, Ui.TEXT_DIM, false);
        g.text(font, ellipsize(value, w - 6), x + 4, y + 13, valueColor, false);
    }

    protected void tooltip(GuiGraphicsExtractor g, List<Component> lines, int mouseX, int mouseY) {
        g.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY, Ui.TOOLTIP_STYLE);
    }
}
