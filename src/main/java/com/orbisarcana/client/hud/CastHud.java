package com.orbisarcana.client.hud;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.ClientCastState;
import com.orbisarcana.core.CastDenial;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.progression.Loadout;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
    private static final int CHARGE_READY = 0xFF8FD8FF;

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
        int mastery = mc.player.getData(ModAttachments.KNOWLEDGE).progress(spell).map(p -> p.level()).orElse(1);

        // Line 1: spell name, mastery level, charges and the time until the next charge
        MutableComponent line = Component.translatable("spell." + spell.getNamespace() + "." + spell.getPath()).withColor(color)
                .append(Component.translatable("hud.orbisarcana.mastery", mastery).withColor(TEXT_DIM));
        long now = ClientCastState.gameTime();
        var charges = ClientCastState.charges(mc.player, spell, now);
        if (charges.max() > 1) {
            line.append(Component.literal(" " + "◆".repeat(charges.available()) + "◇".repeat(charges.max() - charges.available()))
                    .withColor(charges.available() > 0 ? CHARGE_READY : TEXT_DIM));
        }
        if (charges.nextChargeAt() > now) {
            double secs = (charges.nextChargeAt() - now) / 20.0;
            line.append(Component.literal(String.format(" %.1fs", secs)).withColor(charges.blocked(now) ? TEXT_DENIED : TEXT_DIM));
        }
        g.text(mc.font, line, fit(g, mc, line, x), y, color, true);

        // Line 2 (above): radial-menu set, so the number is not mistaken for mastery
        var loadout = ClientCastState.loadout(mc.player);
        Component set = Component.translatable("hud.orbisarcana.set", loadout.page() + 1, Loadout.PAGES);
        g.text(mc.font, set, fit(g, mc, set, x), y - 10, TEXT_DIM, true);
    }

    /** Shifts text left so it does not run off the right edge on narrow windows. */
    private static int fit(GuiGraphicsExtractor g, Minecraft mc, Component text, int x) {
        return Math.min(x, g.guiWidth() - 2 - mc.font.width(text));
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
