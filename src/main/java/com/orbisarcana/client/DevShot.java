package com.orbisarcana.client;

import java.util.List;
import java.util.Locale;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.circle.ClientCircles;
import com.orbisarcana.compat.iris.IrisCompat;
import com.orbisarcana.progression.KnowledgeService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Dev-only visual check, enabled by {@code -Dorbisarcana.devshot=true} (the {@code clientShot} run in
 * build.gradle). After the world loads it plays a list of scenes (circle stand in every render mode, radial
 * menu, grimoire), saves {@code run/screenshots/devshot_<scene>.png} for each, then quits.
 * Third-party mods with load warnings need {@code showLoadWarnings = false} in run/config/neoforge-client.toml.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class DevShot {
    private static final boolean ENABLED = Boolean.getBoolean("orbisarcana.devshot");
    private static final int WARMUP_TICKS = 200;
    private static final int SCENE_TICKS = 50;

    private record Scene(String name, Runnable setup) {}

    private static final List<Scene> SCENES = List.of(
            circles(ClientConfig.RenderMode.AUTO),
            circles(ClientConfig.RenderMode.MESH),
            circles(ClientConfig.RenderMode.PARTICLES),
            new Scene("radial", () -> {
                learnTestSpells();
                Minecraft.getInstance().gui.setScreen(new RadialMenuScreen(true));
            }),
            new Scene("grimoire", () -> Minecraft.getInstance().gui.setScreen(new GrimoireScreen())));

    private static int ticks;
    private static int waiting;
    private static int scene;

    private static Scene circles(ClientConfig.RenderMode mode) {
        return new Scene(mode.name().toLowerCase(Locale.ROOT), () -> {
            Minecraft mc = Minecraft.getInstance();
            ClientConfig.RENDER_MODE.set(mode);
            mc.player.setYRot(0);
            mc.player.setXRot(30);
            ClientCircles.spawnDemo(9);
        });
    }

    /** Teaches the four test spells on the integrated server so menus have content. */
    private static void learnTestSpells() {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;
            for (String id : List.of("fireball", "life_bloom", "healing_dew", "wind_step")) KnowledgeService.learn(sp, OrbisArcana.id(id));
            KnowledgeService.setMastery(sp, OrbisArcana.id("fireball"), 7);
        });
    }

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
        if (++ticks < WARMUP_TICKS) return;
        int step = ticks - WARMUP_TICKS;
        if (step % SCENE_TICKS == 0) {
            if (scene >= SCENES.size()) {
                OrbisArcana.LOGGER.info("[devshot] done, quitting");
                mc.stop();
                return;
            }
            OrbisArcana.LOGGER.info("[devshot] scene {} (iris={}, shaders={}, pipeline assigned={})", SCENES.get(scene).name(),
                    IrisCompat.isLoaded(), IrisCompat.isShaderPackInUse(), IrisCompat.isPipelineAssigned());
            SCENES.get(scene).setup().run();
        } else if (step % SCENE_TICKS == SCENE_TICKS - 5) {
            String name = "devshot_" + SCENES.get(scene).name() + ".png";
            Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 2, msg -> OrbisArcana.LOGGER.info("[devshot] saved {}", name));
            scene++;
        }
    }

    private DevShot() {}
}
