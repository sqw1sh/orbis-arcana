package com.orbisarcana.client;

import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;
import com.orbisarcana.item.SpellScrollItem;
import com.orbisarcana.progression.Loadout;
import com.orbisarcana.spell.SpellDefinition;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Radial menu (§13.3): hold R, point at a sector, let go to select. 8 sectors x 3 sets; the wheel or
 * 1/2/3 switch sets. Empty sectors cannot be picked. The game keeps running while it is open.
 */
public class RadialMenuScreen extends Screen {
    private static final int RING_RADIUS = 78;
    private static final int CARD_W = 74, CARD_H = 24;
    private static final int DEAD_ZONE = 22;
    private static final int BG = 0xC0101018, BG_HOVER = 0xE0283050, BG_EMPTY = 0x80101018;
    private static final int TEXT = 0xFFE8E8F0, TEXT_DIM = 0xFF8A8A9A, SELECTED = 0xFFFFD866;

    /** Preview (dev screenshots): stays open without R held. */
    private final boolean preview;
    private int page;
    private int hovered = -1;

    public RadialMenuScreen() {
        this(false);
    }

    RadialMenuScreen(boolean preview) {
        super(Component.translatable("screen.orbisarcana.radial"));
        this.preview = preview;
    }

    @Override
    protected void init() {
        page = minecraft.player == null ? 0 : ClientCastState.loadout(minecraft.player).page();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        // Releasing R confirms, even if the key-up event went to another window
        if (!preview && !InputConstants.isKeyDown(CastInput.RADIAL.getKey().getValue())) confirm();
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (CastInput.RADIAL.matches(event)) {
            confirm();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() >= InputConstants.KEY_1 && event.key() < InputConstants.KEY_1 + Loadout.PAGES) {
            page = event.key() - InputConstants.KEY_1;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        confirm();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) page = Math.floorMod(page + (scrollY > 0 ? -1 : 1), Loadout.PAGES);
        return true;
    }

    private void confirm() {
        if (minecraft.player != null && hovered >= 0 && ClientCastState.loadout(minecraft.player).get(page, hovered).isPresent()) {
            ClientCastState.select(minecraft.player, page, hovered);
        }
        onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0x40000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (minecraft.player == null) return;
        Loadout loadout = ClientCastState.loadout(minecraft.player);
        int cx = width / 2, cy = height / 2;
        hovered = sectorAt(mouseX - cx, mouseY - cy);
        long now = ClientCastState.gameTime();

        for (int slot = 0; slot < Loadout.SLOTS; slot++) {
            double angle = Math.toRadians(slot * 360.0 / Loadout.SLOTS - 90);
            int x = cx + (int) (Math.cos(angle) * RING_RADIUS) - CARD_W / 2;
            int y = cy + (int) (Math.sin(angle) * RING_RADIUS) - CARD_H / 2;
            Optional<Identifier> spell = loadout.get(page, slot);
            boolean isHovered = slot == hovered && spell.isPresent();
            boolean isSelected = page == loadout.page() && slot == loadout.selected();

            if (spell.isEmpty()) {
                g.fill(x, y, x + CARD_W, y + CARD_H, BG_EMPTY);
                g.centeredText(font, Component.literal("·"), x + CARD_W / 2, y + 8, TEXT_DIM);
                continue;
            }
            Optional<SpellDefinition> def = SpellRegistry.get(minecraft.player.registryAccess(), spell.get());
            int rarityColor = def.map(d -> 0xFF000000 | d.rarity().color).orElse(TEXT_DIM);
            g.fill(x, y, x + CARD_W, y + CARD_H, isHovered ? BG_HOVER : BG);
            outline(g, x, y, CARD_W, CARD_H, isSelected ? SELECTED : rarityColor);
            g.centeredText(font, ellipsize(SpellScrollItem.spellName(spell.get()).getString(), CARD_W - 6), x + CARD_W / 2, y + 4, TEXT);

            var cd = ClientCastState.cooldown(spell.get(), now);
            if (cd.isPresent()) {
                float left = (cd.get().untilTick() - now) / (float) Math.max(1, cd.get().totalTicks());
                g.fill(x + 1, y + CARD_H - 4, x + 1 + (int) ((CARD_W - 2) * left), y + CARD_H - 2, 0xC0A0A0B0);
            }
            def.ifPresent(d -> g.centeredText(font, Component.literal((int) d.cost() + " ✦"), x + CARD_W / 2, y + 13, TEXT_DIM));
        }

        // Centre: hovered spell or the set number
        Optional<Identifier> centre = hovered >= 0 ? loadout.get(page, hovered) : Optional.empty();
        g.centeredText(font, Component.translatable("screen.orbisarcana.radial.page", page + 1, Loadout.PAGES), cx, cy - 4, TEXT_DIM);
        centre.ifPresent(id -> g.centeredText(font, SpellScrollItem.spellName(id), cx, cy + 8, TEXT));
        g.centeredText(font, Component.translatable("screen.orbisarcana.radial.hint"), cx, cy + RING_RADIUS + CARD_H, TEXT_DIM);
    }

    /** Sector index under the cursor: 0 at the top, clockwise; -1 inside the dead zone. */
    private static int sectorAt(int dx, int dy) {
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) return -1;
        double deg = Math.toDegrees(Math.atan2(dy, dx)) + 90;
        return Mth.positiveModulo((int) Math.round(deg / (360.0 / Loadout.SLOTS)), Loadout.SLOTS);
    }

    private String ellipsize(String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        while (s.length() > 1 && font.width(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
