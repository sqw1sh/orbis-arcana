package com.orbisarcana.client.circle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.ClientConfig;
import com.orbisarcana.compat.iris.IrisCompat;
import com.orbisarcana.client.circle.CircleDefinition.Layer;
import com.orbisarcana.core.ActiveCast.Outcome;
import com.orbisarcana.core.Rarity;
import com.orbisarcana.core.Targeting;
import com.orbisarcana.network.CastPayloads.CastStarted;
import com.orbisarcana.network.CastPayloads.RitualCircle;
import com.orbisarcana.school.AltarService;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.SpellDefinition;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Active magic circles on this client (§9). Created from {@code CastStarted}, ended by {@code CastEnded};
 * all animation is computed locally from the start tick and duration (§9.7).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class ClientCircles {
    private static final ContextKey<List<CircleRenderState>> RENDER_KEY = new ContextKey<>(OrbisArcana.id("circles"));

    private static final float APPEAR_TICKS = 3f;    // 0.15 s (§9.1)
    private static final float RELEASE_TICKS = 6f;
    private static final float BREAK_TICKS = 4f;     // <= 0.2 s
    private static final long ORPHAN_TIMEOUT = 200;  // drop circles whose CastEnded never arrived
    private static final double MAX_RADIUS = 6.0;
    /** Own first-person DIRECTIONAL circle: at most this share of the screen height (§9.3). */
    private static final double MAX_SCREEN_SHARE = 0.25;
    /**
     * Own first-person circle hangs this close to the eye and is shrunk to keep its on-screen size (D6).
     * Pushing it further away instead made it sink into terrain when looking down.
     */
    private static final double FIRST_PERSON_DISTANCE = 0.6;

    private static final class Circle {
        final CastStarted cast;
        final @Nullable SpellDefinition spell;
        long endTick = -1;
        @Nullable Outcome outcome;

        Circle(CastStarted cast, @Nullable SpellDefinition spell) {
            this.cast = cast;
            this.spell = spell;
        }
    }

    private static final List<Circle> CIRCLES = new ArrayList<>();

    public static void start(CastStarted msg) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        CIRCLES.removeIf(c -> c.cast.casterId() == msg.casterId() && c.cast.castId() >= 0); // keep demo circles
        CIRCLES.add(new Circle(msg, SpellRegistry.get(mc.level.registryAccess(), msg.spell()).orElse(null)));
    }

    public static void end(int castId, Outcome outcome) {
        var level = Minecraft.getInstance().level;
        for (Circle c : CIRCLES) {
            if (c.cast.castId() == castId && c.endTick < 0) {
                c.endTick = level == null ? c.cast.startTick() : level.getGameTime();
                c.outcome = outcome;
            }
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CIRCLES.clear();
    }

    /**
     * Debug stand (§15.8 {@code debug circles}): {@code count} local-only circles in a grid in front of
     * the player, one per rarity in turn, charging over 3 s and fading after ~13 s. Not sent to the server.
     */
    public static int spawnDemo(int count) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return 0;
        long now = mc.level.getGameTime();
        Vec3 origin = mc.player.position();
        Vec3 forward = new Vec3(mc.player.getLookAngle().x, 0, mc.player.getLookAngle().z).normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Rarity[] rarities = Rarity.values();
        int perRow = (int) Math.ceil(Math.sqrt(count));
        for (int i = 0; i < count; i++) {
            Rarity rarity = rarities[i % rarities.length];
            Identifier school = Schools.ALL.get(i % Schools.ALL.size());
            SpellDefinition spell = syntheticSpell(school, rarity, OrbisArcana.id("fireball"), rarity.circleBaseRadius * 1.4, 60);
            int row = i / perRow, col = i % perRow;
            Vec3 pos = origin.add(forward.scale(4 + row * 3.5)).add(side.scale((col - (perRow - 1) / 2.0) * 3.5));
            var start = new CastStarted(-1000 - i, mc.player.getId(), OrbisArcana.id("demo_" + rarity.id), now, 60,
                    1 + (i * 7) % 15, java.util.Optional.of(pos), -1);
            CIRCLES.add(new Circle(start, spell));
        }
        return count;
    }

    /** Altar ritual seal (§10.6): a large ground circle of the chosen school around the altar. */
    public static void startRitual(RitualCircle msg) {
        if (Minecraft.getInstance().level == null) return;
        Vec3 floor = Vec3.atBottomCenterOf(msg.altar());
        var start = new CastStarted(msg.circleId(), msg.casterId(), OrbisArcana.id("ritual"), msg.startTick(), msg.durationTicks(),
                15, java.util.Optional.of(floor), -1);
        CIRCLES.add(new Circle(start, syntheticSpell(msg.school(), Rarity.EPIC, OrbisArcana.id("ritual"),
                AltarService.RITUAL_RADIUS, msg.durationTicks())));
    }

    /** A GROUND "spell" used only to drive a circle that is not a real cast (demo stand, ritual seal). */
    private static SpellDefinition syntheticSpell(Identifier school, Rarity rarity, Identifier circle, double radius, int ticks) {
        return new SpellDefinition(school, rarity, OrbisArcana.id("zone"), Targeting.GROUND,
                new SpellDefinition.Cast(com.orbisarcana.core.CastMode.CHARGED, ticks, 0), 0, 0, "radius",
                java.util.Map.of("radius", radius), java.util.Map.of(),
                java.util.Optional.of(circle), java.util.Optional.empty(),
                com.orbisarcana.core.XpCategory.ATTACK, false, true);
    }

    // ---- extract (game state -> render state) ----

    @SubscribeEvent
    static void extract(ExtractLevelRenderStateEvent event) {
        var mc = Minecraft.getInstance();
        if (!ClientConfig.CIRCLES_ENABLED.getAsBoolean()) {
            event.getRenderState().setRenderData(RENDER_KEY, List.of());
            return;
        }
        var level = event.getLevel();
        float partial = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partial;
        Vec3 cam = event.getCamera().position();

        List<CircleRenderState> out = new ArrayList<>();
        Iterator<Circle> it = CIRCLES.iterator();
        while (it.hasNext()) {
            Circle c = it.next();
            CircleRenderState s = extractOne(mc, c, now, partial, cam);
            if (s == REMOVE) {
                it.remove();
            } else if (s != null) {
                out.add(s);
            }
        }
        int max = ClientConfig.MAX_CIRCLES.getAsInt();
        if (out.size() > max) {
            out.sort(Comparator.comparingDouble(s -> s.center().lengthSqr()));
            out = new ArrayList<>(out.subList(0, max));
        }
        if (effectiveMode() == ClientConfig.RenderMode.PARTICLES) {
            CircleParticles.emit(level, cam, out);
            out = List.of();
        }
        event.getRenderState().setRenderData(RENDER_KEY, out);
    }

    private static final CircleRenderState REMOVE = new CircleRenderState(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), true, 0);

    private static @Nullable CircleRenderState extractOne(Minecraft mc, Circle c, double now, float partial, Vec3 cam) {
        CastStarted cast = c.cast;
        double age = now - cast.startTick();
        if (c.endTick < 0 && age > cast.durationTicks() + ORPHAN_TIMEOUT) return REMOVE;
        double sinceEnd = c.endTick < 0 ? -1 : now - c.endTick;
        boolean released = c.outcome == Outcome.RELEASED;
        if (sinceEnd > (released ? RELEASE_TICKS : BREAK_TICKS)) return REMOVE;
        if (c.spell == null || mc.level == null) return null;

        Entity caster = mc.level.getEntity(cast.casterId());
        if (caster == null) return null;
        Targeting targeting = c.spell.targeting();
        boolean own = caster == mc.player;
        boolean firstPerson = own && mc.options.getCameraType().isFirstPerson();
        if (firstPerson && targeting == Targeting.DIRECTIONAL && (mc.player.isScoping() || mc.player.isUsingItem())) return null;
        // As particles, the own first-person circle would be a cloud right in front of the eyes
        if (firstPerson && targeting == Targeting.DIRECTIONAL && effectiveMode() == ClientConfig.RenderMode.PARTICLES) return null;

        float progress = cast.durationTicks() <= 0 ? 1f : (float) Math.min(1.0, Math.max(0.0, age / cast.durationTicks()));
        if (sinceEnd >= 0 && released) progress = 1f;
        double radius = radius(c, mc);

        // Placement (§9.2)
        Vec3 center;
        Vec3 normal;
        Vec3 look = caster.getViewVector(partial);
        Vec3[] planeAxes = null;
        switch (targeting) {
            case DIRECTIONAL -> {
                normal = look;
                Vec3 eye = caster.getEyePosition(partial);
                if (firstPerson) {
                    double tanHalfFov = Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
                    double dist = FIRST_PERSON_DISTANCE;
                    radius = Math.min(radius, MAX_SCREEN_SHARE * dist * tanHalfFov) * ClientConfig.OWN_SCALE.get();
                    // Screen axes from the camera yaw: stable even when looking straight down, where
                    // axes derived from the look vector alone stay fixed in the world and the circle slides
                    planeAxes = screenAxes(caster.getViewYRot(partial), look);
                    // Down-right towards the main hand, centre of the screen stays clear (§9.3)
                    center = eye.add(look.scale(dist)).add(planeAxes[0].scale(0.42 * dist * tanHalfFov)).add(planeAxes[1].scale(-0.32 * dist * tanHalfFov));
                } else {
                    center = eye.add(look.scale(0.9)).add(0, -0.25, 0);
                }
            }
            case GROUND -> {
                normal = new Vec3(0, 1, 0);
                center = cast.groundPoint().map(p -> p.add(0, 0.03, 0)).orElse(caster.getPosition(partial));
            }
            case TARGET -> {
                normal = new Vec3(0, 1, 0);
                Entity target = cast.targetId() >= 0 ? mc.level.getEntity(cast.targetId()) : caster;
                if (target == null) target = caster;
                center = target.getPosition(partial).add(0, 0.03, 0);
                radius = Math.max(radius, target.getBbWidth() * 0.75);
            }
            default -> { // SELF; dashes get a vertical circle behind the back
                if (c.spell.behavior().getPath().equals("dash")) {
                    Vec3 flat = new Vec3(look.x, 0, look.z);
                    normal = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
                    center = caster.getPosition(partial).add(0, 1.0, 0).subtract(normal.scale(0.6));
                } else {
                    normal = new Vec3(0, 1, 0);
                    center = caster.getPosition(partial).add(0, 0.03, 0);
                }
            }
        }

        Vec3 rel = center.subtract(cam);
        double dist = rel.length();
        if (dist > ClientConfig.HIDE_DISTANCE.getAsInt()) return null;

        // Animation (§9.1)
        float appear = (float) Math.min(1.0, age / APPEAR_TICKS);
        float scale = 1f - (1f - appear) * (1f - appear);
        float alpha = 0.85f * ClientConfig.OPACITY.get().floatValue();
        float flash = 0f;
        if (sinceEnd >= 0) {
            float t = (float) (sinceEnd / (released ? RELEASE_TICKS : BREAK_TICKS));
            if (released) {
                scale *= 1f + 0.2f * t;
                flash = 1f - t;
            } else {
                scale *= 1f - 0.12f * t;
            }
            alpha *= 1f - t;
        }
        if (firstPerson && targeting == Targeting.DIRECTIONAL) {
            alpha *= (0.4f + 0.3f * progress) * ClientConfig.OWN_OPACITY.get().floatValue(); // 35-60% (§9.3)
        }

        Vec3[] axes = planeAxes != null ? planeAxes : axes(normal);
        int schoolColor = Schools.color(c.spell.school());
        if (!released && sinceEnd >= 0) schoolColor = dim(schoolColor);
        Rarity rarity = c.spell.rarity();

        return new CircleRenderState(rel, axes[0], axes[1], (float) radius, scale, alpha, progress,
                (float) (now / 20.0), flash, schoolColor, rarity.color, rarity.circleLayers,
                layers(c), ClientConfig.RIM_ONLY.getAsBoolean() || dist > ClientConfig.RIM_ONLY_DISTANCE.getAsInt(),
                c.cast.spell().hashCode());
    }

    /** §9.4: base radius by rarity, +2% per mastery level; zones/targets use the effect radius. */
    private static double radius(Circle c, Minecraft mc) {
        SpellDefinition spell = c.spell;
        double r = spell.rarity().circleBaseRadius * (1 + 0.02 * (c.cast.mastery() - 1));
        if (spell.targeting() == Targeting.GROUND || spell.targeting() == Targeting.TARGET) {
            Double effect = spell.params().get("radius");
            if (effect != null) r = effect;
        }
        return Math.min(MAX_RADIUS, r);
    }

    private static List<Layer> layers(Circle c) {
        Identifier circleId = c.spell.circle().orElse(c.cast.spell());
        CircleDefinition def = CircleLoader.get(circleId);
        if (def.milestoneLayers().isEmpty()) return def.layers();
        List<Layer> all = new ArrayList<>(def.layers());
        def.milestoneLayers().forEach((level, extra) -> {
            if (c.cast.mastery() >= level) all.addAll(extra);
        });
        return all;
    }

    private static ClientConfig.RenderMode effectiveMode() {
        ClientConfig.RenderMode mode = ClientConfig.RENDER_MODE.get();
        if (mode == ClientConfig.RenderMode.AUTO) {
            boolean meshUnderShaders = IrisCompat.isPipelineAssigned();
            return IrisCompat.isShaderPackInUse() && !meshUnderShaders ? ClientConfig.RenderMode.PARTICLES : ClientConfig.RenderMode.MESH;
        }
        return mode;
    }

    /** Camera right/up for a view with the given yaw (degrees) and look vector: [right, up]. */
    private static Vec3[] screenAxes(float yawDeg, Vec3 look) {
        double yaw = Math.toRadians(yawDeg);
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = right.cross(look).normalize();
        return new Vec3[] {right, up};
    }

    /** Two unit axes spanning the plane perpendicular to {@code normal}: [right, up]. */
    private static Vec3[] axes(Vec3 normal) {
        Vec3 ref = Math.abs(normal.y) > 0.95 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 right = normal.cross(ref).normalize();
        Vec3 up = right.cross(normal).normalize();
        return new Vec3[] {right, up};
    }

    private static int dim(int rgb) {
        int r = (rgb >> 16 & 0xFF) / 2, g = (rgb >> 8 & 0xFF) / 2, b = (rgb & 0xFF) / 2;
        return r << 16 | g << 8 | b;
    }

    // ---- submit (render state -> geometry) ----

    @SubscribeEvent
    static void submit(SubmitCustomGeometryEvent event) {
        List<CircleRenderState> states = event.getLevelRenderState().getRenderData(RENDER_KEY);
        if (states == null || states.isEmpty()) return;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), CircleRenderTypes.CIRCLE, (pose, buffer) -> {
            CircleRenderer renderer = new CircleRenderer(pose, buffer);
            for (CircleRenderState s : states) renderer.draw(s);
        });
    }

    private ClientCircles() {}
}
