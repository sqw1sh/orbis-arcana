package com.orbisarcana.registry;

import com.orbisarcana.block.FrostWallBlock;
import com.orbisarcana.block.GlyphRingBlock;
import com.orbisarcana.block.ResonanceAltarBlock;
import com.orbisarcana.block.ThornHedgeBlock;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
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

    /** Ruined Observatory puzzle (§12): turnable glyph rings and the seals they break. Both unbreakable. */
    public static final DeferredBlock<GlyphRingBlock> GLYPH_RING = ModRegistries.BLOCKS.registerBlock(
            "glyph_ring", GlyphRingBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(-1f, 3600000f).noLootTable().sound(SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 4));
    public static final DeferredBlock<Block> ARCANE_SEAL = ModRegistries.BLOCKS.registerSimpleBlock(
            "arcane_seal", p -> p.mapColor(MapColor.COLOR_PURPLE).strength(-1f, 3600000f).noLootTable().sound(SoundType.AMETHYST)
                    .lightLevel(s -> 10).pushReaction(PushReaction.IMMOVEABLE));

    /** Aether Crystal ore (§11.3): the basic magic resource in caves, drops shards and some XP. */
    public static final DeferredBlock<DropExperienceBlock> AETHER_ORE = ModRegistries.BLOCKS.registerBlock(
            "aether_crystal_ore", p -> new DropExperienceBlock(UniformInt.of(2, 5), p),
            p -> p.mapColor(MapColor.STONE).strength(3f, 3f).requiresCorrectToolForDrops().sound(SoundType.AMETHYST).lightLevel(s -> 3));
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_AETHER_ORE = ModRegistries.BLOCKS.registerBlock(
            "deepslate_aether_crystal_ore", p -> new DropExperienceBlock(UniformInt.of(2, 5), p),
            p -> p.mapColor(MapColor.DEEPSLATE).strength(4.5f, 3f).requiresCorrectToolForDrops().sound(SoundType.AMETHYST).lightLevel(s -> 3));
    public static final DeferredItem<BlockItem> AETHER_ORE_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("aether_crystal_ore", AETHER_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_AETHER_ORE_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("deepslate_aether_crystal_ore", DEEPSLATE_AETHER_ORE);

    /** Forces class loading so the deferred entries above are registered. */
    public static void init() {}

    private ModBlocks() {}
}
