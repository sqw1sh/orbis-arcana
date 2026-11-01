package com.orbisarcana.client;

import java.util.Optional;

import com.orbisarcana.core.SchoolRules;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.network.CastPayloads.AltarAction;
import com.orbisarcana.school.AltarService;
import com.orbisarcana.school.Attunement;
import com.orbisarcana.school.AttunementService;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Resonance Altar screen (§10.6, §13.4): the ten schools with resonance, state and the price of binding.
 * Everything shown is recomputed from synced data; the server re-checks every action.
 */
public class AltarScreen extends Screen {
    private static final int CELL_H = 24, PANEL = 0xC0101018, TEXT = 0xFFE8E8F0, DIM = 0xFF8A8A9A, BAD = 0xFFE07070;

    private final BlockPos altar;

    public AltarScreen(BlockPos altar) {
        super(Component.translatable("screen.orbisarcana.altar"));
        this.altar = altar;
    }

    @Override
    protected void init() {
        if (minecraft.player == null) return;
        Attunement a = minecraft.player.getData(ModAttachments.ATTUNEMENT);
        int cellW = Math.min(215, (width - 30) / 2);
        int left = (width - cellW * 2 - 10) / 2;
        int top = 34;
        for (int i = 0; i < Schools.ALL.size(); i++) {
            Identifier school = Schools.ALL.get(i);
            int x = left + (i % 2) * (cellW + 10);
            int y = top + (i / 2) * CELL_H;
            boolean dominant = a.dominant().filter(school::equals).isPresent();
            Button bind = Button.builder(Component.translatable(a.everBound() ? "screen.orbisarcana.altar.switch" : "screen.orbisarcana.altar.bind"), b -> {
                ClientPacketDistributor.sendToServer(new AltarAction(altar, AltarService.Action.BIND.ordinal(), Optional.of(school)));
                onClose();
            }).bounds(x + cellW - 52, y + 2, 50, 16).build();
            bind.active = !dominant && knowsSpellOf(school) && switchReady(a);
            addRenderableWidget(bind);
        }
        if (a.dominant().isPresent()) {
            Identifier dominant = a.dominant().get();
            addRenderableWidget(Button.builder(Component.translatable("screen.orbisarcana.altar.unbind"), b -> {
                ClientPacketDistributor.sendToServer(new AltarAction(altar, AltarService.Action.UNBIND.ordinal(), Optional.empty()));
                onClose();
            }).bounds(width / 2 - 104, top + 5 * CELL_H + 6, 100, 18).build());
            Button reset = Button.builder(Component.translatable("screen.orbisarcana.altar.reset")
                    .append(" (" + com.orbisarcana.core.TalentRules.RESET_INK + ")"), b -> {
                ClientPacketDistributor.sendToServer(new AltarAction(altar, AltarService.Action.RESET_TALENTS.ordinal(), Optional.of(dominant)));
                onClose();
            }).bounds(width / 2 + 4, top + 5 * CELL_H + 6, 100, 18).build();
            reset.active = a.schools().containsKey(dominant) && !a.schools().get(dominant).talents().isEmpty();
            addRenderableWidget(reset);
        }
    }

    private boolean knowsSpellOf(Identifier school) {
        var registries = minecraft.player.registryAccess();
        return minecraft.player.getData(ModAttachments.KNOWLEDGE).spells().keySet().stream()
                .anyMatch(id -> SpellRegistry.get(registries, id).filter(d -> d.school().equals(school)).isPresent());
    }

    private boolean switchReady(Attunement a) {
        return !a.everBound() || SchoolRules.canSwitchAt(ClientCastState.gameTime(), a.nextSwitchAt());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (minecraft.player == null) return;
        Attunement a = minecraft.player.getData(ModAttachments.ATTUNEMENT);
        int cellW = Math.min(215, (width - 30) / 2);
        int left = (width - cellW * 2 - 10) / 2;
        int top = 34;
        g.fill(left - 6, 6, left + cellW * 2 + 16, top + 5 * CELL_H + 30, PANEL);

        // Header: dominant, Dissonance, switch pause
        long now = ClientCastState.gameTime();
        Component dominant = a.dominant().map(AttunementService::schoolName).orElse(Component.translatable("screen.orbisarcana.altar.neutral"));
        g.centeredText(font, Component.translatable("screen.orbisarcana.altar.title", dominant), width / 2, 10, TEXT);
        String status = "";
        if (now < a.dissonanceUntil()) status += Component.translatable("screen.orbisarcana.altar.dissonance", minutes(a.dissonanceUntil() - now)).getString() + "   ";
        if (a.everBound() && now < a.nextSwitchAt()) status += Component.translatable("screen.orbisarcana.altar.pause", minutes(a.nextSwitchAt() - now)).getString();
        if (!status.isEmpty()) g.centeredText(font, Component.literal(status.trim()), width / 2, 21, BAD);

        super.extractRenderState(g, mouseX, mouseY, partial);

        for (int i = 0; i < Schools.ALL.size(); i++) {
            Identifier school = Schools.ALL.get(i);
            int x = left + (i % 2) * (cellW + 10);
            int y = top + (i / 2) * CELL_H;
            Attunement.SchoolProgress p = a.schools().get(school);
            int resonance = p == null ? 0 : p.resonance();
            boolean isDominant = a.dominant().filter(school::equals).isPresent();
            Component name = AttunementService.schoolName(school).copy().append(isDominant ? " ◆" : "");
            g.text(font, name, x, y + 2, TEXT, true);
            Component resonanceText = Component.translatable("screen.orbisarcana.altar.resonance", resonance, SchoolRules.MAX_RESONANCE);
            g.text(font, resonanceText, x, y + 12, DIM, false);

            // Line 1 right: price or requirement; moves to line 2 (instead of the bar) when it does not fit
            Component note = null;
            int noteColor = DIM;
            if (!isDominant) {
                if (!knowsSpellOf(school)) {
                    note = Component.translatable("screen.orbisarcana.altar.need_spell");
                    noteColor = BAD;
                } else {
                    SchoolRules.SwitchCost cost = AttunementService.costFor(a, school);
                    note = cost.equals(SchoolRules.SwitchCost.FREE)
                            ? Component.translatable("screen.orbisarcana.altar.free")
                            : Component.translatable("screen.orbisarcana.altar.cost", cost.ink(), cost.xpLevels());
                }
            }
            int rightEdge = x + cellW - 56;
            boolean noteOnLine1 = note != null && x + font.width(name) + 6 + font.width(note) <= rightEdge;
            if (noteOnLine1) g.text(font, note, rightEdge - font.width(note), y + 2, noteColor, false);

            int line2X = x + font.width(resonanceText) + 6;
            if (note != null && !noteOnLine1) {
                // Still too wide: fall back to the short form ("4 · 5") so it never runs under the button
                if (line2X + font.width(note) > rightEdge && !knowsSpellOf(school)) note = Component.literal("—");
                else if (line2X + font.width(note) > rightEdge) {
                    var cost = AttunementService.costFor(a, school);
                    note = Component.translatable("screen.orbisarcana.altar.cost_short", cost.ink(), cost.xpLevels());
                }
                g.text(font, note, Math.max(line2X, rightEdge - font.width(note)), y + 12, noteColor, false);
            } else if (rightEdge - line2X > 10) {
                int need = SchoolRules.resonanceXpRequired(resonance);
                float frac = need <= 0 ? 1f : (float) Math.min(1.0, (p == null ? 0 : p.xp()) / need);
                g.fill(line2X, y + 15, rightEdge, y + 18, 0xFF2A2A40);
                g.fill(line2X, y + 15, line2X + (int) ((rightEdge - line2X) * frac), y + 18, 0xFF000000 | Schools.color(school));
            }
        }
    }

    private static String minutes(long ticks) {
        long s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
