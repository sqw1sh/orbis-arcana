package com.orbisarcana.core;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Talent tree of a school (§10.4): a free root passive plus two branches of two nodes. The second
 * node of a branch needs the first. Three points (resonance 3/6/9) for four nodes: you cannot take all.
 */
public final class TalentRules {
    public static final String ROOT = "root";
    public static final String A1 = "a1", A2 = "a2", B1 = "b1", B2 = "b2";
    public static final List<String> NODES = List.of(A1, A2, B1, B2);
    /** Aether Ink to reset a school's talents at the altar (prototype). */
    public static final int RESET_INK = 2;

    public enum Denial {
        NOT_DOMINANT,
        UNKNOWN_NODE,
        ALREADY_PICKED,
        NO_POINTS,
        NEEDS_FIRST_NODE;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static boolean rootActive(int resonance) {
        return resonance >= 1;
    }

    public static int pointsLeft(int resonance, Set<String> picked) {
        return Math.max(0, SchoolRules.talentPoints(resonance) - picked.size());
    }

    public static Optional<String> prerequisite(String node) {
        return switch (node) {
            case A2 -> Optional.of(A1);
            case B2 -> Optional.of(B1);
            default -> Optional.empty();
        };
    }

    public static Optional<Denial> check(boolean dominant, String node, Set<String> picked, int resonance) {
        if (!dominant) return Optional.of(Denial.NOT_DOMINANT);
        if (!NODES.contains(node)) return Optional.of(Denial.UNKNOWN_NODE);
        if (picked.contains(node)) return Optional.of(Denial.ALREADY_PICKED);
        if (pointsLeft(resonance, picked) <= 0) return Optional.of(Denial.NO_POINTS);
        if (prerequisite(node).filter(p -> !picked.contains(p)).isPresent()) return Optional.of(Denial.NEEDS_FIRST_NODE);
        return Optional.empty();
    }

    private TalentRules() {}
}
