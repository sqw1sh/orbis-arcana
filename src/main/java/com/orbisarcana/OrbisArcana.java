package com.orbisarcana;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.registry.ModAttributes;
import com.orbisarcana.registry.ModRegistries;
import com.orbisarcana.spell.SpellRegistry;

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
        ModRegistries.register(modEventBus);
        ModAttributes.register(modEventBus);
        ModAttachments.register(modEventBus);
        SpellRegistry.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SYNCED, OrbisConfig.SERVER_SPEC);
        LOGGER.info("Orbis Arcana loading");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
