package com.orbisarcana.client.hud;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.ClientCastState;
import com.orbisarcana.core.CastDenial;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.progression.Loadout;
import com.orbisarcana.school.Schools;
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
    /** Fire Heat lives on targets and decays; hide the indicator after 8 s without changes. */
    private static final long ECHO_SHOW_TICKS = 160;

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

        // Line 4 (above): toggles that are on, in their school colour
        var toggles = ClientCastState.ownToggles();
        if (!toggles.isEmpty()) {
            MutableComponent on = Component.literal("● ");
            boolean first = true;
            for (Identifier t : toggles) {
                if (!first) on.append(", ");
                first = false;
                var def = SpellRegistry.get(mc.player.registryAccess(), t);
                on.append(Component.translatable("spell." + t.getNamespace() + "." + t.getPath())
                        .withColor(def.map(d -> Schools.color(d.school())).orElse(0xFFFFFF)));
            }
            g.text(mc.font, on, fit(g, mc, on, x), y - 30, TEXT_DIM, true);
        }

        // Line 3 (above): Echo stacks of the dominant school (§13.2); fire Heat fades after a while without hits
        var echo = ClientCastState.echo();
        var attunement = mc.player.getData(ModAttachments.ATTUNEMENT);
        var dominant = attunement.dominant();
        if (dominant.isPresent()) {
            var progress = attunement.schools().get(dominant.get());
            if (progress == null || progress.resonance() < 1) {
                Component locked = Component.translatable("hud.orbisarcana.echo_locked",
                        Component.translatable("talent.orbisarcana." + dominant.get().getPath() + ".echo"));
                g.text(mc.font, locked, fit(g, mc, locked, x), y - 20, TEXT_DIM, true);
                return;
            }
        }
        if (echo != null && dominant.filter(echo.school()::equals).isPresent() && (echo.stacks() > 0 || now - echo.tick() < 60)
                && now - echo.tick() < ECHO_SHOW_TICKS) {
            int filled = (int) Math.floor(echo.stacks());
            Component pips = Component.translatable("talent.orbisarcana." + echo.school().getPath() + ".echo")
                    .append(" " + "■".repeat(filled) + "□".repeat(Math.max(0, echo.max() - filled)));
            g.text(mc.font, pips, fit(g, mc, pips, x), y - 20, 0xFF000000 | Schools.color(echo.school()), true);
        }
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
