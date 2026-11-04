package com.orbisarcana.client.anim;

import java.util.HashMap;
import java.util.Map;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.ClientConfig;
import com.orbisarcana.core.ActiveCast.Outcome;
import com.orbisarcana.core.CastMode;
import com.orbisarcana.core.Targeting;
import com.orbisarcana.network.CastPayloads.CastStarted;
import com.orbisarcana.spell.SpellRegistry;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;

/**
 * Caster poses on Player Animation Library (roadmap V5). Animations live in
 * {@code assets/orbisarcana/player_animations/caster.json}, exported from the player rig {@code art/animations/caster.bbmodel}
 * (bones head, torso, right_arm, left_arm, right_leg, left_leg; in the editor X+ raises an arm forward, sideways is
 * -Z for the left arm and +Z for the right). Per placement (directional, ground, target, self) there is a charge loop
 * and a release; channels have their own loop; an interrupted cast flinches. Casts of every player nearby animate
 * (driven by the CastStarted/CastEnded packets), with short fades between poses; in first person the animated arms
 * are shown (client option).
 */
public final class CasterAnimations {
    public static final Identifier LAYER = OrbisArcana.id("caster");
    /** Pipeline check: both arms raised for 2 s. */
    public static final Identifier CAST_TEST = anim("cast_test");
    public static final Identifier CHANNEL = anim("channel"), INTERRUPT = anim("interrupt");

    private static final int FADE_IN = 4, FADE_TO_RELEASE = 2;

    /** Cast id -> (caster entity id, spell, placement), so the end of a cast knows which release to play. */
    private record Running(int casterId, Identifier spell, Targeting targeting, boolean channel) {}

    /** Spells with a release pose of their own (tools/caster-poses.cjs); the others use their placement's release. */
    private static final Map<String, String> POSES = Map.ofEntries(
            Map.entry("flame_dash", "release_dash"), Map.entry("wind_step", "release_dash"), Map.entry("shadow_step", "release_step"),
            Map.entry("healing_dew", "release_heal"), Map.entry("heavens_judgment", "release_judgment"), Map.entry("granite_oath", "release_guard"),
            Map.entry("gust_dome", "release_dome"), Map.entry("discharge", "release_thrust"),
            // V10 fire pack: calling down the sky, flaring out around oneself, lashing and flashing forward, a guard
            Map.entry("meteor", "release_judgment"), Map.entry("fallen_sun", "release_judgment"), Map.entry("immolation", "release_dome"),
            Map.entry("heat_wave", "release_dome"), Map.entry("blazing_ring", "release_dome"), Map.entry("flame_lash", "release_thrust"),
            Map.entry("flare", "release_thrust"), Map.entry("ember_ward", "release_guard"));
    public static final Identifier TOGGLE_ON = anim("toggle_on");

    private static final Map<Integer, Running> RUNNING = new HashMap<>();

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1000, CasterController::new);
    }

    /** The caster layer controller: never chooses an animation itself, shows the animated arms in first person. */
    private static final class CasterController extends PlayerAnimationController {
        CasterController(Avatar avatar) {
            super(avatar, (controller, data, setter) -> PlayState.STOP);
            // a staff is two blocks long: on the animated arm it would fill the screen, so it keeps the vanilla first-person view
            this.firstPersonMode = c -> !ClientConfig.FIRST_PERSON_ANIMATIONS.get() ? FirstPersonMode.NONE
                    : avatar.getMainHandItem().getItem() instanceof com.orbisarcana.item.FocusItem focus
                            && focus.type() == com.orbisarcana.core.FocusRules.Type.STAFF ? FirstPersonMode.VANILLA : FirstPersonMode.THIRD_PERSON_MODEL;
            this.firstPersonConfiguration = c -> new FirstPersonConfiguration(true, true, true, true);
        }
    }

    /** Plays {@code animation} on the caster layer of {@code avatar}; false if it is unknown. */
    public static boolean play(Avatar avatar, Identifier animation) {
        return play(avatar, animation, 0);
    }

    private static boolean play(Avatar avatar, Identifier animation, int fadeTicks) {
        if (!(PlayerAnimationAccess.getPlayerAnimationLayer(avatar, LAYER) instanceof PlayerAnimationController controller)) return false;
        if (fadeTicks <= 0) return controller.triggerAnimation(animation);
        return controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeTicks, EasingType.EASE_IN_OUT_SINE), animation);
    }

    /** A cast began (any player in range): its charge or channel pose, or straight the release for instant casts. */
    public static void castStarted(CastStarted msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(msg.casterId()) instanceof Avatar avatar)) return;
        var def = SpellRegistry.get(mc.level.registryAccess(), msg.spell());
        if (def.isEmpty()) return;
        Targeting targeting = def.get().targeting();
        boolean channel = def.get().cast().mode() == CastMode.CHANNEL;
        if (msg.durationTicks() <= 0 && !channel) {
            playRelease(avatar, msg.spell(), targeting);
            return;
        }
        RUNNING.put(msg.castId(), new Running(msg.casterId(), msg.spell(), targeting, channel));
        play(avatar, channel ? CHANNEL : anim("charge_" + targeting.id()), FADE_IN);
    }

    /** A cast ended: release, or a flinch when it was interrupted; a channel that ran out just lets go. */
    public static void castEnded(int castId, Outcome outcome) {
        Running run = RUNNING.remove(castId);
        Minecraft mc = Minecraft.getInstance();
        if (run == null || mc.level == null) return;
        Entity entity = mc.level.getEntity(run.casterId());
        if (!(entity instanceof Avatar avatar)) return;
        if (outcome == Outcome.RELEASED) {
            if (run.channel()) play(avatar, release(Targeting.DIRECTIONAL), FADE_TO_RELEASE);
            else playRelease(avatar, run.spell(), run.targeting());
        }
        else play(avatar, INTERRUPT, FADE_TO_RELEASE);
    }

    /** The spell's own release pose if it has one, else its placement's. */
    private static void playRelease(Avatar avatar, Identifier spell, Targeting targeting) {
        String pose = POSES.get(spell.getPath());
        if (pose == null || !play(avatar, anim(pose), FADE_TO_RELEASE)) play(avatar, release(targeting), FADE_TO_RELEASE);
    }

    /** A toggle switched on by a player in range: a short gesture of raising the arms. */
    public static void toggled(int playerId, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (on && mc.level != null && mc.level.getEntity(playerId) instanceof Avatar avatar) play(avatar, TOGGLE_ON, FADE_TO_RELEASE);
    }

    private static Identifier release(Targeting targeting) {
        return anim("release_" + targeting.id());
    }

    /** PAL keys an animation by its name in the file, which Blockbench always prefixes with {@code animation.}. */
    private static Identifier anim(String name) {
        return OrbisArcana.id("animation." + name);
    }

    private CasterAnimations() {}
}
