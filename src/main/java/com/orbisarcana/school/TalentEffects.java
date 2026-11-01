package com.orbisarcana.school;

import java.util.Set;

import com.orbisarcana.core.TalentRules;

import net.minecraft.server.level.ServerPlayer;

/**
 * Gameplay effects of talents and root passives (§10.5). Effects come school by school; nodes listed in
 * {@link #IMPLEMENTED} work, others are shown as "in development" in the grimoire.
 */
public final class TalentEffects {
    /** "school/node" keys whose effect is live. */
    public static final Set<String> IMPLEMENTED = Set.of("fire/root", "water/root");

    /** Fire root "Embers": burning from your spells lasts +30%. */
    public static final double EMBERS_IGNITE_MULT = 1.3;
    /** Water root "Living Wave": +10% healing. */
    public static final double LIVING_WAVE_HEAL_MULT = 1.1;

    public static boolean implemented(String school, String node) {
        return IMPLEMENTED.contains(school + "/" + node);
    }

    public static double igniteMult(ServerPlayer caster) {
        return AttunementService.hasTalent(caster, Schools.FIRE, TalentRules.ROOT) ? EMBERS_IGNITE_MULT : 1.0;
    }

    public static double healMult(ServerPlayer caster) {
        return AttunementService.hasTalent(caster, Schools.WATER, TalentRules.ROOT) ? LIVING_WAVE_HEAL_MULT : 1.0;
    }

    private TalentEffects() {}
}
