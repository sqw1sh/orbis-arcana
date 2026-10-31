package com.orbisarcana.progression;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbisarcana.core.MasteryMath;

import net.minecraft.resources.Identifier;

/**
 * {@code knowledge} attachment (§15.3): learned spells with mastery, disabled milestones,
 * and spells of removed addons kept in {@code archived} until the addon comes back.
 */
public final class KnownSpells {
    public static final int SCHEMA = 1;

    public static final MapCodec<KnownSpells> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(k -> SCHEMA),
            Codec.unboundedMap(Identifier.CODEC, SpellProgress.CODEC).optionalFieldOf("spells", Map.of()).forGetter(k -> k.spells),
            Codec.unboundedMap(Identifier.CODEC, SpellProgress.CODEC).optionalFieldOf("archived", Map.of()).forGetter(k -> k.archived)
    ).apply(i, KnownSpells::new));

    private final Map<Identifier, SpellProgress> spells;
    private final Map<Identifier, SpellProgress> archived;

    public KnownSpells() {
        this(SCHEMA, Map.of(), Map.of());
    }

    private KnownSpells(int schema, Map<Identifier, SpellProgress> spells, Map<Identifier, SpellProgress> archived) {
        // schema > SCHEMA would need migrations; none exist yet
        this.spells = new HashMap<>(spells);
        this.archived = new HashMap<>(archived);
    }

    public boolean knows(Identifier spell) {
        return spells.containsKey(spell);
    }

    /** Learns the spell (restoring archived progress if any). Returns false if already known. */
    public boolean learn(Identifier spell) {
        if (knows(spell)) return false;
        SpellProgress restored = archived.remove(spell);
        spells.put(spell, restored != null ? restored : new SpellProgress());
        return true;
    }

    public boolean forget(Identifier spell) {
        return spells.remove(spell) != null;
    }

    public Optional<SpellProgress> progress(Identifier spell) {
        return Optional.ofNullable(spells.get(spell));
    }

    public Map<Identifier, SpellProgress> spells() {
        return Collections.unmodifiableMap(spells);
    }

    public Map<Identifier, SpellProgress> archived() {
        return Collections.unmodifiableMap(archived);
    }

    /** Moves spells that no longer exist into {@code archived}; returns how many were moved. */
    public int archiveUnknown(Set<Identifier> existing) {
        List<Identifier> missing = spells.keySet().stream().filter(id -> !existing.contains(id)).toList();
        missing.forEach(id -> archived.put(id, spells.remove(id)));
        return missing.size();
    }

    /** Mastery of one spell for one player. */
    public static final class SpellProgress {
        public static final Codec<SpellProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, MasteryMath.MAX_LEVEL).optionalFieldOf("level", 1).forGetter(p -> p.level),
                Codec.DOUBLE.optionalFieldOf("xp", 0.0).forGetter(p -> p.xp),
                Codec.INT.listOf().optionalFieldOf("disabled_milestones", List.of()).forGetter(p -> List.copyOf(p.disabledMilestones))
        ).apply(i, SpellProgress::new));

        private int level;
        private double xp;
        private final Set<Integer> disabledMilestones;

        public SpellProgress() {
            this(1, 0.0, List.of());
        }

        private SpellProgress(int level, double xp, List<Integer> disabledMilestones) {
            this.level = level;
            this.xp = xp;
            this.disabledMilestones = new HashSet<>(disabledMilestones);
        }

        public int level() {
            return level;
        }

        public double xp() {
            return xp;
        }

        /** Sets the level directly (admin/debug); progress inside the level is reset. */
        public void setLevel(int level) {
            this.level = Math.max(1, Math.min(MasteryMath.MAX_LEVEL, level));
            this.xp = 0;
        }

        public void setXp(double xp) {
            this.xp = Math.max(0, xp);
        }

        public boolean isMilestoneEnabled(int milestone) {
            return level >= milestone && !disabledMilestones.contains(milestone);
        }

        public void setMilestoneDisabled(int milestone, boolean disabled) {
            if (disabled) disabledMilestones.add(milestone);
            else disabledMilestones.remove(milestone);
        }
    }
}
