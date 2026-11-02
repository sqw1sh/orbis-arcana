package com.orbisarcana.client.ui;

import java.util.Set;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.school.Schools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The UI kit (docs/ART.md §2): colour tokens and drawing helpers over the sprites from {@code tools/gui.cjs}
 * ({@code orbisarcana:ui/<name>}). Panels, buttons, tabs, wells and frames are nine-slice, so any size works.
 */
public final class Ui {
    public static final int VOID = 0xFF07090F, SLATE = 0xFF0E141E, SURFACE = 0xFF16202C, LINE = 0xFF2A3646;
    public static final int GOLD = 0xFFD9B26A, GOLD_DARK = 0xFF8A6A35, TEAL = 0xFF5FE3C8, MANA = 0xFF58A8FF;
    public static final int TEXT = 0xFFE6EDF5, TEXT_DIM = 0xFF8C9AAD, DANGER = 0xFFFF6B6B;
    /** Tooltip style of the mod's items and screens (sprites tooltip/arcane_background, tooltip/arcane_frame). */
    public static final Identifier TOOLTIP_STYLE = OrbisArcana.id("arcane");

    private static final Set<String> SIGILS = Set.of("fire", "earth", "water", "air", "ice", "lightning", "nature", "light", "dark", "astral");

    public enum ButtonStyle { NORMAL, PRIMARY }

    public static Identifier sprite(String name) {
        return OrbisArcana.id("ui/" + name);
    }

    public static void sprite(GuiGraphicsExtractor g, String name, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(name), x, y, w, h);
    }

    /** A white sprite tinted with {@code argb}. */
    public static void sprite(GuiGraphicsExtractor g, String name, int x, int y, int w, int h, int argb) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(name), x, y, w, h, argb);
    }

    /** Main window panel: gold frame, slate body. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        sprite(g, "panel", x, y, w, h);
    }

    /** Translucent HUD card. */
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        sprite(g, "card", x, y, w, h);
    }

    /** Recessed well for lists and fields. */
    public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        sprite(g, "inset", x, y, w, h);
    }

    /** Dark slot behind an icon, with a frame in {@code frameArgb} (rarity colour), or none if 0. */
    public static void slot(GuiGraphicsExtractor g, int x, int y, int size, int frameArgb) {
        sprite(g, "slot", x, y, size, size);
        if (frameArgb != 0) sprite(g, "frame", x, y, size, size, frameArgb);
    }

    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, ButtonStyle style, boolean hovered, boolean active) {
        String name = !active ? "button_disabled"
                : style == ButtonStyle.PRIMARY ? (hovered ? "button_primary_hover" : "button_primary")
                : hovered ? "button_hover" : "button";
        sprite(g, name, x, y, w, h);
    }

    public static void tab(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean selected, boolean hovered) {
        sprite(g, selected ? "tab_selected" : hovered ? "tab_hover" : "tab", x, y, w, h);
    }

    /** Framed bar filled to {@code fraction}; {@code fill} is bar_fill_mana, bar_fill_danger or bar_fill (tinted). */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, double fraction, String fill, int tint, boolean spark) {
        sprite(g, "bar_frame", x, y, w, h);
        int inner = w - 4, filled = (int) Math.round(inner * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) sprite(g, fill, x + 2, y + 2, filled, h - 4, tint);
        if (spark && filled > 0 && filled < inner) sprite(g, "bar_spark", x + 2 + filled - 1, y, 3, h);
    }

    /** 9x9 hint icon: mana, cooldown, target, range, lock, busy, charge, charge_empty. */
    public static void icon(GuiGraphicsExtractor g, String name, int x, int y, int argb) {
        sprite(g, "icon/" + name, x, y, 9, 9, argb);
    }

    /** The school's sigil in its colour; addon schools without a sigil get a star. */
    public static void sigil(GuiGraphicsExtractor g, Identifier school, int x, int y, int size, int alpha) {
        String name = school.getNamespace().equals(OrbisArcana.MODID) && SIGILS.contains(school.getPath()) ? school.getPath() : "astral";
        sprite(g, "school/" + name, x, y, size, size, (alpha << 24) | (Schools.color(school) & 0xFFFFFF));
    }

    /** Mastery levels as Roman numerals (I-XV), as on the reference cards. */
    public static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV"};
        return n >= 0 && n < r.length ? r[n] : Integer.toString(n);
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    private Ui() {}
}
