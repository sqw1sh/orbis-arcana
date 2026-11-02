package com.orbisarcana.client.hud;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.ui.Ui;
import com.orbisarcana.mana.ManaService;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Mana to the right of the hotbar (§13.2): a framed bar across the whole HUD block with the numbers above it; below 20%
 * it turns red. The selected spell card ({@link CastHud}) sits on top of this block.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class ManaHud {
    /** Height of the mana block (numbers + bar). */
    static final int HEIGHT = 20;
    private static final int BAR_H = 8, MIN_W = 110, MAX_W = 150;
    private static final double LOW = 0.2;

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, OrbisArcana.id("mana_bar"), ManaHud::render);
    }

    /** Width of the HUD block: what fits right of the hotbar, between 110 and 150. */
    static int width(GuiGraphicsExtractor g) {
        return Math.max(MIN_W, Math.min(MAX_W, g.guiWidth() - (g.guiWidth() / 2 + 91 + 6) - 2));
    }

    /** Left edge of the HUD block: next to the hotbar, pulled left on narrow windows so it stays on screen. */
    static int left(GuiGraphicsExtractor g) {
        return Math.min(g.guiWidth() / 2 + 91 + 6, g.guiWidth() - 2 - width(g));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.isSpectator()) return;
        double max = ManaService.maxMana(player);
        if (max <= 0) return;
        double mana = ManaService.mana(player), share = Math.min(1, mana / max);
        boolean low = share < LOW;

        int x = left(g), w = width(g), by = g.guiHeight() - BAR_H - 1;
        Ui.icon(g, "mana", x + 1, by - 10, low ? Ui.DANGER : Ui.MANA);
        String value = (int) Math.floor(mana) + " / " + (int) max;
        g.text(mc.font, value, x + w - mc.font.width(value), by - 9, low ? Ui.DANGER : Ui.TEXT, true);
        Ui.bar(g, x, by, w, BAR_H, share, low ? "bar_fill_danger" : "bar_fill_mana", -1, true);
    }

    private ManaHud() {}
}
