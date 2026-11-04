package com.orbisarcana.client.grimoire;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.client.ui.OrbisButton;
import com.orbisarcana.client.ui.Ui;
import com.orbisarcana.core.SchoolRules;
import com.orbisarcana.core.TalentRules;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.network.CastPayloads.TalentPick;
import com.orbisarcana.school.Attunement;
import com.orbisarcana.school.Schools;
import com.orbisarcana.school.TalentEffects;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Talents tab: the talent tree of a school as a graph (Echo → Root → branch A: attack, branch B: defence and
 * control; the second node of a branch needs the first). Gold = learned or active, teal = can be learned now, grey =
 * locked. The node under the mouse (or the last clicked) is described on the right with a button to learn it.
 */
class TalentsTab extends GrimoireTab {
    private static final int SIDE_W = 132, NODE = 30;
    private static final String ECHO = "echo";
    private static final List<String> ALL = List.of(ECHO, TalentRules.ROOT, TalentRules.A1, TalentRules.A2, TalentRules.A3, TalentRules.B1, TalentRules.B2, TalentRules.B3);
    /** The school shown has the third nodes (its pack added them): four columns instead of three. */
    private boolean deep;

    private static String focused = TalentRules.ROOT;
    private @Nullable String hovered;
    private @Nullable OrbisButton learn;

    TalentsTab(GrimoireScreen screen) {
        super(screen);
    }

    private record State(Identifier school, int resonance, Set<String> picked, boolean dominant) {}

    private State state() {
        Attunement a = player().getData(ModAttachments.ATTUNEMENT);
        Identifier s = SchoolsTab.selected(a);
        var p = a.schools().get(s);
        return new State(s, p == null ? 0 : p.resonance(), p == null ? Set.of() : p.talents(), a.dominant().filter(s::equals).isPresent());
    }

    @Override
    void init() {
        int sx = x + w - SIDE_W;
        learn = screen.addTabWidget(new OrbisButton(sx, y + h - 18, SIDE_W, 18, Component.translatable("screen.orbisarcana.talents.learn"),
                Ui.ButtonStyle.PRIMARY, b -> {
                    State st = state();
                    ClientPacketDistributor.sendToServer(new TalentPick(st.school(), focused));
                }));
    }

    // ---- layout ----
    private int graphTop() {
        return y + 40;
    }

    private int[] pos(String node) {
        int gx = x, gw = w - SIDE_W - 14, gy = graphTop(), gh = y + h - gy;
        int mid = gy + gh / 2 - 6, off = Math.max(34, Math.min(50, gh / 2 - 26));
        float c1 = deep ? 0.46f : 0.55f, c2 = deep ? 0.69f : 0.88f, c3 = 0.91f;
        return switch (node) {
            case ECHO -> new int[] {gx + (int) (gw * 0.08), mid};
            case TalentRules.ROOT -> new int[] {gx + (int) (gw * (deep ? 0.26 : 0.29)), mid};
            case TalentRules.A1 -> new int[] {gx + (int) (gw * c1), mid - off};
            case TalentRules.A2 -> new int[] {gx + (int) (gw * c2), mid - off};
            case TalentRules.A3 -> new int[] {gx + (int) (gw * c3), mid - off};
            case TalentRules.B1 -> new int[] {gx + (int) (gw * c1), mid + off};
            case TalentRules.B2 -> new int[] {gx + (int) (gw * c2), mid + off};
            default -> new int[] {gx + (int) (gw * c3), mid + off};
        };
    }

    /** 2 = learned/active, 1 = can be learned now, 0 = locked. */
    private int status(State st, String node) {
        return switch (node) {
            case ECHO -> st.dominant() && st.resonance() >= 1 ? 2 : 0;
            case TalentRules.ROOT -> st.dominant() && TalentRules.rootActive(st.resonance()) ? 2 : 0;
            default -> st.picked().contains(node) ? 2 : TalentRules.check(st.dominant(), node, st.picked(), st.resonance()).isEmpty() ? 1 : 0;
        };
    }

    private static int statusColor(int status) {
        return status == 2 ? Ui.GOLD : status == 1 ? Ui.TEAL : Ui.LINE;
    }

    @Override
    void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        State st = state();
        // school picker
        List<Identifier> all = Schools.all();
        for (int i = 0; i < all.size(); i++) {
            Identifier s = all.get(i);
            int sx = x + i * 21;
            boolean sel = s.equals(st.school()), hover = inside(mouseX, mouseY, sx, y, 20, 20);
            Ui.slot(g, sx, y, 20, sel ? Ui.TEAL : hover ? Ui.TEXT_DIM : 0);
            Ui.sigil(g, s, sx + 2, y + 2, 16, sel || hover ? 0xFF : 0x90);
        }
        // talent points: top of the side panel
        g.text(font, Component.translatable("screen.orbisarcana.schools.points", TalentRules.pointsLeft(st.resonance(), st.picked())), x + w - SIDE_W, y + 6, Ui.GOLD, false);

        // branch captions
        deep = TalentEffects.implemented(st.school().getPath(), TalentRules.A3);
        int[] a1 = pos(TalentRules.A1), a2 = pos(TalentRules.A2), b1 = pos(TalentRules.B1), b2 = pos(TalentRules.B2);
        int[] aEnd = deep ? pos(TalentRules.A3) : a2, bEnd = deep ? pos(TalentRules.B3) : b2;
        Component ca = Component.translatable("screen.orbisarcana.talents.branch_a"), cb = Component.translatable("screen.orbisarcana.talents.branch_b");
        g.centeredText(font, ca, (a1[0] + aEnd[0]) / 2, a1[1] - NODE / 2 - 12, Ui.TEXT_DIM);
        // branch B caption above its row too: below, the two-line names would hit it
        g.centeredText(font, cb, (b1[0] + bEnd[0]) / 2, b1[1] - NODE / 2 - 12, Ui.TEXT_DIM);

        // connectors, drawn under the nodes
        link(g, pos(ECHO), pos(TalentRules.ROOT), Math.min(status(st, ECHO), status(st, TalentRules.ROOT)));
        link(g, pos(TalentRules.ROOT), a1, status(st, TalentRules.A1));
        link(g, a1, a2, status(st, TalentRules.A2));
        link(g, pos(TalentRules.ROOT), b1, status(st, TalentRules.B1));
        link(g, b1, b2, status(st, TalentRules.B2));
        if (deep) {
            link(g, a2, aEnd, status(st, TalentRules.A3));
            link(g, b2, bEnd, status(st, TalentRules.B3));
        }

        hovered = null;
        int color = Ui.opaque(Schools.color(st.school()));
        for (String node : ALL) {
            if (!deep && TalentRules.DEEP.contains(node)) continue;
            int[] p = pos(node);
            int status = status(st, node);
            int nx = p[0] - NODE / 2, ny = p[1] - NODE / 2;
            boolean hover = inside(mouseX, mouseY, nx, ny, NODE, NODE);
            if (hover) hovered = node;
            if (status == 2 || hover || node.equals(focused)) {
                Ui.sprite(g, "glow", p[0] - 24, p[1] - 24, 48, 48, ((status == 2 ? 0x80 : 0x50) << 24) | (color & 0xFFFFFF));
            }
            Ui.sprite(g, "radial_slot", nx, ny, NODE, NODE);
            int edge = node.equals(focused) ? Ui.TEXT : statusColor(status);
            Ui.sprite(g, "radial_slot_edge", nx, ny, NODE, NODE, edge);
            Ui.sigil(g, st.school(), p[0] - 8, p[1] - 8, 16, status == 2 ? 0xFF : status == 1 ? 0xC0 : 0x50);
            String name = Component.translatable(nodeKey(st.school(), node)).getString();
            // names must not run into the neighbour node in the same row
            int gw = w - SIDE_W - 14, maxW = node.equals(ECHO) || node.equals(TalentRules.ROOT) ? (int) (gw * 0.2) : (int) (gw * (deep ? 0.21 : 0.31));
            // up to two lines under the node (split at a space), the full text is in the side panel
            String first = name, rest = "";
            if (font.width(name) > maxW) {
                int fit = font.plainSubstrByWidth(name, maxW).length(), cut = name.lastIndexOf(' ', fit);
                if (cut <= 0) cut = fit;
                first = name.substring(0, cut).trim();
                rest = ellipsize(name.substring(cut).trim(), maxW);
            }
            int labelColor = status == 0 ? Ui.TEXT_DIM : status == 1 ? Ui.TEAL : Ui.TEXT;
            g.centeredText(font, first, p[0], p[1] + NODE / 2 + 2, labelColor);
            if (!rest.isEmpty()) g.centeredText(font, rest, p[0], p[1] + NODE / 2 + 11, labelColor);
        }
        renderDetail(g, st, hovered != null ? hovered : focused);
    }

    /** Elbow connector: horizontal to the middle, vertical, horizontal on; 2 px thick. */
    private void link(GuiGraphicsExtractor g, int[] a, int[] b, int status) {
        int c = status == 2 ? Ui.GOLD : status == 1 ? Ui.TEAL : Ui.LINE;
        int ax = a[0] + NODE / 2, bx = b[0] - NODE / 2, mx = (ax + bx) / 2;
        g.fill(ax, a[1] - 1, mx + 1, a[1] + 1, c);
        g.fill(mx - 1, Math.min(a[1], b[1]) - 1, mx + 1, Math.max(a[1], b[1]) + 1, c);
        g.fill(mx - 1, b[1] - 1, bx, b[1] + 1, c);
    }

    private static String nodeKey(Identifier school, String node) {
        return "talent." + school.getNamespace() + "." + school.getPath() + "." + node;
    }

    private void renderDetail(GuiGraphicsExtractor g, State st, String node) {
        int sx = x + w - SIDE_W, sy = y + 26, sw = SIDE_W;
        g.fill(sx - 8, y, sx - 7, y + h, Ui.LINE);
        int status = status(st, node);
        g.text(font, Component.translatable(nodeKey(st.school(), node)), sx, sy, statusColor(status) == Ui.LINE ? Ui.TEXT : statusColor(status), true);
        sy += 11;
        Component kind = switch (node) {
            case ECHO -> Component.translatable("screen.orbisarcana.schools.echo", SchoolRules.echoTier(st.dominant() ? st.resonance() : 0));
            case TalentRules.ROOT -> Component.translatable("screen.orbisarcana.schools.root");
            case TalentRules.A1, TalentRules.A2, TalentRules.A3 -> Component.translatable("screen.orbisarcana.schools.branch_a");
            default -> Component.translatable("screen.orbisarcana.schools.branch_b");
        };
        sy = wrap(g, kind, sx, sy, sw, Ui.TEXT_DIM, 2) + 4;
        sy = wrap(g, Component.translatable(nodeKey(st.school(), node) + ".desc"), sx, sy, sw, Ui.TEXT, Math.max(1, (y + h - 52 - sy) / 10)) + 4;

        MutableComponent stateLine;
        if (status == 2) stateLine = Component.translatable(node.equals(ECHO) || node.equals(TalentRules.ROOT)
                ? "screen.orbisarcana.talents.active" : "screen.orbisarcana.talents.learned").withColor(0xFF7CE07C);
        else if (status == 1) stateLine = Component.translatable("screen.orbisarcana.talents.available").withColor(Ui.TEAL);
        else {
            Optional<TalentRules.Denial> denial = node.equals(ECHO) || node.equals(TalentRules.ROOT)
                    ? Optional.of(st.dominant() ? TalentRules.Denial.NO_POINTS : TalentRules.Denial.NOT_DOMINANT)
                    : TalentRules.check(st.dominant(), node, st.picked(), st.resonance());
            String id = denial.map(TalentRules.Denial::id).orElse("not_dominant");
            if (!st.dominant()) id = "not_dominant";
            else if (node.equals(ECHO) || node.equals(TalentRules.ROOT)) id = "needs_resonance";
            stateLine = Component.translatable("screen.orbisarcana.talents.denied." + id).withColor(Ui.DANGER);
        }
        if (!node.equals(ECHO) && !TalentEffects.implemented(st.school().getPath(), node)) {
            stateLine.append(" ").append(Component.translatable("screen.orbisarcana.schools.wip").withColor(0xFFB09060));
        }
        wrap(g, stateLine, sx, sy, sw, -1, Math.max(1, (y + h - 22 - sy) / 10));

        if (learn != null) {
            boolean talent = TalentRules.NODES.contains(focused);
            learn.visible = talent;
            learn.active = talent && status(st, focused) == 1;
        }
    }

    @Override
    boolean mouseClicked(double mx, double my, int button) {
        List<Identifier> all = Schools.all();
        for (int i = 0; i < all.size(); i++) {
            if (inside(mx, my, x + i * 21, y, 20, 20)) {
                GrimoireScreen.selectedSchool = all.get(i);
                return true;
            }
        }
        for (String node : ALL) {
            if (!deep && TalentRules.DEEP.contains(node)) continue;
            int[] p = pos(node);
            if (inside(mx, my, p[0] - NODE / 2, p[1] - NODE / 2, NODE, NODE)) {
                focused = node;
                return true;
            }
        }
        return false;
    }
}
