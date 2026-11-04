package com.orbisarcana.client;

import java.util.Optional;

import com.orbisarcana.client.ui.OrbisButton;
import com.orbisarcana.client.ui.Ui;
import com.orbisarcana.core.SchoolRules;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.network.CastPayloads.AltarAction;
import com.orbisarcana.school.AltarService;
import com.orbisarcana.school.Attunement;
import com.orbisarcana.school.AttunementService;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Resonance Altar screen (§10.6, §13.4) in the grimoire's style: a gold-framed window, a card per school with its
 * sigil, resonance bar, price and bind button, unbinding and the talent reset at the bottom. Everything shown is
 * recomputed from synced data; the server re-checks every action.
 */
public class AltarScreen extends Screen {
    private static final int MAX_W = 440, HEADER = 24, GAP = 3, FOOTER = 26;

    private final BlockPos altar;
    /** Card height: 30 when the screen has room, down to 24 on small screens (two lines of text still fit). */
    private int cellH = 30;
    private int left, top, winW, winH, cellW, buttonW;

    public AltarScreen(BlockPos altar) {
        super(Component.translatable("screen.orbisarcana.altar"));
        this.altar = altar;
    }

    private int rows() {
        return (Schools.all().size() + 1) / 2;
    }

    private int cellX(int i) {
        return left + 10 + (i % 2) * (cellW + GAP);
    }

    private int cellY(int i) {
        return top + HEADER + 18 + (i / 2) * (cellH + GAP);
    }

    @Override
    protected void init() {
        if (minecraft.player == null) return;
        Attunement a = minecraft.player.getData(ModAttachments.ATTUNEMENT);
        winW = Math.min(width - 16, MAX_W);
        int fixed = HEADER + 18 + FOOTER + 6;
        cellH = Math.max(24, Math.min(30, (height - 8 - fixed) / rows() - GAP));
        winH = fixed + rows() * (cellH + GAP);
        left = (width - winW) / 2;
        top = Math.max(4, (height - winH) / 2);
        cellW = (winW - 20 - GAP) / 2;
        Component bindText = Component.translatable(a.everBound() ? "screen.orbisarcana.altar.switch" : "screen.orbisarcana.altar.bind");
        buttonW = font.width(bindText) + 14;
        for (int i = 0; i < Schools.all().size(); i++) {
            Identifier school = Schools.all().get(i);
            boolean dominant = a.dominant().filter(school::equals).isPresent();
            OrbisButton bind = new OrbisButton(cellX(i) + cellW - buttonW - 6, cellY(i) + (cellH - 16) / 2, buttonW, 16, bindText, Ui.ButtonStyle.NORMAL, b -> {
                ClientPacketDistributor.sendToServer(new AltarAction(altar, AltarService.Action.BIND.ordinal(), Optional.of(school)));
                onClose();
            });
            bind.active = !dominant && knowsSpellOf(school) && switchReady(a);
            addRenderableWidget(bind);
        }
        if (a.dominant().isPresent()) {
            Identifier dominant = a.dominant().get();
            int by = top + winH - FOOTER + 2;
            addRenderableWidget(new OrbisButton(width / 2 - 124, by, 120, 18, Component.translatable("screen.orbisarcana.altar.unbind"), Ui.ButtonStyle.NORMAL, b -> {
                ClientPacketDistributor.sendToServer(new AltarAction(altar, AltarService.Action.UNBIND.ordinal(), Optional.empty()));
                onClose();
            }));
            OrbisButton reset = new OrbisButton(width / 2 + 4, by, 120, 18, Component.translatable("screen.orbisarcana.altar.reset")
                    .append(" (" + com.orbisarcana.core.TalentRules.RESET_INK + ")"), Ui.ButtonStyle.NORMAL, b -> {
                ClientPacketDistributor.sendToServer(new AltarAction(altar, AltarService.Action.RESET_TALENTS.ordinal(), Optional.of(dominant)));
                onClose();
            });
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
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xA0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (minecraft.player == null) return;
        Attunement a = minecraft.player.getData(ModAttachments.ATTUNEMENT);
        Ui.panel(g, left, top, winW, winH);

        // header: title on the left, the dominant school on the right, a gold rule; Dissonance and the switch pause under it
        g.text(font, title, left + 10, top + 9, Ui.GOLD, true);
        Component dominantName = a.dominant().map(AttunementService::schoolName).orElse(Component.translatable("screen.orbisarcana.altar.neutral"));
        Component dominant = Component.translatable("screen.orbisarcana.altar.dominant", dominantName);
        g.text(font, dominant, left + winW - 10 - font.width(dominant), top + 9, Ui.TEXT_DIM, false);
        g.fill(left + 6, top + HEADER + 2, left + winW - 6, top + HEADER + 3, Ui.GOLD_DARK);
        long now = ClientCastState.gameTime();
        String status = "";
        if (now < a.dissonanceUntil()) status += Component.translatable("screen.orbisarcana.altar.dissonance", minutes(a.dissonanceUntil() - now)).getString() + "   ";
        if (a.everBound() && now < a.nextSwitchAt()) status += Component.translatable("screen.orbisarcana.altar.pause", minutes(a.nextSwitchAt() - now)).getString();
        if (!status.isEmpty()) g.centeredText(font, Component.literal(status.trim()), width / 2, top + HEADER + 6, Ui.DANGER);

        for (int i = 0; i < Schools.all().size(); i++) drawSchool(g, a, i);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    /** A school card: sigil and name, price or requirement, resonance and the bar towards the next level. */
    private void drawSchool(GuiGraphicsExtractor g, Attunement a, int i) {
        Identifier school = Schools.all().get(i);
        int x = cellX(i), y = cellY(i);
        boolean isDominant = a.dominant().filter(school::equals).isPresent();
        Ui.inset(g, x, y, cellW, cellH);
        if (isDominant) Ui.sprite(g, "frame", x, y, cellW, cellH, Ui.GOLD);
        Ui.sigil(g, school, x + 5, y + (cellH - 18) / 2, 18, isDominant ? 255 : 200);

        int tx = x + 28, rightEdge = x + cellW - buttonW - 12;
        Component name = AttunementService.schoolName(school);
        g.text(font, name, tx, y + line1(), Ui.opaque(Schools.color(school)), true);

        // price or requirement on the name's line, shortened when it does not fit
        if (!isDominant) {
            Component note;
            int noteColor = Ui.TEXT_DIM;
            if (!knowsSpellOf(school)) {
                note = Component.translatable("screen.orbisarcana.altar.need_spell");
                noteColor = Ui.DANGER;
            } else {
                SchoolRules.SwitchCost cost = AttunementService.costFor(a, school);
                note = cost.equals(SchoolRules.SwitchCost.FREE) ? Component.translatable("screen.orbisarcana.altar.free")
                        : Component.translatable("screen.orbisarcana.altar.cost", cost.ink(), cost.xpLevels());
                if (tx + font.width(name) + 6 + font.width(note) > rightEdge)
                    note = Component.translatable("screen.orbisarcana.altar.cost_short", cost.ink(), cost.xpLevels());
            }
            if (tx + font.width(name) + 6 + font.width(note) > rightEdge) note = Component.literal("—");
            g.text(font, note, rightEdge - font.width(note), y + line1(), noteColor, false);
        } else {
            Component mark = Component.translatable("screen.orbisarcana.altar.current");
            g.text(font, mark, rightEdge - font.width(mark), y + line1(), Ui.GOLD, false);
        }

        Attunement.SchoolProgress p = a.schools().get(school);
        int resonance = p == null ? 0 : p.resonance();
        Component resonanceText = Component.translatable("screen.orbisarcana.altar.resonance", resonance, SchoolRules.MAX_RESONANCE);
        g.text(font, resonanceText, tx, y + line1() + 11, Ui.TEXT_DIM, false);
        int barX = tx + font.width(resonanceText) + 5;
        if (rightEdge - barX > 10) {
            int need = SchoolRules.resonanceXpRequired(resonance);
            double frac = need <= 0 ? 1 : Math.min(1.0, (p == null ? 0 : p.xp()) / need);
            Ui.bar(g, barX, y + line1() + 11, rightEdge - barX, 7, frac, "bar_fill", Ui.opaque(Schools.color(school)), false);
        }
    }

    /** The first text line of a card; the second is 11 below. */
    private int line1() {
        return (cellH - 19) / 2;
    }

    private static String minutes(long ticks) {
        long s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
