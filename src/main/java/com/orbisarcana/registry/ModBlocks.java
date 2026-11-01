package com.orbisarcana.registry;

import com.orbisarcana.block.FrostWallBlock;
import com.orbisarcana.block.ResonanceAltarBlock;
import com.orbisarcana.block.ThornHedgeBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

public final class ModBlocks {
    public static final DeferredBlock<ResonanceAltarBlock> RESONANCE_ALTAR = ModRegistries.BLOCKS.registerBlock(
            "resonance_altar", ResonanceAltarBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(3.5f, 1200f).requiresCorrectToolForDrops().sound(SoundType.LODESTONE).lightLevel(s -> 7));

    /** Temporary Ice Bastion wall: no item, no drops. */
    public static final DeferredBlock<FrostWallBlock> FROST_WALL = ModRegistries.BLOCKS.registerBlock(
            "frost_wall", FrostWallBlock::new,
            p -> p.mapColor(MapColor.ICE).strength(1.5f, 6f).friction(0.98f).sound(SoundType.GLASS).noLootTable().pushReaction(PushReaction.IMMOVEABLE));

    /** Temporary Thorn Seed barrier: no item, no drops. */
    public static final DeferredBlock<ThornHedgeBlock> THORN_HEDGE = ModRegistries.BLOCKS.registerBlock(
            "thorn_hedge", ThornHedgeBlock::new,
            p -> p.mapColor(MapColor.PLANT).strength(0.6f, 1f).sound(SoundType.SWEET_BERRY_BUSH).noOcclusion().noLootTable().pushReaction(PushReaction.IMMOVEABLE));

    public static final DeferredItem<BlockItem> RESONANCE_ALTAR_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("resonance_altar", RESONANCE_ALTAR);

    /** Forces class loading so the deferred entries above are registered. */
    public static void init() {}

    private ModBlocks() {}
}
