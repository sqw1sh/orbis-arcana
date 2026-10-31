package com.orbisarcana.client.hud;

import com.orbisarcana.OrbisArcana;
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

/** Compact mana bar to the right of the hotbar (§13.2). Position becomes configurable with the client config. */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class ManaHud {
    private static final int WIDTH = 62;
    private static final int HEIGHT = 5;
    private static final int BG = 0xB0101018;
    private static final int BORDER = 0xFF2A2A40;
    private static final int FILL_TOP = 0xFF6FA8FF;
    private static final int FILL_BOTTOM = 0xFF3A5FD9;
    private static final int TEXT = 0xFFBFD6FF;

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, OrbisArcana.id("mana_bar"), ManaHud::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.isSpectator()) return;

        double max = ManaService.maxMana(player);
        if (max <= 0) return;
        double mana = ManaService.mana(player);

        int x = g.guiWidth() / 2 + 91 + 8;
        int y = g.guiHeight() - 9;
        int filled = (int) Math.round((WIDTH - 2) * Math.min(1.0, mana / max));

        g.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, BORDER);
        g.fill(x, y, x + WIDTH, y + HEIGHT, BG);
        if (filled > 0) {
            g.fillGradient(x + 1, y + 1, x + 1 + filled, y + HEIGHT - 1, FILL_TOP, FILL_BOTTOM);
        }
        String label = (int) Math.floor(mana) + " / " + (int) max;
        g.text(mc.font, label, x, y - 10, TEXT, true);
    }

    private ManaHud() {}
}
