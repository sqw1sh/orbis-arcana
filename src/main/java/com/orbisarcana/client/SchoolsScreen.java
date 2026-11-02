package com.orbisarcana.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.core.SchoolRules;
import com.orbisarcana.core.TalentRules;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.network.CastPayloads.TalentPick;
import com.orbisarcana.school.Attunement;
import com.orbisarcana.school.AttunementService;
import com.orbisarcana.school.Schools;
import com.orbisarcana.school.TalentEffects;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Grimoire "Schools" tab (§13.4): resonance of every school, the dominant one, its Echo, root passive
 * and the two talent branches. Nodes of the dominant school are picked by clicking them.
 */
public class SchoolsScreen extends Screen {
    private static final int LIST_W = 110, CARD_W = 260, ROW_H = 15;
    private static final int PANEL = 0xC0101018, TEXT = 0xFFE8E8F0, DIM = 0xFF8A8A9A, GOOD = 0xFF7CE07C, LOCKED = 0xFF5A5A6A, WIP = 0xFFB09060;

    private record Node(int x, int y, int w, int h, String node) {}

    private final List<Node> clickable = new ArrayList<>();
    private @Nullable Identifier selected;

    public SchoolsScreen() {
        super(Component.translatable("screen.orbisarcana.tab.schools"));
    }

    /** Tab buttons shared by both grimoire screens. */
    static void addTabs(Screen screen, java.util.function.Consumer<Button> add) {
        add.accept(Button.builder(Component.translatable("screen.orbisarcana.tab.knowledge"),
                b -> screen.getMinecraft().gui.setScreen(new GrimoireScreen())).bounds(6, 6, 70, 16).build());
        add.accept(Button.builder(Component.translatable("screen.orbisarcana.tab.schools"),
                b -> screen.getMinecraft().gui.setScreen(new SchoolsScreen())).bounds(80, 6, 70, 16).build());
    }

    @Override
    protected void init() {
        if (minecraft.player == null) return;
        Attunement a = minecraft.player.getData(ModAttachments.ATTUNEMENT);
        if (selected == null) selected = a.dominant().orElse(Schools.FIRE);
        addTabs(this, this::addRenderableWidget);
        int left = left();
        for (int i = 0; i < Schools.all().size(); i++) {
            Identifier school = Schools.all().get(i);
            addRenderableWidget(Button.builder(AttunementService.schoolName(school), b -> {
                selected = school;
                rebuildWidgets();
            }).bounds(left, 30 + i * ROW_H, LIST_W, ROW_H - 1).build());
        }
    }

    private int left() {
        return Math.max(6, (width - LIST_W - CARD_W - 10) / 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Node n : clickable) {
            if (event.x() >= n.x() && event.x() < n.x() + n.w() && event.y() >= n.y() - 1 && event.y() < n.y() + n.h()) {
                ClientPacketDistributor.sendToServer(new TalentPick(selected, n.node()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (minecraft.player == null || selected == null) return;
        clickable.clear();
        int left = left();
        int x = left + LIST_W + 10;
        g.fill(left - 4, 26, left + LIST_W + 4, 30 + Schools.all().size() * ROW_H + 4, PANEL);
        g.fill(x - 4, 26, x + CARD_W + 4, height - 8, PANEL);
        super.extractRenderState(g, mouseX, mouseY, partial);

        Attunement a = minecraft.player.getData(ModAttachments.ATTUNEMENT);
        Component dominantName = a.dominant().map(AttunementService::schoolName).orElse(Component.translatable("screen.orbisarcana.altar.neutral"));
        g.centeredText(font, Component.translatable("screen.orbisarcana.schools.title", dominantName), width / 2 + 40, 10, TEXT);

        String s = selected.getPath();
        Attunement.SchoolProgress p = a.schools().get(selected);
        int resonance = p == null ? 0 : p.resonance();
        var picked = p == null ? java.util.Set.<String>of() : p.talents();
        boolean dominant = a.dominant().filter(selected::equals).isPresent();
        int y = 30;

        MutableComponent header = AttunementService.schoolName(selected).copy();
        if (dominant) header.append(Component.literal(" ◆ ").append(Component.translatable("screen.orbisarcana.schools.state.dominant")).withColor(DIM));
        else if (p != null && resonance > 0) header.append(Component.literal(" · ").append(Component.translatable("screen.orbisarcana.schools.state.frozen")).withColor(DIM));
        g.text(font, header, x, y, TEXT, true);
        y += 11;
        g.text(font, Component.translatable("screen.orbisarcana.schools.resonance", resonance, SchoolRules.MAX_RESONANCE)
                .append("   ").append(Component.translatable("screen.orbisarcana.schools.points", TalentRules.pointsLeft(resonance, picked))), x, y, DIM, false);
        y += 10;
        int need = SchoolRules.resonanceXpRequired(resonance);
        float frac = need <= 0 ? 1f : (float) Math.min(1.0, (p == null ? 0 : p.xp()) / need);
        g.fill(x, y, x + CARD_W, y + 3, 0xFF2A2A40);
        g.fill(x, y, x + (int) (CARD_W * frac), y + 3, 0xFF000000 | Schools.color(selected));
        y += 8;

        if (!dominant) y = wrap(g, Component.translatable("screen.orbisarcana.schools.not_dominant"), x, y, WIP) + 3;

        y = section(g, Component.translatable("screen.orbisarcana.schools.echo", SchoolRules.echoTier(dominant ? resonance : 0)), x, y);
        y = entry(g, s, "echo", x, y, dominant && resonance >= 1 ? GOOD : DIM, Optional.empty());
        y = section(g, Component.translatable("screen.orbisarcana.schools.root"), x, y);
        y = entry(g, s, "root", x, y, dominant && TalentRules.rootActive(resonance) ? GOOD : DIM, Optional.empty());
        y = section(g, Component.translatable("screen.orbisarcana.schools.branch_a"), x, y);
        y = talent(g, a, s, TalentRules.A1, picked, resonance, dominant, x, y);
        y = talent(g, a, s, TalentRules.A2, picked, resonance, dominant, x, y);
        y = section(g, Component.translatable("screen.orbisarcana.schools.branch_b"), x, y);
        y = talent(g, a, s, TalentRules.B1, picked, resonance, dominant, x, y);
        talent(g, a, s, TalentRules.B2, picked, resonance, dominant, x, y);
    }

    private int talent(GuiGraphicsExtractor g, Attunement a, String school, String node, java.util.Set<String> picked,
                       int resonance, boolean dominant, int x, int y) {
        boolean has = picked.contains(node);
        boolean canPick = TalentRules.check(dominant, node, picked, resonance).isEmpty();
        String mark = has ? "[✔] " : canPick ? "[+] " : "[ ] ";
        int color = has ? GOOD : canPick ? TEXT : LOCKED;
        if (canPick) clickable.add(new Node(x, y, CARD_W, 9, node));
        return entry(g, school, node, x, y, color, Optional.of(mark));
    }

    private int section(GuiGraphicsExtractor g, Component title, int x, int y) {
        if (y + 9 > height - 10) return y;
        g.text(font, title, x, y + 2, DIM, false);
        return y + 12;
    }

    /** Name (+ "in development" if the effect is not live yet) and wrapped description. */
    private int entry(GuiGraphicsExtractor g, String school, String node, int x, int y, int color, Optional<String> mark) {
        String key = "talent.orbisarcana." + school + "." + node;
        MutableComponent line = Component.literal(mark.orElse("")).append(Component.translatable(key)).append(" — ")
                .append(Component.translatable(key + ".desc"));
        if (!TalentEffects.implemented(school, node)) line.append(" ").append(Component.translatable("screen.orbisarcana.schools.wip").withColor(WIP));
        return wrap(g, line, x, y, color);
    }

    private int wrap(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
        for (var part : font.split(text, CARD_W)) {
            if (y + 9 > height - 10) return y;
            g.text(font, part, x, y, color, false);
            y += 10;
        }
        return y + 1;
    }
}
