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

    /** {@code beforeShot} runs a few ticks before the screenshot, for short-lived HUD states. */
    private record Scene(String name, Runnable setup, @org.jspecify.annotations.Nullable Runnable beforeShot) {
        Scene(String name, Runnable setup) {
            this(name, setup, null);
        }
    }

    private static final List<Scene> ALL_SCENES = List.of(
            circles(ClientConfig.RenderMode.AUTO),
            circles(ClientConfig.RenderMode.MESH),
            circles(ClientConfig.RenderMode.PARTICLES),
            new Scene("gallery_setup", DevShot::galleryStage),
            new Scene("gallery", () -> {
                // circles of every rarity on a dark stage at night, seen from above at an angle
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(null);
                mc.player.setYRot(0);
                mc.player.setXRot(38);
                ClientCircles.spawnDemo(8);
            }),
            new Scene("vfx", () -> {
                // the looping fire trail is running by the shot; the bursts start just before it
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(null);
                mc.player.setYRot(0);
                mc.player.setXRot(20);
                vfxAt("fire_trail", -1.5, 0, 0);
            }, () -> {
                vfxAt("fire_burst", 0, 0, 0);
                vfxAt("fire_sparks", 0, 0, 0);
                vfxAt("smoke_puff", 0, 0, 0);
                vfxAt("arcane_sparkle", 1.6, 0, 0x7A8CFF);
                vfxAt("arcane_sparkle", 3.0, 0, 0x6BD66B);
            }),
            new Scene("cast_anim", () -> fakeCast(true)),
            new Scene("cast_anim_fp", () -> fakeCast(false)),
            new Scene("radial", () -> {
                learnTestSpells();
                Minecraft.getInstance().gui.setScreen(new RadialMenuScreen(true));
            }),
            new Scene("grimoire", () -> Minecraft.getInstance().gui.setScreen(new com.orbisarcana.client.grimoire.GrimoireScreen(com.orbisarcana.client.grimoire.GrimoireScreen.Tab.SPELLS))),
            new Scene("atlas", () -> Minecraft.getInstance().gui.setScreen(new com.orbisarcana.client.grimoire.GrimoireScreen(com.orbisarcana.client.grimoire.GrimoireScreen.Tab.ATLAS))),
            new Scene("overview", () -> Minecraft.getInstance().gui.setScreen(new com.orbisarcana.client.grimoire.GrimoireScreen(com.orbisarcana.client.grimoire.GrimoireScreen.Tab.OVERVIEW))),
            ownCircle("own_down_yaw0", 0, 88),
            ownCircle("own_down_yaw120", 120, 88),
            ownCircle("own_ahead", 45, 10),
            new Scene("hud_one_charge", () -> castWindStep(true)),
            new Scene("hud_no_charges", () -> castWindStep(false)),
            new Scene("hud_cast", () -> {}, () -> {
                // client-side only: a fireball half charged and a denial chip, to see the cast bar and the hint
                ClientCastState.setOwnCast(new ClientCastState.OwnCast(-1, OrbisArcana.id("fireball"), ClientCastState.gameTime() - 20, 40));
                ClientCastState.deny(com.orbisarcana.core.CastDenial.NOT_ENOUGH_MANA);
            }),
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
            new Scene("schools", () -> Minecraft.getInstance().gui.setScreen(new com.orbisarcana.client.grimoire.GrimoireScreen(com.orbisarcana.client.grimoire.GrimoireScreen.Tab.SCHOOLS))),
            new Scene("talents", () -> Minecraft.getInstance().gui.setScreen(new com.orbisarcana.client.grimoire.GrimoireScreen(com.orbisarcana.client.grimoire.GrimoireScreen.Tab.TALENTS))),
            new Scene("curios", DevShot::curiosCheck),
            new Scene("items", DevShot::showItems),
            new Scene("blocks", DevShot::showBlocks),
            new Scene("creatures", DevShot::showCreatures),
            new Scene("geckolib", DevShot::showGeckoLib),
            new Scene("pose", () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(null);
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack().withSuppressedOutput(), "time set noon"));
                mc.player.setYRot(0);
                mc.player.setXRot(0);
            }, () -> showPose("cast_test")),
            new Scene("jei", DevShot::showJei),
            new Scene("jade", DevShot::showJade),
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
        // "pose" expands to one scene per name in -Dorbisarcana.devshot.pose=a,b (screenshots devshot_pose_<name>.png)
        var out = new java.util.ArrayList<Scene>();
        for (Scene s : ALL_SCENES) {
            if (!names.contains(s.name())) continue;
            if (!s.name().equals("pose")) {
                out.add(s);
                continue;
            }
            for (String pose : System.getProperty("orbisarcana.devshot.pose", "cast_test").split(",")) {
                out.add(new Scene("pose_" + pose, s.setup(), () -> showPose(pose)));
            }
        }
        return out;
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

    /** The GeckoLib debug orb (art/models/debug_orb.bbmodel) in hand, first person: model, texture, idle animation. */
    private static void showGeckoLib() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        mc.player.setXRot(10);
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;
            sp.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.orbisarcana.registry.ModItems.DEBUG_ORB.get()));
            sp.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(com.orbisarcana.registry.ModItems.DEBUG_ORB.get()));
        });
    }

    /** Player Animation Library: the test pose from player_animations/caster.json, third person from the front. */
    /** The caster animation {@code name}, third person from the front. */
    private static void showPose(String name) {
        Minecraft mc = Minecraft.getInstance();
        mc.player.setXRot(0);
        mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        OrbisArcana.LOGGER.info("[devshot] player animations: {}", com.zigythebird.playeranim.animation.PlayerAnimResources.getModAnimations(OrbisArcana.MODID).keySet());
        OrbisArcana.LOGGER.info("[devshot] pose {} started: {}", name, com.orbisarcana.client.anim.CasterAnimations.play(mc.player, OrbisArcana.id("animation." + name)));
    }

    /** With -PwithCompat: the inventory with JEI filtered to the mod. */
    private static void showJei() {
        showItems();
        Minecraft.getInstance().execute(() -> {
            if (net.neoforged.fml.ModList.get().isLoaded("jei")) com.orbisarcana.compat.jei.OrbisJeiPlugin.search("@orbisarcana");
        });
    }

    /** With -PwithCompat: a resonance altar under the crosshair, for Jade's tooltip. */
    private static void showJade() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        mc.player.setYRot(0);
        mc.player.setYHeadRot(0);
        mc.player.setXRot(35);
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;
            sp.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var at = net.minecraft.core.BlockPos.containing(sp.getX(), 150, sp.getZ());
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(at.offset(-3, -1, -1), at.offset(3, -1, 5))) {
                sp.level().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.MOSS_BLOCK.defaultBlockState());
            }
            sp.level().setBlockAndUpdate(at.offset(0, 0, 2), com.orbisarcana.registry.ModBlocks.RESONANCE_ALTAR.get().defaultBlockState());
            sp.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            com.orbisarcana.school.AttunementService.bind(sp, com.orbisarcana.school.Schools.FIRE);
        });
    }

    /** A Fireball cast start as the packet would announce it (charge pose and circle), seen from the front or first person. */
    private static void fakeCast(boolean front) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        mc.player.setXRot(front ? 0 : 10);
        if (front) mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        var msg = new com.orbisarcana.network.CastPayloads.CastStarted(-77, mc.player.getId(), OrbisArcana.id("fireball"),
                mc.level.getGameTime(), 200, 15, java.util.Optional.empty(), -1, 0);
        ClientCircles.start(msg);
        com.orbisarcana.client.anim.CasterAnimations.castStarted(msg);
    }

    /** Plays a VFX effect locally, {@code side} blocks to the right of a point 4 blocks ahead at eye height. */
    private static void vfxAt(String effect, double side, double up, int tint) {
        Minecraft mc = Minecraft.getInstance();
        var look = mc.player.getLookAngle();
        var flat = new net.minecraft.world.phys.Vec3(look.x, 0, look.z).normalize();
        var right = new net.minecraft.world.phys.Vec3(-flat.z, 0, flat.x);
        var at = mc.player.getEyePosition().add(flat.scale(4)).add(right.scale(side)).add(0, up - 0.6, 0);
        com.orbisarcana.client.vfx.VfxManager.spawn(OrbisArcana.id(effect), at, net.minecraft.world.phys.Vec3.ZERO, tint, 1f, -1);
    }

    /** A dark 21x21 stage at y 150, night: the magic circle gallery is shot over it. */
    private static void galleryStage() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;
            server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withSuppressedOutput(), "time set midnight");
            sp.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var stage = net.minecraft.core.BlockPos.containing(sp.getX(), 150, sp.getZ());
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(stage.offset(-10, -1, -4), stage.offset(10, -1, 18))) {
                sp.level().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(stage.offset(-10, 0, -4), stage.offset(10, 4, 18))) {
                sp.level().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            sp.teleportTo(stage.getX() + 0.5, stage.getY() + 3, stage.getZ() + 0.5);
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
        });
    }

    /** The Guardian Treant and a shadow decoy ahead, at noon (model and texture check). */
    private static void showCreatures() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        mc.player.setYRot(0);
        mc.player.setYHeadRot(0);
        mc.player.setXRot(5);
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;
            server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withSuppressedOutput(), "time set noon");
            // a clean stage in the sky: the dev world below is dug up by older scenes
            var stage = net.minecraft.core.BlockPos.containing(sp.getX(), 150, sp.getZ());
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(stage.offset(-6, -1, -2), stage.offset(6, -1, 10))) {
                sp.level().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.MOSS_BLOCK.defaultBlockState());
            }
            sp.teleportTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5);
            var treant = com.orbisarcana.registry.ModEntities.GUARDIAN_TREANT.get().create(sp.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            var decoy = com.orbisarcana.registry.ModEntities.SHADOW_DECOY.get().create(sp.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (treant == null || decoy == null) return;
            treant.snapTo(stage.getX() - 1.5, stage.getY(), stage.getZ() + 6, 180, 0);
            treant.setNoAi(true);
            decoy.snapTo(stage.getX() + 2, stage.getY(), stage.getZ() + 4, 180, 0);
            decoy.setYHeadRot(180);
            decoy.setYBodyRot(180);
            sp.level().addFreshEntity(treant);
            sp.level().addFreshEntity(decoy);
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
        mc.gui.setScreen(null);
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
                Screenshot.grab(mc.gameDirectory, "devshot_waiting.png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
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
            if (!SCENES.get(scene).name().startsWith("pose") && !SCENES.get(scene).name().equals("cast_anim")) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            SCENES.get(scene).setup().run();
        } else if (step % SCENE_TICKS == SCENE_TICKS - 8 && SCENES.get(scene).beforeShot() != null) {
            SCENES.get(scene).beforeShot().run();
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
