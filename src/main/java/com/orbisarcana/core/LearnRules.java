package com.orbisarcana.core;

import java.util.Optional;

/** Scroll learning requirements by rarity (§6.2) and duplicate dissolving (§6.3). */
public final class LearnRules {
    /** Dissolving a duplicate gives this share of the next level's XP. */
    public static final double DISSOLVE_SHARE = 0.15;
    /** Dissolving never raises mastery above this level: milestones 10 and 15 need practice. */
    public static final int DISSOLVE_MAX_LEVEL = 9;

    public enum Denial {
        ALREADY_KNOWN,
        /** Epic: any spell of the school at mastery >= 5. */
        NEEDS_MASTERY_5,
        /** Legendary: mastery >= 10 in the school, or resonance >= 4. */
        NEEDS_MASTERY_10_OR_RESONANCE_4,
        /** Mythic and above: mastery >= 10 in the school and resonance >= 6 reached at some point. */
        NEEDS_MASTERY_10_AND_RESONANCE_6,
        /** Higher school (Light/Dark/Astral) without a Seal of Comprehension on the first spell. */
        NEEDS_SEAL;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * @param bestMasteryInSchool highest mastery among known spells of the same school (0 if none)
     * @param resonance           current resonance of the school
     * @param peakResonance       highest resonance ever reached in the school
     * @param needsSeal           higher school and no spell of it known yet
     * @param hasSeal             the player carries the school's seal
     */
    public record Context(Rarity rarity, boolean known, int bestMasteryInSchool, int resonance, int peakResonance,
                          boolean needsSeal, boolean hasSeal, boolean requirementsEnabled) {}

    public static Optional<Denial> check(Context c) {
        if (c.known()) return Optional.of(Denial.ALREADY_KNOWN);
        if (!c.requirementsEnabled()) return Optional.empty();
        switch (c.rarity()) {
            case COMMON, UNCOMMON, RARE -> {}
            case EPIC -> {
                if (c.bestMasteryInSchool() < 5) return Optional.of(Denial.NEEDS_MASTERY_5);
            }
            case LEGENDARY -> {
                if (c.bestMasteryInSchool() < 10 && c.resonance() < 4) return Optional.of(Denial.NEEDS_MASTERY_10_OR_RESONANCE_4);
            }
            default -> {
                if (c.bestMasteryInSchool() < 10 || c.peakResonance() < 6) return Optional.of(Denial.NEEDS_MASTERY_10_AND_RESONANCE_6);
            }
        }
        if (c.needsSeal() && !c.hasSeal()) return Optional.of(Denial.NEEDS_SEAL);
        return Optional.empty();
    }

    /** XP a dissolved duplicate grants at {@code level}, or 0 when the level is already at the dissolve cap. */
    public static double dissolveXp(int level, Rarity rarity) {
        if (level >= DISSOLVE_MAX_LEVEL) return 0;
        return DISSOLVE_SHARE * MasteryMath.xpRequired(level, rarity);
    }

    private LearnRules() {}
}
