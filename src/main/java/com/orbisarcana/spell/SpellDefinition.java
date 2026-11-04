package com.orbisarcana.spell;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbisarcana.core.CastMode;
import com.orbisarcana.core.Rarity;
import com.orbisarcana.core.Targeting;
import com.orbisarcana.core.XpCategory;

import net.minecraft.resources.Identifier;

/**
 * A spell as data (§15.4), loaded from {@code data/<ns>/orbisarcana/spell/<id>.json}.
 * Numbers live in {@code params}; milestones (5/10/15) override params. Spells of the {@code composite} behaviour describe
 * their shape and effects in {@code compose} ({@link SpellCompose}).
 */
public record SpellDefinition(
        Identifier school,
        Rarity rarity,
        Identifier behavior,
        Targeting targeting,
        Cast cast,
        double cost,
        int cooldownTicks,
        String primaryStat,
        Map<String, Double> params,
        Map<Integer, Map<String, Double>> milestones,
        Optional<Identifier> circle,
        Optional<Identifier> icon,
        XpCategory xpCategory,
        boolean allowSelf,
        boolean enabled,
        Optional<SpellCompose> compose) {

    public static final int[] MILESTONE_LEVELS = {5, 10, 15};

    /** Cast timing: total ticks, and the minimum hold for CHARGED spells. */
    public record Cast(CastMode mode, int ticks, int minTicks) {
        public static final Codec<Cast> CODEC = RecordCodecBuilder.<Cast>create(i -> i.group(
                enumCodec(CastMode.values(), CastMode::id).fieldOf("mode").forGetter(Cast::mode),
                Codec.intRange(0, 20 * 60).optionalFieldOf("ticks", 0).forGetter(Cast::ticks),
                Codec.intRange(0, 20 * 60).optionalFieldOf("min_ticks", 0).forGetter(Cast::minTicks)
        ).apply(i, Cast::new)).validate(c -> c.minTicks() > c.ticks()
                ? DataResult.error(() -> "min_ticks (" + c.minTicks() + ") > ticks (" + c.ticks() + ")")
                : DataResult.success(c));
    }

    private static final Codec<Rarity> RARITY_CODEC = Identifier.CODEC.comapFlatMap(id -> {
        for (Rarity r : Rarity.values()) {
            if (r.id.equals(id.getPath())) return DataResult.success(r);
        }
        return DataResult.error(() -> "Unknown rarity " + id + " (rarities are fixed, §16)");
    }, r -> Identifier.fromNamespaceAndPath("orbisarcana", r.id));

    private static final Codec<Map<String, Double>> PARAMS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);

    private static final Codec<Integer> MILESTONE_KEY = Codec.STRING.comapFlatMap(s -> {
        try {
            int level = Integer.parseInt(s);
            if (level == 5 || level == 10 || level == 15) return DataResult.success(level);
        } catch (NumberFormatException ignored) {
        }
        return DataResult.error(() -> "Milestone key must be 5, 10 or 15, got '" + s + "'");
    }, String::valueOf);

    private static final Codec<Map<String, Double>> MILESTONE_CODEC = RecordCodecBuilder.create(i -> i.group(
            PARAMS_CODEC.optionalFieldOf("params", Map.of()).forGetter(Function.identity())
    ).apply(i, Function.identity()));

    public static final Codec<SpellDefinition> CODEC = RecordCodecBuilder.<SpellDefinition>create(i -> i.group(
            Identifier.CODEC.fieldOf("school").forGetter(SpellDefinition::school),
            RARITY_CODEC.fieldOf("rarity").forGetter(SpellDefinition::rarity),
            Identifier.CODEC.fieldOf("behavior").forGetter(SpellDefinition::behavior),
            enumCodec(Targeting.values(), Targeting::id).fieldOf("targeting").forGetter(SpellDefinition::targeting),
            Cast.CODEC.fieldOf("cast").forGetter(SpellDefinition::cast),
            Codec.doubleRange(0, 10000).fieldOf("cost").forGetter(SpellDefinition::cost),
            Codec.intRange(0, 20 * 60 * 60).optionalFieldOf("cooldown_ticks", 0).forGetter(SpellDefinition::cooldownTicks),
            Codec.STRING.optionalFieldOf("primary_stat", "damage").forGetter(SpellDefinition::primaryStat),
            PARAMS_CODEC.optionalFieldOf("params", Map.of()).forGetter(SpellDefinition::params),
            Codec.unboundedMap(MILESTONE_KEY, MILESTONE_CODEC).optionalFieldOf("milestones", Map.of()).forGetter(SpellDefinition::milestones),
            Identifier.CODEC.optionalFieldOf("circle").forGetter(SpellDefinition::circle),
            Identifier.CODEC.optionalFieldOf("icon").forGetter(SpellDefinition::icon),
            enumCodec(XpCategory.values(), XpCategory::id).optionalFieldOf("xp_category", XpCategory.ATTACK).forGetter(SpellDefinition::xpCategory),
            Codec.BOOL.optionalFieldOf("allow_self", false).forGetter(SpellDefinition::allowSelf),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(SpellDefinition::enabled),
            SpellCompose.CODEC.optionalFieldOf("compose").forGetter(SpellDefinition::compose)
    ).apply(i, SpellDefinition::new)).validate(SpellDefinition::validate);

    /** Milestones may only override params the spell declares (§15.4). */
    private static DataResult<SpellDefinition> validate(SpellDefinition def) {
        for (var e : def.milestones.entrySet()) {
            for (String key : e.getValue().keySet()) {
                if (!def.params.containsKey(key)) {
                    return DataResult.error(() -> "Milestone " + e.getKey() + " sets param '" + key + "' that is not declared in params");
                }
            }
        }
        if (!def.params.containsKey(def.primaryStat)) {
            return DataResult.error(() -> "primary_stat '" + def.primaryStat + "' is not declared in params");
        }
        return DataResult.success(def);
    }

    /** Params with all milestones up to {@code masteryLevel} applied, skipping ones the player disabled. */
    public Map<String, Double> paramsAt(int masteryLevel, IntPredicate milestoneDisabled) {
        Map<String, Double> out = new HashMap<>(params);
        for (int level : MILESTONE_LEVELS) {
            if (masteryLevel >= level && !milestoneDisabled.test(level)) {
                out.putAll(milestones.getOrDefault(level, Map.of()));
            }
        }
        return out;
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(E[] values, Function<E, String> id) {
        return Codec.STRING.comapFlatMap(s -> {
            for (E v : values) {
                if (id.apply(v).equals(s.toLowerCase(Locale.ROOT))) return DataResult.success(v);
            }
            return DataResult.error(() -> "Unknown value '" + s + "'");
        }, id);
    }
}
