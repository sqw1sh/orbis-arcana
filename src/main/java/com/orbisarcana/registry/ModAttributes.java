package com.orbisarcana.registry;

import com.orbisarcana.OrbisArcana;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Magic attributes (§8.1, §15.7). Plain attributes so other mods can read and modify them. */
public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, OrbisArcana.MODID);

    public static final DeferredHolder<Attribute, Attribute> MAX_MANA = ATTRIBUTES.register("max_mana",
            () -> new RangedAttribute("attribute.orbisarcana.max_mana", 100.0, 0.0, 10000.0).setSyncable(true));

    /** Multiplier for mana regeneration. */
    public static final DeferredHolder<Attribute, Attribute> MANA_REGEN = ATTRIBUTES.register("mana_regen",
            () -> new RangedAttribute("attribute.orbisarcana.mana_regen", 1.0, 0.0, 100.0).setSyncable(true));

    /** Multiplier for spell damage, healing and shields (foci, accessories). */
    public static final DeferredHolder<Attribute, Attribute> SPELL_POWER = ATTRIBUTES.register("spell_power",
            () -> new RangedAttribute("attribute.orbisarcana.spell_power", 1.0, 0.0, 10.0).setSyncable(true));

    /** Cast speed: cast times are divided by it. */
    public static final DeferredHolder<Attribute, Attribute> CAST_SPEED = ATTRIBUTES.register("cast_speed",
            () -> new RangedAttribute("attribute.orbisarcana.cast_speed", 1.0, 0.1, 10.0).setSyncable(true));

    /** Multiplier for spell range. */
    public static final DeferredHolder<Attribute, Attribute> SPELL_RANGE = ATTRIBUTES.register("spell_range",
            () -> new RangedAttribute("attribute.orbisarcana.spell_range", 1.0, 0.1, 10.0).setSyncable(true));

    /** Taken off the chance that a heavy hit interrupts a cast. */
    public static final DeferredHolder<Attribute, Attribute> CAST_STABILITY = ATTRIBUTES.register("cast_stability",
            () -> new RangedAttribute("attribute.orbisarcana.cast_stability", 0.0, 0.0, 1.0).setSyncable(true));

    /** Multiplier for spell mana cost. */
    public static final DeferredHolder<Attribute, Attribute> SPELL_COST = ATTRIBUTES.register("spell_cost",
            () -> new RangedAttribute("attribute.orbisarcana.spell_cost", 1.0, 0.1, 10.0).setSyncable(true));

    /** Multiplier for mastery XP. */
    public static final DeferredHolder<Attribute, Attribute> MASTERY_XP = ATTRIBUTES.register("mastery_xp",
            () -> new RangedAttribute("attribute.orbisarcana.mastery_xp", 1.0, 0.0, 10.0).setSyncable(true));

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener(ModAttributes::addToPlayer);
    }

    private static void addToPlayer(EntityAttributeModificationEvent event) {
        event.add(EntityTypes.PLAYER, MAX_MANA);
        event.add(EntityTypes.PLAYER, MANA_REGEN);
        for (var a : java.util.List.of(SPELL_POWER, CAST_SPEED, SPELL_RANGE, CAST_STABILITY, SPELL_COST, MASTERY_XP)) event.add(EntityTypes.PLAYER, a);
    }

    private ModAttributes() {}
}
