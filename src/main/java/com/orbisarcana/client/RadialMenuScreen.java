package com.orbisarcana.client;

import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;
import com.orbisarcana.client.ui.Ui;
import com.orbisarcana.item.SpellScrollItem;
import com.orbisarcana.progression.Loadout;
import com.orbisarcana.school.Schools;
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
    private static final int SLOT_RADIUS = 66;
    private static final int DEAD_ZONE = 22;

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
        g.fill(0, 0, width, height, 0x60000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (minecraft.player == null) return;
        Loadout loadout = ClientCastState.loadout(minecraft.player);
        int cx = width / 2, cy = height / 2;
        hovered = sectorAt(mouseX - cx, mouseY - cy);
        long now = ClientCastState.gameTime();

        Ui.sprite(g, "radial_ring", cx - 96, cy - 96, 192, 192);
        for (int slot = 0; slot < Loadout.SLOTS; slot++) {
            double angle = Math.toRadians(slot * 360.0 / Loadout.SLOTS - 90);
            int sx = cx + (int) Math.round(Math.cos(angle) * SLOT_RADIUS), sy = cy + (int) Math.round(Math.sin(angle) * SLOT_RADIUS);
            Optional<Identifier> spell = loadout.get(page, slot);
            Optional<SpellDefinition> def = spell.flatMap(id -> SpellRegistry.get(minecraft.player.registryAccess(), id));
            boolean isHovered = slot == hovered && spell.isPresent();
            boolean isSelected = page == loadout.page() && slot == loadout.selected();

            if (isHovered) def.ifPresent(d -> Ui.sprite(g, "glow", sx - 24, sy - 24, 48, 48, 0x90000000 | (Schools.color(d.school()) & 0xFFFFFF)));
            Ui.sprite(g, "radial_slot", sx - 15, sy - 15, 30, 30);
            if (spell.isEmpty()) {
                Ui.sprite(g, "radial_slot_edge", sx - 15, sy - 15, 30, 30, (Ui.LINE & 0xFFFFFF) | 0x80000000);
                continue;
            }
            int edge = isSelected ? Ui.GOLD : isHovered ? Ui.TEAL : def.map(d -> Ui.opaque(d.rarity().color)).orElse(Ui.TEXT_DIM);
            Ui.sprite(g, "radial_slot_edge", sx - 15, sy - 15, 30, 30, edge);
            def.ifPresent(d -> Ui.sigil(g, d.school(), sx - 8, sy - 8, 16, 0xFF));

            var charges = ClientCastState.charges(minecraft.player, spell.get(), now);
            if (charges.nextChargeAt() > now) {
                float left = (charges.nextChargeAt() - now) / (float) Math.max(1, charges.cooldownTicks());
                g.fill(sx - 9, sy - 9, sx + 9, sy - 9 + Math.round(18 * Math.min(1, left)), charges.blocked(now) ? 0xB0000000 : 0x80000000);
            }
            if (isSelected) Ui.sprite(g, "dot", sx - 2, sy + 9, 5, 5, Ui.GOLD);
        }

        // centre: the hovered spell (name, rarity, cost, cooldown) or the set
        Ui.sprite(g, "radial_center", cx - 32, cy - 32, 64, 64);
        Optional<Identifier> centre = hovered >= 0 ? loadout.get(page, hovered) : Optional.empty();
        if (centre.isPresent()) {
            Optional<SpellDefinition> def = SpellRegistry.get(minecraft.player.registryAccess(), centre.get());
            int rarity = def.map(d -> Ui.opaque(d.rarity().color)).orElse(Ui.TEXT);
            var lines = font.split(SpellScrollItem.spellName(centre.get()), 58);
            int top = cy - 4 - (Math.min(2, lines.size()) * 9) / 2 - 6;
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.centeredText(font, lines.get(i), cx, top + i * 9, rarity);
            def.ifPresent(d -> {
                int y = cy + 8;
                String cost = Integer.toString((int) d.cost());
                String cd = d.cooldownTicks() > 0 ? String.format("%.0fs", d.cooldownTicks() / 20.0) : "";
                int w = 9 + 1 + font.width(cost) + (cd.isEmpty() ? 0 : 5 + 9 + 1 + font.width(cd));
                int x = cx - w / 2;
                Ui.icon(g, "mana", x, y - 1, Ui.MANA);
                g.text(font, cost, x + 10, y, Ui.TEXT, true);
                if (!cd.isEmpty()) {
                    int x2 = x + 10 + font.width(cost) + 5;
                    Ui.icon(g, "cooldown", x2, y - 1, Ui.TEXT_DIM);
                    g.text(font, cd, x2 + 10, y, Ui.TEXT_DIM, true);
                }
                g.centeredText(font, Component.translatable("rarity.orbisarcana." + d.rarity().id), cx, cy + 18, Ui.TEXT_DIM);
            });
        } else {
            // the set: its number and one dot per set
            g.centeredText(font, Component.translatable("screen.orbisarcana.radial.page", page + 1, Loadout.PAGES), cx, cy - 8, Ui.TEXT_DIM);
            for (int p = 0; p < Loadout.PAGES; p++) {
                Ui.sprite(g, p == page ? "dot" : "dot_empty", cx - (Loadout.PAGES * 10) / 2 + p * 10 + 1, cy + 5, 7, 7, p == page ? Ui.TEAL : Ui.TEXT_DIM);
            }
        }
        // the hint above the ring: below it the hotbar would cover it in small windows
        g.centeredText(font, Component.translatable("screen.orbisarcana.radial.hint"), cx, cy - 110, Ui.TEXT_DIM);
    }

    /** Sector index under the cursor: 0 at the top, clockwise; -1 inside the dead zone. */
    private static int sectorAt(int dx, int dy) {
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) return -1;
        double deg = Math.toDegrees(Math.atan2(dy, dx)) + 90;
        return Mth.positiveModulo((int) Math.round(deg / (360.0 / Loadout.SLOTS)), Loadout.SLOTS);
    }
}
