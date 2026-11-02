package com.orbisarcana.client.hud;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.ClientCastState;
import com.orbisarcana.client.ClientConfig;
import com.orbisarcana.client.ui.Ui;
import com.orbisarcana.core.CastDenial;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.progression.Loadout;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.SpellDefinition;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Spell HUD (§13.2): the selected spell card above the mana block (sigil in a rarity frame, cooldown shade, mastery,
 * charges, set), the Echo and active toggles above it; under the crosshair the cast bar in the school colour and the
 * reason a cast was denied.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class CastHud {
    private static final int CARD_H = 24, CAST_W = 84, CAST_H = 7;
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
        if (mc.player == null || mc.player.isSpectator() || ClientConfig.HUD_COMPACT.get()) return;
        Font font = mc.font;
        int x = ManaHud.left(g), w = ManaHud.WIDTH;
        int y = g.guiHeight() - ManaHud.ORB - 1 - CARD_H - 1;
        Ui.card(g, x, y, w, CARD_H);

        var selected = ClientCastState.selected(mc.player);
        if (selected.isEmpty()) {
            Ui.slot(g, x + 2, y + 2, 20, Ui.LINE);
            g.text(font, Component.translatable("hud.orbisarcana.no_spell"), x + 26, y + 8, Ui.TEXT_DIM, true);
            return;
        }
        Identifier spell = selected.get();
        var def = SpellRegistry.get(mc.player.registryAccess(), spell);
        int rarity = def.map(d -> Ui.opaque(d.rarity().color)).orElse(Ui.TEXT);
        long now = ClientCastState.gameTime();
        var charges = ClientCastState.charges(mc.player, spell, now);

        // icon: school sigil in a rarity frame, shaded from the top while the next charge is cooling down
        Ui.slot(g, x + 2, y + 2, 20, rarity);
        def.map(SpellDefinition::school).ifPresent(s -> Ui.sigil(g, s, x + 4, y + 4, 16, 0xFF));
        if (charges.nextChargeAt() > now) {
            float left = (charges.nextChargeAt() - now) / (float) Math.max(1, charges.cooldownTicks());
            g.fill(x + 3, y + 3, x + 21, y + 3 + Math.round(18 * Math.min(1, left)), charges.blocked(now) ? 0xC0000000 : 0x90000000);
        }

        // line 1: name in the rarity colour
        Component name = Component.translatable("spell." + spell.getNamespace() + "." + spell.getPath());
        g.text(font, ellipsize(font, name.getString(), w - 28 - Loadout.PAGES * 6), x + 26, y + 3, rarity, true);

        // line 2: mastery, charges, cooldown; set dots top right
        int mastery = mc.player.getData(ModAttachments.KNOWLEDGE).progress(spell).map(p -> p.level()).orElse(1);
        int lx = x + 26, ly = y + 13;
        String level = Ui.roman(mastery);
        g.text(font, level, lx, ly, Ui.GOLD, true);
        lx += font.width(level) + 4;
        if (charges.max() > 1) {
            for (int i = 0; i < charges.max(); i++) {
                boolean ready = i < charges.available();
                Ui.icon(g, ready ? "charge" : "charge_empty", lx, ly - 1, ready ? CHARGE_READY : Ui.TEXT_DIM);
                lx += 8;
            }
            lx += 2;
        }
        if (charges.nextChargeAt() > now) {
            String secs = String.format("%.1fs", (charges.nextChargeAt() - now) / 20.0);
            g.text(font, secs, lx, ly, charges.blocked(now) ? Ui.DANGER : Ui.TEXT_DIM, true);
        }
        var loadout = ClientCastState.loadout(mc.player);
        for (int p = 0; p < Loadout.PAGES; p++) {
            Ui.sprite(g, p == loadout.page() ? "dot" : "dot_empty", x + w - 3 - (Loadout.PAGES - p) * 6, y + 4, 5, 5,
                    p == loadout.page() ? Ui.TEAL : Ui.TEXT_DIM);
        }

        // above the card: Echo of the dominant school, then the toggles that are on
        int above = y - 10;
        var echo = ClientCastState.echo();
        var attunement = mc.player.getData(ModAttachments.ATTUNEMENT);
        var dominant = attunement.dominant();
        Component echoLine = null;
        int echoColor = Ui.TEXT_DIM;
        if (dominant.isPresent()) {
            var progress = attunement.schools().get(dominant.get());
            if (progress == null || progress.resonance() < 1) {
                echoLine = Component.translatable("hud.orbisarcana.echo_locked",
                        Component.translatable("talent.orbisarcana." + dominant.get().getPath() + ".echo"));
            } else if (echo != null && dominant.get().equals(echo.school()) && (echo.stacks() > 0 || now - echo.tick() < 60)
                    && now - echo.tick() < ECHO_SHOW_TICKS) {
                int filled = (int) Math.floor(echo.stacks());
                echoLine = Component.translatable("talent.orbisarcana." + echo.school().getPath() + ".echo")
                        .append(" " + "■".repeat(filled) + "□".repeat(Math.max(0, echo.max() - filled)));
                echoColor = Ui.opaque(Schools.color(echo.school()));
            }
        }
        if (echoLine != null) {
            g.text(font, echoLine, Math.min(x, g.guiWidth() - 2 - font.width(echoLine)), above, echoColor, true);
            above -= 10;
        }
        var toggles = ClientCastState.ownToggles();
        if (!toggles.isEmpty()) {
            MutableComponent on = Component.literal("● ");
            boolean first = true;
            for (Identifier t : toggles) {
                if (!first) on.append(", ");
                first = false;
                on.append(Component.translatable("spell." + t.getNamespace() + "." + t.getPath())
                        .withColor(SpellRegistry.get(mc.player.registryAccess(), t).map(d -> Schools.color(d.school())).orElse(0xFFFFFF)));
            }
            g.text(font, on, Math.min(x, g.guiWidth() - 2 - font.width(on)), above, Ui.TEXT_DIM, true);
        }
    }

    private static void renderCastBar(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        long now = ClientCastState.gameTime();

        var own = ClientCastState.ownCast();
        if (own != null && own.durationTicks() > 0) {
            float elapsed = now - own.startTick() + delta.getGameTimeDeltaPartialTick(true);
            float t = Math.min(1f, Math.max(0f, elapsed / own.durationTicks()));
            int color = SpellRegistry.get(mc.player.registryAccess(), own.spell()).map(d -> Ui.opaque(Schools.color(d.school()))).orElse(Ui.GOLD);
            int x = cx - CAST_W / 2, y = cy + 22;
            Component name = Component.translatable("spell." + own.spell().getNamespace() + "." + own.spell().getPath());
            g.centeredText(font, name, cx, y - 10, color);
            Ui.bar(g, x, y, CAST_W, CAST_H, t, "bar_fill", color, true);
            String left = String.format("%.1fs", Math.max(0, own.durationTicks() - elapsed) / 20.0);
            g.text(font, left, x + CAST_W + 4, y, Ui.TEXT_DIM, true);
        }

        CastDenial denial = ClientCastState.lastDenial(now);
        if (denial != null) {
            Component text = Component.translatable("cast.orbisarcana.denied." + denial.id());
            int w = font.width(text) + 9 + 12, x = cx - w / 2, y = cy + 34;
            Ui.sprite(g, "chip", x, y, w, 15);
            Ui.icon(g, icon(denial), x + 4, y + 3, Ui.DANGER);
            g.text(font, text, x + 16, y + 4, 0xFFFFD6D6, true);
        }
    }

    private static String icon(CastDenial denial) {
        return switch (denial) {
            case NOT_ENOUGH_MANA -> "mana";
            case COOLDOWN, GLOBAL_COOLDOWN -> "cooldown";
            case NO_TARGET -> "target";
            case OUT_OF_RANGE -> "range";
            case ALREADY_CASTING -> "busy";
            default -> "lock";
        };
    }

    private static String ellipsize(Font font, String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        while (s.length() > 1 && font.width(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private CastHud() {}
}
