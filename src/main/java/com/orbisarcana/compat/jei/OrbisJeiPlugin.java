package com.orbisarcana.compat.jei;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.registry.ModBlocks;
import com.orbisarcana.registry.ModDataComponents;
import com.orbisarcana.registry.ModItems;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * JEI integration (§17), loaded by JEI only: every spell gets its own scroll entry, and the items that are found or
 * used rather than crafted get information pages (scrolls, ink, shards, seals, the altar, accessories).
 */
@JeiPlugin
public final class OrbisJeiPlugin implements IModPlugin {
    private static IJeiRuntime runtime;

    @Override
    public Identifier getPluginUid() {
        return OrbisArcana.id("jei");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerFromDataComponentTypes(ModItems.SPELL_SCROLL.get(), ModDataComponents.SPELL.get());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        info(registration, ModItems.SPELL_SCROLL.get(), "spell_scroll");
        info(registration, ModItems.AETHER_INK.get(), "aether_ink");
        info(registration, ModItems.AETHER_SHARD.get(), "aether_shard");
        info(registration, ModBlocks.AETHER_ORE_ITEM.get(), "aether_shard");
        info(registration, ModBlocks.DEEPSLATE_AETHER_ORE_ITEM.get(), "aether_shard");
        info(registration, ModBlocks.RESONANCE_ALTAR_ITEM.get(), "resonance_altar");
        for (var seal : ModItems.SEALS.values()) info(registration, seal.get(), "seal");
        info(registration, ModItems.FIRST_SEALS.get(), "first_seals");
        for (var focus : ModItems.FOCI) info(registration, focus.get(), "focus");
        for (var accessory : ModItems.ACCESSORIES) info(registration, accessory.get(), "accessory");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Dev checks: sets JEI's search text. Call only when JEI is loaded. */
    public static void search(String text) {
        if (runtime != null) runtime.getIngredientFilter().setFilterText(text);
    }

    private static void info(IRecipeRegistration registration, ItemLike item, String key) {
        registration.addIngredientInfo(item, Component.translatable("jei.orbisarcana.info." + key));
    }
}
