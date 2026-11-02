package com.orbisarcana.client.anim;

import com.orbisarcana.OrbisArcana;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.enums.PlayState;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;

/**
 * Caster poses on Player Animation Library. Animations live in {@code assets/orbisarcana/player_animations/*.json}
 * (GeckoLib animation files exported from the player rig {@code art/animations/caster.bbmodel}; bones head, torso,
 * right_arm, left_arm, right_leg, left_leg) and play on one layer per player.
 */
public final class CasterAnimations {
    public static final Identifier LAYER = OrbisArcana.id("caster");
    /** Pipeline check: both arms raised for 2 s. */
    public static final Identifier CAST_TEST = anim("cast_test");

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1000,
                avatar -> new PlayerAnimationController(avatar, (controller, data, setter) -> PlayState.STOP));
    }

    /** Plays {@code animation} on the caster layer of {@code avatar}; false if it is unknown. */
    public static boolean play(Avatar avatar, Identifier animation) {
        if (!(PlayerAnimationAccess.getPlayerAnimationLayer(avatar, LAYER) instanceof PlayerAnimationController controller)) return false;
        return controller.triggerAnimation(animation);
    }

    /** PAL keys an animation by its name in the file, which Blockbench always prefixes with {@code animation.}. */
    private static Identifier anim(String name) {
        return OrbisArcana.id("animation." + name);
    }

    private CasterAnimations() {}
}
