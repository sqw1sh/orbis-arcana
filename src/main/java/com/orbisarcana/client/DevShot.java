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
            new Scene("stock", () -> {
                // the stock effects of spell/Fx in two rows, each with the stock tint (dark stage: gallery_setup)
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(null);
                mc.player.setYRot(0);
                mc.player.setXRot(10);
            }, () -> {
                String[] names = {"mote", "twinkle", "spark", "arc", "smoke", "flame", "frost", "splash", "leaf", "debris", "shadow", "shockwave", "slash", "rune"};
                int[] tints = {0xFFF2C8, 0xFFFFFF, 0xA8D8FF, 0x9FD8FF, 0x9A9A9A, 0xFFFFFF, 0xBFEFFF, 0x6AB0FF, 0x7CD65A, 0x8A8076, 0x3A1E58, 0xFFFFFF, 0xE8F4FF, 0xC890FF};
                for (int i = 0; i < names.length; i++) {
                    Minecraft mc = Minecraft.getInstance();
                    var look = mc.player.getLookAngle();
                    var flat = new net.minecraft.world.phys.Vec3(look.x, 0, look.z).normalize();
                    var right = new net.minecraft.world.phys.Vec3(-flat.z, 0, flat.x);
                    var at = mc.player.getEyePosition().add(flat.scale(6)).add(right.scale((i % 7 - 3) * 1.4)).add(0, i < 7 ? 0.9 : -1.1, 0);
                    com.orbisarcana.client.vfx.VfxManager.spawn(OrbisArcana.id(names[i]), at, net.minecraft.world.phys.Vec3.ZERO, tints[i], 1f, -1,
                            new org.joml.Vector3f(0.35f, 0.35f, 0.35f), 2f);
                }
            }),
            new Scene("fireball", () -> {
                // a slow fireball crossing the view from right to left (flight effects), the impact effects on the left before the shot
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(null);
                mc.player.setYRot(0);
                mc.player.setXRot(8);
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp == null) return;
                    var p = com.orbisarcana.entity.SpellProjectile.create(sp.level(), sp, com.orbisarcana.school.Schools.FIRE, 0, 0, 0, 0, 400).look(1.6f);
                    p.setPos(sp.getX() - 3.5, sp.getEyeY() - 0.3, sp.getZ() + 6);
                    p.shoot(1, 0, 0, 0.07f, 0);
                    sp.level().addFreshEntity(p);
                });
            }, () -> {
                Minecraft mc = Minecraft.getInstance();
                var at = mc.player.position().add(4.5, 0.2, 8);
                for (String fx : new String[] {"fireball_flash", "fireball_blast", "fireball_tongues", "fireball_ring", "fireball_sparks", "fireball_column", "fireball_cinders"})
                    com.orbisarcana.client.vfx.VfxManager.spawn(OrbisArcana.id(fx), at, net.minecraft.world.phys.Vec3.ZERO, 0, 1.6f, -1);
            }),
            new Scene("signatures", () -> {
                // V8 signature effects: a Discharge bolt with its strike (left), an ice shard breaking (middle), a line of
                // Stone Spear spikes (right), all started on the server just before the shot
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(null);
                mc.player.setYRot(0);
                mc.player.setXRot(10);
            }, () -> {
                Minecraft mc = Minecraft.getInstance();
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp == null) return;
                    var level = sp.level();
                    var o = sp.position();
                    com.orbisarcana.spell.Arcs.draw(level, o.add(5, 2.2, 7), o.add(2.2, 0.9, 7.5));
                    com.orbisarcana.spell.Arcs.strike(level, o.add(2.2, 0.9, 7.5));
                    var ice = o.add(0, 1, 7);
                    for (String fx : new String[] {"ice_flash", "ice_shatter", "ice_puff"})
                        com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id(fx), ice, net.minecraft.world.phys.Vec3.ZERO, 0, 1f);
                    for (int i = 0; i < 4; i++) {
                        var at = o.add(-1.5 - i * 0.9, 0.7, 5 + i * 0.8);
                        com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("stone_spikes"), at, net.minecraft.world.phys.Vec3.ZERO, 0, 1f, 0.4f, 1, 0.4f, 1);
                        com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("earth_dust"), at.add(0, -0.5, 0), net.minecraft.world.phys.Vec3.ZERO, 0, 1f, 0.4f, 1, 0.4f, 1);
                    }
                });
            }),
            new Scene("signatures2", () -> {
                // V8 signature effects: a Tidal Stream jet sweeping right to left (left), its last wave (middle), a Thorn
                // Seed bursting (right)
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(null);
                mc.player.setYRot(0);
                mc.player.setXRot(10);
            }, () -> {
                Minecraft mc = Minecraft.getInstance();
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp == null) return;
                    var level = sp.level();
                    var o = sp.position();
                    var zero = net.minecraft.world.phys.Vec3.ZERO;
                    var dir = new net.minecraft.world.phys.Vec3(1, 0.05, 0.3).normalize();
                    for (String fx : new String[] {"water_jet", "water_spray"})
                        com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id(fx), o.add(1, 1.4, 6), dir, 0, 1f);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("water_wave"), o.add(0, 0.4, 8), zero, 0, 1f, 0.6f, 1, 0.6f, 1);
                    var seed = o.add(-3.5, 0.4, 7);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("bloom_flash"), seed.add(0, 0.1, 0), zero, 0, 1f);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("bloom_burst"), seed, zero, 0, 1f, 1, 1, 1, 1.5f);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("thorn_spikes"), seed.add(0, 0.15, 0), zero, 0, 1f, 1.4f, 1, 1.4f, 2.5f);
                });
            }),
            new Scene("signatures3", () -> {
                // V8 signature effects: a Star Spike beam crossing the view, the shadows rushing in on a Shadow Dart hit
                // (left), the Dawn Ray finale (right)
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(null);
                mc.player.setYRot(0);
                mc.player.setXRot(10);
            }, () -> {
                Minecraft mc = Minecraft.getInstance();
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp == null) return;
                    var level = sp.level();
                    var o = sp.position();
                    var zero = net.minecraft.world.phys.Vec3.ZERO;
                    var a = o.add(-4, 1.2, 4);
                    var beam = o.add(4, 1.6, 10).subtract(a);
                    for (String fx : new String[] {"star_core", "star_glow"})
                        com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id(fx), a.add(beam.scale(0.5)), beam, 0, (float) beam.length());
                    for (String fx : new String[] {"dusk_implode", "dusk_ring"})
                        com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id(fx), o.add(3, 1, 7), zero, 0, 1f);
                    for (String fx : new String[] {"dawn_burst", "dawn_ring"})
                        com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id(fx), o.add(-3, 1, 8), zero, 0, 1f);
                });
            }),
            new Scene("signatures4", () -> {
                // V8 batch A: Ash Spiral ring with a fire pillar (left), Frost Pattern with its freezing ring (middle),
                // Storm Anchor rune and a strike from the sky (right), Current Shell arcs and a flame dash stream (front)
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(null);
                mc.player.setYRot(0);
                mc.player.setXRot(18);
            }, () -> {
                Minecraft mc = Minecraft.getInstance();
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp == null) return;
                    var level = sp.level();
                    var o = sp.position();
                    var zero = net.minecraft.world.phys.Vec3.ZERO;
                    var ash = o.add(4, 0.1, 8);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("ash_ring"), ash, zero, 0, 1f, 2.5f, 1, 2.5f, 2);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("ash_flakes"), ash, zero, 0, 1f, 2, 1, 2, 2);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("fire_pillar"), ash.add(0, 0.3, 0), zero, 0, 1f);
                    var frost = o.add(0, 0.08, 9);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("frost_field"), frost, zero, 0, 1f, 2.5f, 1, 2.5f, 2);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("frost_mist"), frost.add(0, 0.2, 0), zero, 0, 1f, 2.5f, 1, 2.5f, 2);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("frost_ring"), frost, zero, 0, 1f);
                    var anchorAt = o.add(-4, 0.06, 8);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("anchor_rune"), anchorAt, zero, 0, 1f);
                    com.orbisarcana.spell.Arcs.draw(level, anchorAt.add(1, 9, 0.5), anchorAt);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("shell_arcs"), o.add(-1.5, 1, 4.5), zero, 0, 1f, 0.7f, 1.4f, 0.7f, 2);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("dash_flames"), o.add(1.5, 0.6, 4.5), zero, 0, 1f);
                });
            }),
            new Scene("signatures5", () -> {
                // V8 batch B: Heaven's Judgment column with its rune (left), Water Prison bubble and Gust Dome wind (middle),
                // Granite Oath plates and a Guardian Orbit star (right), Life Bloom petals and the aura ring (front)
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(null);
                mc.player.setYRot(0);
                mc.player.setXRot(12);
            }, () -> {
                Minecraft mc = Minecraft.getInstance();
                var server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (sp == null) return;
                    var level = sp.level();
                    var o = sp.position();
                    var zero = net.minecraft.world.phys.Vec3.ZERO;
                    java.util.function.BiConsumer<String, net.minecraft.world.phys.Vec3> at = (fx, p) -> com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id(fx), p, zero, 0, 1f);
                    var judge = o.add(4, 0, 9);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("judgment_rune"), judge.add(0, 0.06, 0), zero, 0, 1.5f);
                    var top = judge.add(0, 14, 0); var down = judge.subtract(top);
                    for (String fx : new String[] {"ray_core", "ray_glow"})
                        com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id(fx), top.add(down.scale(0.5)), down, 0, (float) down.length());
                    at.accept("dawn_burst", judge.add(0, 0.5, 0));
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("bubble"), o.add(0.5, 1.2, 8), zero, 0, 1.3f);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("dome_wind"), o.add(0.5, 0.2, 8), zero, 0, 1f, 2.5f, 2.5f, 2.5f, 1.4f);
                    var oath = o.add(-3.5, 0.6, 8);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("stone_guard"), oath, zero, 0, 1f, 1.1f, 1, 1.1f, 1);
                    at.accept("orbit_star", oath.add(0, 1.8, 0));
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("petals"), o.add(1.8, 0.2, 5), zero, 0, 1f, 1.5f, 1, 1.5f, 2);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("aura_ring"), o.add(-1.5, 0.06, 5), zero, 0, 1.5f);
                    com.orbisarcana.spell.Vfx.play(level, OrbisArcana.id("curse_rune"), o.add(-1.5, 2, 6), zero, 0, 1f);
                });
            }),
            new Scene("cast_anim", () -> fakeCast(true)),
            new Scene("cast_anim_fp", () -> fakeCast(false)),
            new Scene("vfx_cleanup", () -> {
                // a trail attached to an entity that never shows up must stop and vanish after its grace time
                Minecraft mc = Minecraft.getInstance();
                com.orbisarcana.client.vfx.VfxManager.spawn(OrbisArcana.id("fire_trail"), mc.player.getEyePosition().add(mc.player.getLookAngle().scale(3)),
                        net.minecraft.world.phys.Vec3.ZERO, 0, 1f, 987654);
            }, () -> OrbisArcana.LOGGER.info("[devshot] vfx particles alive: {}", com.orbisarcana.client.vfx.VfxManager.particleCount())),
            new Scene("stands_front", () -> holdStands(false)),
            new Scene("stands_side", () -> holdStands(true)),
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
                // items go into the hand at the start of the scene, so they are in the hand by the shot
                out.add(pose.startsWith("hold")
                        ? new Scene("pose_" + pose.replace(":", "_"), () -> { s.setup().run(); showPose(pose); }, () -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                            // hold_side: the body turned away from the camera (as far as the head allows), a near side view of the arms
                            if (pose.startsWith("hold_side:")) {
                                mc.player.setYBodyRot(mc.player.getYRot() - 70);
                                mc.player.yBodyRotO = mc.player.getYRot() - 70;
                            }
                        })
                        : new Scene("pose_" + pose, s.setup(), () -> showPose(pose)));
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
    /**
     * Four armor stands with arms holding an iron sword (the reference grip), the amethyst wand, staff and tome, seen from the front or from their right side:
     * stands use the same third-person hand transforms as players, and can be looked at from any side.
     */
    private static void holdStands(boolean side) {
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
            var level = sp.level();
            server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withSuppressedOutput(), "time set noon");
            // a tome in the main hand: a first-person staff would cover the stands
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(OrbisArcana.id("amethyst_tome"))));
            var box = sp.getBoundingBox().inflate(12);
            for (var old : level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, box)) old.discard();
            // a sword first: the foci are held the way it is
            String[] items = {"minecraft:iron_sword", "orbisarcana:amethyst_wand", "orbisarcana:amethyst_staff", "orbisarcana:amethyst_tome"};
            for (int i = 0; i < items.length; i++) {
                var stand = new net.minecraft.world.entity.decoration.ArmorStand(level, sp.getX() + (i - 1.5) * 1.3, sp.getY(), sp.getZ() + 4.2);
                stand.setShowArms(true);
                stand.setNoBasePlate(true);
                stand.setNoGravity(true);
                // arms like an idle player: the item arm raised 18 degrees forward (vanilla ArmPose.ITEM), the other down
                stand.setRightArmPose(new net.minecraft.core.Rotations(-18, 0, 0));
                stand.setLeftArmPose(new net.minecraft.core.Rotations(0, 0, 0));
                // front: facing the camera; side: facing west, so the right hand is towards the camera
                stand.setYRot(side ? 90 : 180);
                stand.setYBodyRot(side ? 90 : 180);
                stand.setYHeadRot(side ? 90 : 180);
                stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(items[i]))));
                level.addFreshEntity(stand);
            }
        });
    }

    /** The caster animation {@code name}, third person from the front. */
    private static void showPose(String name) {
        Minecraft mc = Minecraft.getInstance();
        mc.player.setXRot(0);
        mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        if (name.startsWith("hold")) {
            // "hold:<item>": that mod item in the main hand, no caster animation (display transforms check)
            var server = mc.getSingleplayerServer();
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(OrbisArcana.id(name.substring(name.indexOf(':') + 1)));
            if (server != null) server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (sp != null) sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(item));
            });
            return;
        }
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
            for (var old : sp.level().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, sp.getBoundingBox().inflate(24))) old.discard();
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
            // the blocks scene builds on the same stage: clear it, put the altar between the creatures, empty the hand
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(stage.offset(-6, 0, -2), stage.offset(6, 4, 10))) {
                sp.level().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            sp.level().setBlockAndUpdate(stage.offset(0, 0, 3), com.orbisarcana.registry.ModBlocks.RESONANCE_ALTAR.get().defaultBlockState());
            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            sp.teleportTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5);
            var treant = com.orbisarcana.registry.ModEntities.GUARDIAN_TREANT.get().create(sp.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            var decoy = com.orbisarcana.registry.ModEntities.SHADOW_DECOY.get().create(sp.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (treant == null || decoy == null) return;
            treant.snapTo(stage.getX() - 1.5, stage.getY(), stage.getZ() + 6, 180, 0);
            treant.setNoAi(true);
            treant.setYHeadRot(180);
            treant.setYBodyRot(180);
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
