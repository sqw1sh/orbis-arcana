package com.orbisarcana.spell;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * A spell assembled from parts (roadmap V9), for the {@code orbisarcana:composite} behaviour: a shape that finds who is
 * hit, effects applied to each of them, and the effects to show. The numbers stay in the spell's {@code params}, so
 * milestones and mastery scale them as for any spell: an effect reads its param by name (its default, or {@code param}).
 *
 * <pre>"compose": { "shape": "bolt|beam|cone|burst|nova|target|self|zone|aura|wall|meteor", "affects": "enemies|allies|self",
 *   "effects": [{ "type": "damage" }, { "type": "ignite" }, { "type": "slow", "param": "chill_ticks" }],
 *   "visuals": { "cast": [ids], "travel": [ids], "impact": [ids], "tint": 0xRRGGBB,
 *     "release_sound": id, "impact_sound": id, "apogee_travel": [ids], "apogee_impact": [ids], "chain": [ids] } }</pre>
 * Shape params: bolt — speed, range, count, spread_deg, pierce, chain, chain_range, explode_radius; beam — range,
 * pierce; cone — range, angle_deg; burst, nova — radius (+ ground_fire_ticks: burning ground left behind); zone (at the
 * ground point), aura (follows the caster) — radius, duration_ticks, pulse_ticks; wall (across the aim) — length, width,
 * height, duration_ticks, pulse_ticks; meteor — explode_radius, ground_fire_ticks. Lasting shapes play their travel
 * effects and apply the effects every pulse, the impact at the end. Channelled spells repeat the shape every pulse_ticks.
 * Effect params (defaults): damage, ignite_ticks, slow_ticks (+ slow_level), freeze_ticks, knockback, pull, lift,
 * weaken_ticks, blind_ticks, heal, shield, speed_ticks.
 */
public record SpellCompose(String shape, String affects, List<Effect> effects, Visuals visuals) {
    public record Effect(String type, Optional<String> param) {
        public static final Codec<Effect> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("type").forGetter(Effect::type),
                Codec.STRING.optionalFieldOf("param").forGetter(Effect::param)
        ).apply(i, Effect::new));

        /** The param this effect reads: its own, or the default named after the type. */
        public String paramName() {
            return param.orElseGet(() -> switch (type) {
                case "ignite" -> "ignite_ticks";
                case "slow" -> "slow_ticks";
                case "freeze" -> "freeze_ticks";
                case "weaken" -> "weaken_ticks";
                case "blind" -> "blind_ticks";
                case "speed" -> "speed_ticks";
                default -> type;
            });
        }
    }

    /**
     * Effects played at the hand, along the way (each bolt tick, the beam, the cone) and where it lands; tinted. The sounds
     * (sound event ids) replace the school's release and impact sounds; at mastery 15 (the apogee) the apogee effects are
     * played on top of the travel and impact ones. The chain effects draw a bolt's jump between two targets, pointing along
     * it and scaled to its length (without them, the travel effects are played at normal size).
     */
    public record Visuals(List<Identifier> cast, List<Identifier> travel, List<Identifier> impact, int tint,
                          Optional<Identifier> releaseSound, Optional<Identifier> impactSound, List<Identifier> apogeeTravel, List<Identifier> apogeeImpact,
                          List<Identifier> chain) {
        public static final Visuals NONE = new Visuals(List.of(), List.of(), List.of(), 0, Optional.empty(), Optional.empty(), List.of(), List.of(), List.of());
        public static final Codec<Visuals> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.listOf().optionalFieldOf("cast", List.of()).forGetter(Visuals::cast),
                Identifier.CODEC.listOf().optionalFieldOf("travel", List.of()).forGetter(Visuals::travel),
                Identifier.CODEC.listOf().optionalFieldOf("impact", List.of()).forGetter(Visuals::impact),
                Codec.INT.optionalFieldOf("tint", 0).forGetter(Visuals::tint),
                Identifier.CODEC.optionalFieldOf("release_sound").forGetter(Visuals::releaseSound),
                Identifier.CODEC.optionalFieldOf("impact_sound").forGetter(Visuals::impactSound),
                Identifier.CODEC.listOf().optionalFieldOf("apogee_travel", List.of()).forGetter(Visuals::apogeeTravel),
                Identifier.CODEC.listOf().optionalFieldOf("apogee_impact", List.of()).forGetter(Visuals::apogeeImpact),
                Identifier.CODEC.listOf().optionalFieldOf("chain", List.of()).forGetter(Visuals::chain)
        ).apply(i, Visuals::new));
    }

    public static final List<String> SHAPES = List.of("bolt", "beam", "cone", "burst", "nova", "target", "self", "zone", "aura", "wall", "meteor");

    public static final Codec<SpellCompose> CODEC = RecordCodecBuilder.<SpellCompose>create(i -> i.group(
            Codec.STRING.fieldOf("shape").forGetter(SpellCompose::shape),
            Codec.STRING.optionalFieldOf("affects", "enemies").forGetter(SpellCompose::affects),
            Effect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(SpellCompose::effects),
            Visuals.CODEC.optionalFieldOf("visuals", Visuals.NONE).forGetter(SpellCompose::visuals)
    ).apply(i, SpellCompose::new)).validate(c -> SHAPES.contains(c.shape())
            ? com.mojang.serialization.DataResult.success(c)
            : com.mojang.serialization.DataResult.error(() -> "Unknown compose shape '" + c.shape() + "', expected one of " + SHAPES));
}
