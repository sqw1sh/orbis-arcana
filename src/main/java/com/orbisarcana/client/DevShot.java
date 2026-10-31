package com.orbisarcana.client;

import java.util.List;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.circle.ClientCircles;
import com.orbisarcana.compat.iris.IrisCompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Dev-only visual check, enabled by {@code -Dorbisarcana.devshot=true} (see the {@code clientShot} run in
 * build.gradle): after the world loads it shows the circle stand in every render mode, saves a screenshot
 * of each to {@code run/screenshots/devshot_<mode>.png}, then quits. Does nothing in normal play.
 * Third-party mods with load warnings need {@code showLoadWarnings = false} in run/config/neoforge-client.toml.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class DevShot {
    private static final boolean ENABLED = Boolean.getBoolean("orbisarcana.devshot");
    private static final int WARMUP_TICKS = 200;
    private static final int SHOT_DELAY = 50;
    private static final List<ClientConfig.RenderMode> MODES = List.of(
            ClientConfig.RenderMode.AUTO, ClientConfig.RenderMode.MESH, ClientConfig.RenderMode.PARTICLES);

    private static int ticks;
    private static int waiting;
    private static int modeIndex;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            // Diagnose a run that never reaches the world: log the screen and capture it
            if (++waiting % 400 == 0) {
                var screen = mc.gui.screen();
                OrbisArcana.LOGGER.info("[devshot] still waiting for the world, screen = {}", screen == null ? "none" : screen.getClass().getName());
                Screenshot.grab(mc.gameDirectory, "devshot_waiting.png", mc.gameRenderer.mainRenderTarget(), 2, msg -> {});
            }
            return;
        }
        ticks++;
        if (ticks < WARMUP_TICKS) return;
        int step = ticks - WARMUP_TICKS;
        if (step % SHOT_DELAY == 0) {
            if (modeIndex >= MODES.size()) {
                OrbisArcana.LOGGER.info("[devshot] done, quitting");
                mc.stop();
                return;
            }
            ClientConfig.RenderMode mode = MODES.get(modeIndex);
            ClientConfig.RENDER_MODE.set(mode);
            mc.player.setYRot(0);
            mc.player.setXRot(30);
            ClientCircles.spawnDemo(9);
            OrbisArcana.LOGGER.info("[devshot] mode {} (iris loaded={}, shaders={}, pipeline assigned={})",
                    mode, IrisCompat.isLoaded(), IrisCompat.isShaderPackInUse(), IrisCompat.isPipelineAssigned());
        } else if (step % SHOT_DELAY == SHOT_DELAY - 5) {
            String name = "devshot_" + MODES.get(modeIndex).name().toLowerCase(java.util.Locale.ROOT) + ".png";
            Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 2, msg -> OrbisArcana.LOGGER.info("[devshot] saved {}", name));
            modeIndex++;
        }
    }

    private DevShot() {}
}
