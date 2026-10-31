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

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener(ModAttributes::addToPlayer);
    }

    private static void addToPlayer(EntityAttributeModificationEvent event) {
        event.add(EntityTypes.PLAYER, MAX_MANA);
        event.add(EntityTypes.PLAYER, MANA_REGEN);
    }

    private ModAttributes() {}
}
