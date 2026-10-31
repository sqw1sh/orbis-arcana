package com.orbisarcana.client.circle;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Magic circle geometry as data (§9.6), loaded from {@code assets/<ns>/orbisarcana/circle/<id>.json}.
 * Radii are relative: 1.0 is the circle radius computed from rarity, mastery and effect size (§9.4).
 */
public record CircleDefinition(List<Layer> layers, Map<Integer, List<Layer>> milestoneLayers) {
    public static final CircleDefinition FALLBACK = new CircleDefinition(
            List.of(Layer.of(LayerType.RING, 1.0), new Layer(LayerType.POLYGON, 0.75, 0.03, 4, 1, 25, false, 0.12, Optional.empty())),
            Map.of());

    public enum LayerType {
        /** Circle outline; with {@code progress} it fills along the arc as the cast charges. */
        RING,
        /** Regular polygon outline(s); {@code count} copies are rotated evenly. */
        POLYGON,
        /** Ring of procedural runes that light up one by one with progress. */
        GLYPH_RING,
        /** Small diamonds orbiting at {@code radius}. */
        SATELLITE;

        static final Codec<LayerType> CODEC = Codec.STRING.comapFlatMap(s -> {
            for (LayerType t : values()) {
                if (t.name().equalsIgnoreCase(s)) return DataResult.success(t);
            }
            return DataResult.error(() -> "Unknown circle layer type '" + s + "'");
        }, t -> t.name().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * One layer. Unused fields are ignored by layer types that do not need them.
     * {@code color} overrides the school colour (hex {@code "#RRGGBB"}).
     */
    public record Layer(LayerType type, double radius, double width, int sides, int count, double rotateSpeed,
                        boolean progress, double size, Optional<Integer> color) {
        private static final Codec<Integer> HEX_COLOR = Codec.STRING.comapFlatMap(s -> {
            try {
                return DataResult.success(Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16));
            } catch (NumberFormatException e) {
                return DataResult.error(() -> "Bad colour '" + s + "'");
            }
        }, c -> String.format("#%06X", c));

        public static final Codec<Layer> CODEC = RecordCodecBuilder.create(i -> i.group(
                LayerType.CODEC.fieldOf("type").forGetter(Layer::type),
                Codec.doubleRange(0, 4).optionalFieldOf("radius", 1.0).forGetter(Layer::radius),
                Codec.doubleRange(0, 1).optionalFieldOf("width", 0.04).forGetter(Layer::width),
                Codec.intRange(3, 32).optionalFieldOf("sides", 3).forGetter(Layer::sides),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Layer::count),
                Codec.DOUBLE.optionalFieldOf("rotate_speed", 0.0).forGetter(Layer::rotateSpeed),
                Codec.BOOL.optionalFieldOf("progress", false).forGetter(Layer::progress),
                Codec.doubleRange(0, 1).optionalFieldOf("size", 0.1).forGetter(Layer::size),
                HEX_COLOR.optionalFieldOf("color").forGetter(Layer::color)
        ).apply(i, Layer::new));

        static Layer of(LayerType type, double radius) {
            return new Layer(type, radius, 0.04, 3, 1, 0, true, 0.1, Optional.empty());
        }
    }

    private static final Codec<Integer> MILESTONE_KEY = Codec.STRING.comapFlatMap(s -> {
        try {
            int level = Integer.parseInt(s);
            if (level == 5 || level == 10 || level == 15) return DataResult.success(level);
        } catch (NumberFormatException ignored) {
        }
        return DataResult.error(() -> "Milestone key must be 5, 10 or 15, got '" + s + "'");
    }, String::valueOf);

    public static final Codec<CircleDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Layer.CODEC.listOf().fieldOf("layers").forGetter(CircleDefinition::layers),
            Codec.unboundedMap(MILESTONE_KEY, Layer.CODEC.listOf()).optionalFieldOf("milestone_layers", Map.of()).forGetter(CircleDefinition::milestoneLayers)
    ).apply(i, CircleDefinition::new));
}
