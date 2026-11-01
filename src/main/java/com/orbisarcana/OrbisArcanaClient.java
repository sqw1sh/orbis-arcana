package com.orbisarcana;

import com.orbisarcana.block.ResonanceAltarBlock;
import com.orbisarcana.client.AltarScreen;
import com.orbisarcana.client.ClientConfig;

import net.minecraft.client.Minecraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client entrypoint; never loaded on a dedicated server. */
@Mod(value = OrbisArcana.MODID, dist = Dist.CLIENT)
public class OrbisArcanaClient {
    public OrbisArcanaClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        ResonanceAltarBlock.clientScreenOpener = pos -> Minecraft.getInstance().gui.setScreen(new AltarScreen(pos));
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
