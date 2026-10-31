package com.orbisarcana.client.hud;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.ClientCastState;
import com.orbisarcana.core.CastDenial;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Selected spell + cooldown next to the mana bar, cast bar under the crosshair, denial reason (§13.2). */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class CastHud {
    private static final int BAR_WIDTH = 60;
    private static final int BAR_BG = 0xA0101018;
    private static final int BAR_FILL = 0xFFE0C060;
    private static final int TEXT_DIM = 0xFF9A9AAA;
    private static final int TEXT_DENIED = 0xFFFF6A5A;

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, OrbisArcana.id("cast_bar"), CastHud::renderCastBar);
        event.registerAbove(VanillaGuiLayers.HOTBAR, OrbisArcana.id("selected_spell"), CastHud::renderSelected);
    }

    private static void renderSelected(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return;
        int x = g.guiWidth() / 2 + 91 + 8;
        int y = g.guiHeight() - 30;
        var selected = ClientCastState.selected(mc.player);
        if (selected.isEmpty()) {
            g.text(mc.font, Component.translatable("hud.orbisarcana.no_spell"), x, y, TEXT_DIM, true);
            return;
        }
        Identifier spell = selected.get();
        int color = SpellRegistry.get(mc.player.registryAccess(), spell).map(d -> 0xFF000000 | d.rarity().color).orElse(0xFFFFFFFF);
        int page = ClientCastState.loadout(mc.player).page() + 1;
        Component name = Component.literal("[" + page + "] ").withColor(TEXT_DIM)
                .append(Component.translatable("spell." + spell.getNamespace() + "." + spell.getPath()).withColor(color));
        long now = ClientCastState.gameTime();
        var cd = ClientCastState.cooldown(spell, now);
        if (cd.isPresent()) {
            double secs = (cd.get().untilTick() - now) / 20.0;
            name = name.copy().append(Component.literal(String.format(" %.1fs", secs)).withColor(TEXT_DIM));
        }
        g.text(mc.font, name, x, y, color, true);
    }

    private static void renderCastBar(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        long now = ClientCastState.gameTime();

        var own = ClientCastState.ownCast();
        if (own != null && own.durationTicks() > 0) {
            float t = (now - own.startTick() + delta.getGameTimeDeltaPartialTick(true)) / own.durationTicks();
            int filled = (int) (BAR_WIDTH * Math.min(1f, Math.max(0f, t)));
            int x = cx - BAR_WIDTH / 2;
            int y = cy + 14;
            g.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + 4, BAR_BG);
            g.fill(x, y, x + filled, y + 3, BAR_FILL);
        }

        CastDenial denial = ClientCastState.lastDenial(now);
        if (denial != null) {
            g.centeredText(mc.font, Component.translatable("cast.orbisarcana.denied." + denial.id()), cx, cy + 22, TEXT_DENIED);
        }
    }

    private CastHud() {}
}
