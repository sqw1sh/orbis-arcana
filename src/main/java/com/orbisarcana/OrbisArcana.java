package com.orbisarcana;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.registry.ModAttributes;
import com.orbisarcana.registry.ModDataComponents;
import com.orbisarcana.registry.ModBlocks;
import com.orbisarcana.registry.ModItems;
import com.orbisarcana.registry.ModRegistries;
import com.orbisarcana.spell.SpellRegistry;
import com.orbisarcana.spell.behavior.SpellBehaviors;
import com.orbisarcana.registry.ModEntities;
import com.orbisarcana.gametest.OrbisGameTests;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(OrbisArcana.MODID)
public class OrbisArcana {
    public static final String MODID = "orbisarcana";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OrbisArcana(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.init();
        ModBlocks.init();
        ModRegistries.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModAttributes.register(modEventBus);
        com.orbisarcana.world.RandomSpellFunction.register(modEventBus);
        // Curios is optional: its classes are only loaded when it is present (§11.2, §17)
        if (net.neoforged.fml.ModList.get().isLoaded("curios")) com.orbisarcana.compat.curios.CuriosCompat.init();
        ModAttachments.register(modEventBus);
        SpellRegistry.register(modEventBus);
        SpellBehaviors.register(modEventBus);
        com.orbisarcana.school.Schools.register(modEventBus);
        com.orbisarcana.api.OrbisArcanaAPI.Holder.set(new ApiImpl());
        ModEntities.register(modEventBus);
        OrbisGameTests.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SYNCED, OrbisConfig.SERVER_SPEC);
        LOGGER.info("Orbis Arcana loading");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
