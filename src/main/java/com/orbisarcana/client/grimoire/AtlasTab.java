package com.orbisarcana.client.grimoire;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.client.ui.OrbisButton;
import com.orbisarcana.client.ui.Ui;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.item.SpellScrollItem;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.SpellDefinition;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Atlas tab (3D overview of every spell): the list of all spells (unlearned ones keep their name hidden) and a 3D
 * viewport where the mage casts the selected one in a loop: the circle charges, flashes on release and fades. Drag
 * to orbit, wheel to zoom; play/pause, loop and the mastery level of the preview (I, V, X, XV) are below.
 */
class AtlasTab extends GrimoireTab {
    private static final int LIST_W = 140, ROW_H = 18;
    private static final int[] LEVELS = {1, 5, 10, 15};
    private static final float RELEASE_SEC = 0.35f, HOLD_SEC = 0.9f;

    private static @Nullable Identifier spell;
    private static boolean playing = true, loop = true;
    private static int level = 15;
    private static float yaw = 35, pitch = 18, zoom = 1;
    private static float cycle, spin;

    private int scroll;
    private long lastNanos = System.nanoTime();
    private boolean dragging;
    private @Nullable OrbisButton playButton, loopButton;
    private final List<OrbisButton> levelButtons = new ArrayList<>();

    AtlasTab(GrimoireScreen screen) {
        super(screen);
    }

    private List<Identifier> spells() {
        var registries = player().registryAccess();
        List<Identifier> out = new ArrayList<>(SpellRegistry.ids(registries));
        List<Identifier> schools = Schools.all();
        out.sort(Comparator.<Identifier>comparingInt(id -> SpellRegistry.get(registries, id).map(d -> schools.indexOf(d.school())).orElse(99))
                .thenComparingInt(id -> SpellRegistry.get(registries, id).map(d -> d.rarity().ordinal()).orElse(0))
                .thenComparing(id -> SpellScrollItem.spellName(id).getString()));
        return out;
    }

    private boolean known(Identifier id) {
        return player().getData(ModAttachments.KNOWLEDGE).spells().containsKey(id);
    }

    private int vx() { return x + LIST_W + 10; }
    private int vw() { return w - LIST_W - 10; }
    private int vh() { return h - 24; }

    @Override
    void init() {
        List<Identifier> all = spells();
        if (spell == null || !all.contains(spell)) {
            spell = GrimoireScreen.selectedSpell != null && all.contains(GrimoireScreen.selectedSpell) ? GrimoireScreen.selectedSpell
                    : all.isEmpty() ? null : all.get(0);
        }
        int by = y + h - 18;
        playButton = screen.addTabWidget(new OrbisButton(vx(), by, 56, 18, playLabel(), Ui.ButtonStyle.PRIMARY, b -> {
            playing = !playing;
            b.setMessage(playLabel());
        }));
        loopButton = screen.addTabWidget(new OrbisButton(vx() + 60, by, 64, 18, loopLabel(), Ui.ButtonStyle.NORMAL, b -> {
            loop = !loop;
            cycle = 0;
            b.setMessage(loopLabel());
        }));
        levelButtons.clear();
        int lx = vx() + vw() - LEVELS.length * 26 + 2;
        for (int i = 0; i < LEVELS.length; i++) {
            int lv = LEVELS[i];
            levelButtons.add(screen.addTabWidget(new OrbisButton(lx + i * 26, by, 24, 18, Component.literal(Ui.roman(lv)),
                    lv == level ? Ui.ButtonStyle.PRIMARY : Ui.ButtonStyle.NORMAL, b -> {
                        level = lv;
                        cycle = 0;
                        screen.open(GrimoireScreen.Tab.ATLAS);
                    })));
        }
    }

    private static Component playLabel() {
        return Component.translatable(playing ? "screen.orbisarcana.atlas.pause" : "screen.orbisarcana.atlas.play");
    }

    private static Component loopLabel() {
        return Component.translatable(loop ? "screen.orbisarcana.atlas.loop_on" : "screen.orbisarcana.atlas.loop_off");
    }

    @Override
    void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        renderList(g, mouseX, mouseY);

        int vx = vx(), vy = y, vw = vw(), vh = vh();
        Ui.inset(g, vx, vy, vw, vh);
        Optional<SpellDefinition> def = spell == null ? Optional.empty() : SpellRegistry.get(player().registryAccess(), spell);
        if (def.isEmpty()) return;
        SpellDefinition d = def.get();

        // the cast cycle: charge over the cast time, release flash, hold, again
        float castSec = Math.max(0.6f, d.cast().ticks() / 20f), total = castSec + RELEASE_SEC + HOLD_SEC;
        if (playing) {
            spin += dt;
            cycle += dt;
            if (cycle > total) cycle = loop ? cycle - total : total;
        }
        float progress = Math.min(1, cycle / castSec);
        float flash = cycle > castSec && cycle < castSec + RELEASE_SEC ? 1 - (cycle - castSec) / RELEASE_SEC : 0;

        EntityRenderState mage = mc.getEntityRenderDispatcher().getRenderer(player()).createRenderState(player(), 1.0F);
        mage.shadowPieces.clear();
        mage.outlineColor = 0;
        mage.nameTag = null;
        if (mage instanceof LivingEntityRenderState living) {
            living.bodyRot = 180;
            living.yRot = 0;
            living.xRot = 0;
            living.scale = 1;
        }
        float scale = Math.min(vw, vh) / 3.4f * zoom;
        g.submitPictureInPictureRenderState(new AtlasPreview.State(mage, spell, d, level, progress, spin, flash, cycle, yaw, pitch,
                vx + 1, vy + 1, vx + vw - 1, vy + vh - 1, scale, null));

        // overlay: name, school and placement; level; the hint
        boolean known = known(spell);
        int rarity = Ui.opaque(d.rarity().color);
        g.text(font, known ? SpellScrollItem.spellName(spell) : Component.translatable("screen.orbisarcana.atlas.unknown"), vx + 6, vy + 5, rarity, true);
        Component sub = Component.translatable(Schools.nameKey(d.school())).withColor(Ui.opaque(Schools.color(d.school())))
                .append(Component.literal(" · ").append(Component.translatable("targeting.orbisarcana." + d.targeting().id())).withColor(Ui.TEXT_DIM));
        g.text(font, sub, vx + 6, vy + 16, -1, true);
        String lv = Ui.roman(level);
        g.text(font, lv, vx + vw - 6 - font.width(lv), vy + 5, Ui.GOLD, true);
        Component hint = Component.translatable("screen.orbisarcana.atlas.hint");
        // charge bar bottom left, the hint above it (they do not fit side by side in small windows)
        Ui.bar(g, vx + 6, vy + vh - 12, Math.min(80, vw / 3), 6, progress, "bar_fill", Ui.opaque(Schools.color(d.school())), true);
        g.text(font, ellipsize(hint.getString(), vw - 12), vx + 6, vy + vh - 24, Ui.TEXT_DIM, false);
    }

    private void renderList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<Identifier> all = spells();
        int rows = Math.max(1, h / ROW_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - rows)));
        for (int i = 0; i < rows && i + scroll < all.size(); i++) {
            Identifier id = all.get(i + scroll);
            var d = SpellRegistry.get(player().registryAccess(), id).orElseThrow();
            int ry = y + i * ROW_H;
            boolean selected = id.equals(spell), hover = inside(mouseX, mouseY, x, ry, LIST_W, ROW_H - 1), known = known(id);
            if (selected) {
                g.fill(x, ry, x + LIST_W, ry + ROW_H - 1, 0x305FE3C8);
                g.fill(x, ry, x + 1, ry + ROW_H - 1, Ui.TEAL);
            } else if (hover) {
                g.fill(x, ry, x + LIST_W, ry + ROW_H - 1, 0x18FFFFFF);
            }
            Ui.sigil(g, d.school(), x + 2, ry + 1, 16, known ? 0xFF : 0x60);
            String name = known ? SpellScrollItem.spellName(id).getString() : Component.translatable("screen.orbisarcana.atlas.unknown").getString();
            g.text(font, ellipsize(name, LIST_W - 24), x + 21, ry + 5, known ? Ui.opaque(d.rarity().color) : Ui.TEXT_DIM, false);
        }
        if (all.size() > rows) {
            int trackH = rows * ROW_H, thumbH = Math.max(10, trackH * rows / all.size());
            int ty = y + (trackH - thumbH) * scroll / Math.max(1, all.size() - rows);
            g.fill(x + LIST_W + 1, y, x + LIST_W + 2, y + trackH, Ui.LINE);
            g.fill(x + LIST_W, ty, x + LIST_W + 3, ty + thumbH, Ui.GOLD_DARK);
        }
    }

    @Override
    boolean mouseClicked(double mx, double my, int button) {
        dragging = inside(mx, my, vx(), y, vw(), vh());
        if (dragging) return true;
        List<Identifier> all = spells();
        int rows = Math.max(1, h / ROW_H);
        for (int i = 0; i < rows && i + scroll < all.size(); i++) {
            if (inside(mx, my, x, y + i * ROW_H, LIST_W, ROW_H - 1)) {
                spell = all.get(i + scroll);
                cycle = 0;
                return true;
            }
        }
        return false;
    }

    @Override
    boolean mouseDragged(double mx, double my, double dx, double dy) {
        if (!dragging) return false;
        yaw += (float) dx * 1.2f;
        pitch = Math.max(-10, Math.min(80, pitch + (float) dy * 0.8f));
        return true;
    }

    @Override
    boolean mouseScrolled(double mx, double my, double delta) {
        if (inside(mx, my, vx(), y, vw(), vh())) {
            zoom = Math.max(0.5f, Math.min(2.5f, zoom * (delta > 0 ? 1.1f : 1 / 1.1f)));
            return true;
        }
        if (inside(mx, my, x, y, LIST_W + 4, h)) {
            scroll = Math.max(0, scroll + (delta > 0 ? -1 : 1));
            return true;
        }
        return false;
    }
}
