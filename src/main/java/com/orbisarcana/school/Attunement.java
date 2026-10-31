package com.orbisarcana.school;

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

import net.minecraft.resources.Identifier;

/**
 * {@code attunement} attachment (§10, §15.3): the dominant school, per-school resonance and talents
 * (kept frozen while another school is dominant), Dissonance end and school switch cooldown.
 */
public final class Attunement {
    public static final int MAX_RESONANCE = 10;

    public static final MapCodec<Attunement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.optionalFieldOf("dominant").forGetter(a -> a.dominant),
            Codec.unboundedMap(Identifier.CODEC, SchoolProgress.CODEC).optionalFieldOf("schools", Map.of()).forGetter(a -> a.schools),
            Codec.LONG.optionalFieldOf("dissonance_until", 0L).forGetter(a -> a.dissonanceUntil),
            Codec.LONG.optionalFieldOf("next_switch_at", 0L).forGetter(a -> a.nextSwitchAt)
    ).apply(i, Attunement::new));

    private Optional<Identifier> dominant;
    private final Map<Identifier, SchoolProgress> schools;
    private long dissonanceUntil;
    private long nextSwitchAt;

    public Attunement() {
        this(Optional.empty(), Map.of(), 0L, 0L);
    }

    private Attunement(Optional<Identifier> dominant, Map<Identifier, SchoolProgress> schools, long dissonanceUntil, long nextSwitchAt) {
        this.dominant = dominant;
        this.schools = new HashMap<>(schools);
        this.dissonanceUntil = dissonanceUntil;
        this.nextSwitchAt = nextSwitchAt;
    }

    public Optional<Identifier> dominant() {
        return dominant;
    }

    /** Sets or clears the dominant school; other schools' progress stays frozen. */
    public void setDominant(Optional<Identifier> school) {
        this.dominant = school;
        school.ifPresent(this::school);
    }

    /** Progress of a school, created on first access. */
    public SchoolProgress school(Identifier school) {
        return schools.computeIfAbsent(school, s -> new SchoolProgress());
    }

    public Map<Identifier, SchoolProgress> schools() {
        return Collections.unmodifiableMap(schools);
    }

    public long dissonanceUntil() {
        return dissonanceUntil;
    }

    public void setDissonanceUntil(long gameTime) {
        this.dissonanceUntil = gameTime;
    }

    public long nextSwitchAt() {
        return nextSwitchAt;
    }

    public void setNextSwitchAt(long gameTime) {
        this.nextSwitchAt = gameTime;
    }

    public void reset() {
        dominant = Optional.empty();
        schools.clear();
        dissonanceUntil = 0;
        nextSwitchAt = 0;
    }

    /** Resonance and talents of one school. {@code peak} remembers the highest resonance ever reached (§6.2). */
    public static final class SchoolProgress {
        public static final Codec<SchoolProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, MAX_RESONANCE).optionalFieldOf("resonance", 0).forGetter(p -> p.resonance),
                Codec.DOUBLE.optionalFieldOf("xp", 0.0).forGetter(p -> p.xp),
                Codec.intRange(0, MAX_RESONANCE).optionalFieldOf("peak", 0).forGetter(p -> p.peak),
                Codec.STRING.listOf().optionalFieldOf("talents", List.of()).forGetter(p -> List.copyOf(p.talents))
        ).apply(i, SchoolProgress::new));

        private int resonance;
        private double xp;
        private int peak;
        private final Set<String> talents;

        public SchoolProgress() {
            this(0, 0.0, 0, List.of());
        }

        private SchoolProgress(int resonance, double xp, int peak, List<String> talents) {
            this.resonance = resonance;
            this.xp = xp;
            this.peak = Math.max(peak, resonance);
            this.talents = new HashSet<>(talents);
        }

        public int resonance() {
            return resonance;
        }

        public double xp() {
            return xp;
        }

        public int peak() {
            return peak;
        }

        public void setResonance(int resonance) {
            this.resonance = Math.max(0, Math.min(MAX_RESONANCE, resonance));
            this.xp = 0;
            this.peak = Math.max(peak, this.resonance);
        }

        public void setXp(double xp) {
            this.xp = Math.max(0, xp);
        }

        public Set<String> talents() {
            return talents;
        }
    }
}
