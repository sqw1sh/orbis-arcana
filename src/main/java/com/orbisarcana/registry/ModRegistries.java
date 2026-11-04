package com.orbisarcana.registry;

import com.orbisarcana.OrbisArcana;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All deferred registers of the mod. Content classes add entries to these. */
public final class ModRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OrbisArcana.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OrbisArcana.MODID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, OrbisArcana.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OrbisArcana.MODID);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        CREATIVE_TABS.register(modBus);
    }

    private ModRegistries() {}
}
