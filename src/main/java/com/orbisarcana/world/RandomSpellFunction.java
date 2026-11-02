package com.orbisarcana.world;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.Rarity;
import com.orbisarcana.registry.ModDataComponents;
import com.orbisarcana.spell.SpellDefinition;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Loot function {@code orbisarcana:random_spell} (§12): writes a random enabled spell into a scroll. Filters:
 * {@code rarities} (ids, required) and {@code schools} (optional). Weighted by rarity, rarer spells come less often.
 * Used by chest loot, mob drops and villager trades alike; an empty pool leaves the item as it is.
 */
public final class RandomSpellFunction extends LootItemConditionalFunction {
    public static final MapCodec<RandomSpellFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
            .and(i.group(
                    Codec.STRING.listOf().fieldOf("rarities").forGetter(f -> f.rarities),
                    Identifier.CODEC.listOf().optionalFieldOf("schools", List.of()).forGetter(f -> f.schools)))
            .apply(i, RandomSpellFunction::new));

    private static final DeferredRegister<MapCodec<? extends LootItemFunction>> FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, OrbisArcana.MODID);

    static {
        FUNCTIONS.register("random_spell", () -> CODEC);
    }

    private final List<String> rarities;
    private final List<Identifier> schools;

    private RandomSpellFunction(Optional<net.minecraft.core.Holder<LootItemCondition>> condition, List<String> rarities, List<Identifier> schools) {
        super(condition);
        this.rarities = rarities;
        this.schools = schools;
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
    }

    @Override
    public MapCodec<RandomSpellFunction> codec() {
        return CODEC;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        var registries = context.getLevel().registryAccess();
        List<Identifier> pool = SpellRegistry.ids(registries).stream().sorted().filter(id -> {
            Optional<SpellDefinition> def = SpellRegistry.get(registries, id);
            return def.isPresent() && def.get().enabled() && rarities.contains(def.get().rarity().id)
                    && (schools.isEmpty() || schools.contains(def.get().school()));
        }).toList();
        if (pool.isEmpty()) return stack;
        // Weight 1 / rarity index: common 1, uncommon 1/2, rare 1/3...
        double total = pool.stream().mapToDouble(id -> weight(registries, id)).sum();
        double roll = context.getRandom().nextDouble() * total;
        for (Identifier id : pool) {
            roll -= weight(registries, id);
            if (roll <= 0) {
                stack.set(ModDataComponents.SPELL.get(), id);
                return stack;
            }
        }
        stack.set(ModDataComponents.SPELL.get(), pool.getLast());
        return stack;
    }

    private static double weight(net.minecraft.core.HolderLookup.Provider registries, Identifier id) {
        return 1.0 / (1 + SpellRegistry.get(registries, id).map(d -> d.rarity().ordinal()).orElse(0));
    }

    /** Rarity ids of {@link Rarity}, for building filters in code. */
    public static List<String> ids(Rarity... rarities) {
        return java.util.Arrays.stream(rarities).map(r -> r.id).toList();
    }
}
