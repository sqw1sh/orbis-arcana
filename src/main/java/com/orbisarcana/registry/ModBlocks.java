package com.orbisarcana.registry;

import com.orbisarcana.block.ResonanceAltarBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

public final class ModBlocks {
    public static final DeferredBlock<ResonanceAltarBlock> RESONANCE_ALTAR = ModRegistries.BLOCKS.registerBlock(
            "resonance_altar", ResonanceAltarBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(3.5f, 1200f).requiresCorrectToolForDrops().sound(SoundType.LODESTONE).lightLevel(s -> 7));

    public static final DeferredItem<BlockItem> RESONANCE_ALTAR_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("resonance_altar", RESONANCE_ALTAR);

    /** Forces class loading so the deferred entries above are registered. */
    public static void init() {}

    private ModBlocks() {}
}
