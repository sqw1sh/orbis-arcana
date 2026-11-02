package com.orbisarcana.client;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.circle.ClientCircles;
import com.orbisarcana.compat.iris.IrisCompat;
import com.orbisarcana.network.CastPayloads.CastStarted;
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

    private static final List<Scene> ALL_SCENES = List.of(
            circles(ClientConfig.RenderMode.AUTO),
            circles(ClientConfig.RenderMode.MESH),
            circles(ClientConfig.RenderMode.PARTICLES),
            new Scene("radial", () -> {
                learnTestSpells();
                Minecraft.getInstance().gui.setScreen(new RadialMenuScreen(true));
            }),
            new Scene("grimoire", () -> Minecraft.getInstance().gui.setScreen(new GrimoireScreen())),
            ownCircle("own_down_yaw0", 0, 88),
            ownCircle("own_down_yaw120", 120, 88),
            ownCircle("own_ahead", 45, 10),
            new Scene("hud_one_charge", () -> castWindStep(true)),
            new Scene("hud_no_charges", () -> castWindStep(false)),
            new Scene("altar_setup", () -> {
                Minecraft mc = Minecraft.getInstance();
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp == null) return;
                    com.orbisarcana.school.AttunementService.bind(sp, com.orbisarcana.school.Schools.FIRE);
                    sp.getData(com.orbisarcana.data.ModAttachments.ATTUNEMENT).school(com.orbisarcana.school.Schools.FIRE).setResonance(4);
                    sp.syncData(com.orbisarcana.data.ModAttachments.ATTUNEMENT);
                });
            }),
            new Scene("altar", () -> Minecraft.getInstance().gui.setScreen(new AltarScreen(Minecraft.getInstance().player.blockPosition()))),
            new Scene("ritual", DevShot::startRitual),
            new Scene("schools_setup", () -> {
                Minecraft mc = Minecraft.getInstance();
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp == null) return;
                    var a = sp.getData(com.orbisarcana.data.ModAttachments.ATTUNEMENT);
                    a.reset();
                    com.orbisarcana.school.AttunementService.bind(sp, com.orbisarcana.school.Schools.FIRE);
                    a.school(com.orbisarcana.school.Schools.FIRE).setResonance(6);
                    com.orbisarcana.school.AttunementService.pickTalent(sp, com.orbisarcana.school.Schools.FIRE, "a1");
                });
            }),
            new Scene("schools", () -> Minecraft.getInstance().gui.setScreen(new SchoolsScreen())),
            new Scene("curios", DevShot::curiosCheck),
            new Scene("items", DevShot::showItems),
            new Scene("blocks", DevShot::showBlocks),
            structure("ruined_observatory", 14),
            structure("flooded_scriptorium", 9),
            book(1),
            book(2),
            book(3),
            book(4),
            book(5),
            book(6),
            book(7),
            book(8),
            new Scene("seal_and_echo", () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(null);
                mc.player.setYRot(0);
                mc.player.setXRot(10);
                ClientCircles.start(new CastStarted(-6000, mc.player.getId(), OrbisArcana.id("fireball"),
                        mc.level.getGameTime(), 30, 15, Optional.empty(), -1, 2));
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp == null) return;
                    var pig = net.minecraft.world.entity.EntityTypes.PIG.create(sp.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                    if (pig == null) return;
                    pig.setPos(sp.getX(), sp.getY(), sp.getZ() + 4);
                    pig.setNoAi(true);
                    sp.level().addFreshEntity(pig);
                    for (int i = 0; i < 3; i++) com.orbisarcana.school.EchoService.onSpellHit(sp, com.orbisarcana.school.Schools.FIRE, pig, 9000 + i);
                });
            }));

    /** {@code -Dorbisarcana.devshot.only=a,b}: run only these scenes (setup scenes they need included). */
    private static final List<Scene> SCENES = filterScenes(System.getProperty("orbisarcana.devshot.only", ""));

    private static List<Scene> filterScenes(String only) {
        if (only.isBlank()) return ALL_SCENES;
        var names = java.util.Set.of(only.split(","));
        return ALL_SCENES.stream().filter(s -> names.contains(s.name())).toList();
    }

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

    /** The (survival layout) inventory full of the mod's items (texture check). */
    private static void showItems() {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;
            // survival: the creative player would get the creative screen; the next run resets it
            sp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            sp.getInventory().clearContent();
            var items = new java.util.ArrayList<net.minecraft.world.item.Item>();
            com.orbisarcana.registry.ModItems.FOCI.forEach(i -> items.add(i.get()));
            com.orbisarcana.registry.ModItems.ACCESSORIES.forEach(i -> items.add(i.get()));
            for (var i : java.util.List.of(com.orbisarcana.registry.ModItems.AETHER_SHARD, com.orbisarcana.registry.ModItems.AETHER_INK,
                    com.orbisarcana.registry.ModItems.MANA_POTION, com.orbisarcana.registry.ModItems.FIRST_SEALS, com.orbisarcana.registry.ModItems.SEAL_LIGHT,
                    com.orbisarcana.registry.ModItems.SEAL_DARK, com.orbisarcana.registry.ModItems.SEAL_ASTRAL)) items.add(i.get());
            items.add(com.orbisarcana.registry.ModBlocks.AETHER_ORE_ITEM.get());
            items.add(com.orbisarcana.registry.ModBlocks.RESONANCE_ALTAR_ITEM.get());
            for (int k = 0; k < items.size() && k < 36; k++) sp.getInventory().setItem(k, new net.minecraft.world.item.ItemStack(items.get(k)));
            sp.getInventory().setItem(35, com.orbisarcana.item.SpellScrollItem.of(OrbisArcana.id("fireball")));
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.orbisarcana.registry.ModItems.AMETHYST_STAFF.get()));
            mc.execute(() -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        });
    }

    /** A row of the mod's blocks ahead, at noon (texture check). */
    private static void showBlocks() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        mc.player.setYRot(0);
        mc.player.setYHeadRot(0);
        mc.player.setXRot(20);
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;
            server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withSuppressedOutput(), "time set noon");
            var row = java.util.List.of(com.orbisarcana.registry.ModBlocks.AETHER_ORE.get().defaultBlockState(),
                    com.orbisarcana.registry.ModBlocks.DEEPSLATE_AETHER_ORE.get().defaultBlockState(),
                    com.orbisarcana.registry.ModBlocks.RESONANCE_ALTAR.get().defaultBlockState(),
                    com.orbisarcana.registry.ModBlocks.ARCANE_SEAL.get().defaultBlockState(),
                    com.orbisarcana.registry.ModBlocks.GLYPH_RING.get().defaultBlockState().setValue(com.orbisarcana.block.GlyphRingBlock.GLYPH, 0),
                    com.orbisarcana.registry.ModBlocks.GLYPH_RING.get().defaultBlockState().setValue(com.orbisarcana.block.GlyphRingBlock.GLYPH, 1),
                    com.orbisarcana.registry.ModBlocks.GLYPH_RING.get().defaultBlockState().setValue(com.orbisarcana.block.GlyphRingBlock.GLYPH, 2),
                    com.orbisarcana.registry.ModBlocks.GLYPH_RING.get().defaultBlockState().setValue(com.orbisarcana.block.GlyphRingBlock.GLYPH, 3));
            var base = sp.blockPosition().offset(-4, 0, 4);
            for (int k = 0; k < row.size(); k++) sp.level().setBlockAndUpdate(base.offset(k, 0, 0), row.get(k));
        });
    }

    /** With Curios loaded (-PwithCurios): equips a fire ring and a mana amulet server-side and logs the effect. */
    private static void curiosCheck() {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null || !net.neoforged.fml.ModList.get().isLoaded("curios")) {
            OrbisArcana.LOGGER.info("[devshot] curios: not loaded");
            return;
        }
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;
            double mana = com.orbisarcana.mana.ManaService.maxMana(sp);
            double fire = com.orbisarcana.school.AttunementService.powerMult(sp, com.orbisarcana.school.Schools.FIRE);
            com.orbisarcana.compat.curios.CuriosCompat.equip(sp, new net.minecraft.world.item.ItemStack(com.orbisarcana.registry.ModItems.RING_OF_FLAME.get()),
                    new net.minecraft.world.item.ItemStack(com.orbisarcana.registry.ModItems.MANA_AMULET.get()));
            OrbisArcana.LOGGER.info("[devshot] curios: max mana {} -> {}, fire power {} -> {}", mana, com.orbisarcana.mana.ManaService.maxMana(sp),
                    fire, com.orbisarcana.school.AttunementService.powerMult(sp, com.orbisarcana.school.Schools.FIRE));
            mc.execute(() -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        });
    }

    /** Places a structure template a few blocks ahead (server side) and looks at it. */
    private static Scene structure(String name, int distance) {
        return new Scene(name, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(null);
            mc.player.setYRot(0);
            mc.player.setYHeadRot(0);
            mc.player.setXRot(12);
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (sp == null) return;
                sp.level().getStructureTemplateManager().get(OrbisArcana.id(name)).ifPresent(t -> {
                    var at = sp.blockPosition().offset(-t.getSize().getX() / 2, -1, distance);
                    t.placeInWorld(sp.level(), at, at, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), sp.getRandom(), 2);
                });
            });
        });
    }

    /** Page {@code page} of the "First Seals" book. */
    private static Scene book(int page) {
        return new Scene("book" + page, () -> {
            var screen = new net.minecraft.client.gui.screens.inventory.BookViewScreen(net.minecraft.client.gui.screens.inventory.BookViewScreen.BookAccess
                    .fromItem(new net.minecraft.world.item.ItemStack(com.orbisarcana.registry.ModItems.FIRST_SEALS.get())));
            Minecraft.getInstance().gui.setScreen(screen);
            screen.setPage(page - 1);
        });
    }

    /** Own first-person fireball circle at the given view angles (a local fake cast). */
    private static Scene ownCircle(String name, float yaw, float pitch) {
        return new Scene(name, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(null);
            ClientConfig.RENDER_MODE.set(ClientConfig.RenderMode.AUTO);
            mc.player.setYRot(yaw);
            mc.player.setYHeadRot(yaw);
            mc.player.setXRot(pitch);
            ClientCircles.start(new CastStarted(-5000 - name.hashCode() % 1000, mc.player.getId(), OrbisArcana.id("fireball"),
                    mc.level.getGameTime(), 30, 7, Optional.empty(), -1, 1));
        });
    }

    /** Server-side wind step cast at mastery 15 (two charges); the HUD should show the charges. */
    private static void castWindStep(boolean setup) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;
            var id = OrbisArcana.id("wind_step");
            if (setup) {
                KnowledgeService.setMastery(sp, id, 15);
                var loadout = sp.getData(com.orbisarcana.data.ModAttachments.LOADOUT);
                for (int slot = 0; slot < com.orbisarcana.progression.Loadout.SLOTS; slot++) {
                    if (loadout.get(0, slot).filter(id::equals).isPresent()) KnowledgeService.select(sp, 0, slot);
                }
            }
            com.orbisarcana.spell.CastService.request(sp, id);
        });
    }

    /** Places an altar ahead of the player and starts a school switch ritual (fresh attunement first). */
    private static void startRitual() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        mc.player.setYRot(0);
        mc.player.setXRot(35);
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;
            var altar = sp.blockPosition().south(2);
            sp.level().setBlockAndUpdate(altar, com.orbisarcana.registry.ModBlocks.RESONANCE_ALTAR.get().defaultBlockState());
            sp.getData(com.orbisarcana.data.ModAttachments.ATTUNEMENT).reset();
            var bind = com.orbisarcana.school.AltarService.Action.BIND;
            com.orbisarcana.school.AltarService.handle(sp, altar, bind, Optional.of(com.orbisarcana.school.Schools.FIRE));
            com.orbisarcana.school.AltarService.handle(sp, altar, bind, Optional.of(com.orbisarcana.school.Schools.AIR));
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
        // The first scene slot only resets the player, so respawns and game mode changes settle before any scene
        if (ticks == WARMUP_TICKS) resetPlayer();
        int step = ticks - WARMUP_TICKS - SCENE_TICKS;
        if (step < 0) return;
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

    /** Scenes may leave the saved dev world changed (survival, damage): start every run from a creative, healthy player. */
    private static void resetPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player.isDeadOrDying()) mc.player.respawn();
        mc.gui.setScreen(null);
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;
            sp.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            sp.setHealth(sp.getMaxHealth());
            sp.clearFire();
            sp.removeAllEffects();
            server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withSuppressedOutput(), "kill @e[type=#minecraft:undead,distance=..64]");
        });
    }

    private DevShot() {}
}
