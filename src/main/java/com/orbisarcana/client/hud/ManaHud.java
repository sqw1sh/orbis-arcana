package com.orbisarcana.client.hud;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.ui.Ui;
import com.orbisarcana.mana.ManaService;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Mana to the right of the hotbar (§13.2): a gold-rimmed orb whose liquid level is the mana share, and a framed bar
 * with the numbers. Below 20% both turn red. The selected spell card ({@link CastHud}) sits on top of this block.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class ManaHud {
    static final int ORB = 28, BAR_W = 74, BAR_H = 8;
    /** Width of the whole block (orb + bar), shared with the spell card above it. */
    static final int WIDTH = ORB - 2 + BAR_W;
    private static final double LOW = 0.2;

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, OrbisArcana.id("mana_bar"), ManaHud::render);
    }

    /** Left edge of the HUD block: next to the hotbar, pulled left on narrow windows so it stays on screen. */
    static int left(GuiGraphicsExtractor g) {
        return Math.min(g.guiWidth() / 2 + 91 + 6, g.guiWidth() - 2 - WIDTH);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.isSpectator()) return;
        double max = ManaService.maxMana(player);
        if (max <= 0) return;
        double mana = ManaService.mana(player), share = Math.min(1, mana / max);
        boolean low = share < LOW;

        int x = left(g), y = g.guiHeight() - ORB - 1;
        // bar first, so the orb rim overlaps its left end
        int bx = x + ORB - 4, by = y + ORB - BAR_H - 4;
        Ui.bar(g, bx, by, BAR_W, BAR_H, share, low ? "bar_fill_danger" : "bar_fill_mana", -1, true);
        String value = (int) Math.floor(mana) + " / " + (int) max;
        g.text(mc.font, value, bx + BAR_W - mc.font.width(value), by - 10, low ? Ui.DANGER : Ui.TEXT, true);

        Ui.sprite(g, "orb_frame", x, y, ORB, ORB);
        // the liquid: crop the 20x20 sphere from the top down to the level
        int fill = (int) Math.round(20 * share);
        if (fill > 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Ui.sprite(low ? "orb_fill_danger" : "orb_fill"), 20, 20, 0, 20 - fill,
                    x + 4, y + 4 + 20 - fill, 20, fill);
        }
    }

    private ManaHud() {}
}
