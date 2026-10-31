package com.orbisarcana.registry;

import com.orbisarcana.OrbisArcana;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, OrbisArcana.MODID);

    /** Spell taught by a scroll. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> SPELL = COMPONENTS.registerComponentType(
            "spell", b -> b.persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC));

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }

    private ModDataComponents() {}
}
