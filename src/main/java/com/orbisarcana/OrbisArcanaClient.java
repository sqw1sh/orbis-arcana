package com.orbisarcana;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client entrypoint; never loaded on a dedicated server. */
@Mod(value = OrbisArcana.MODID, dist = Dist.CLIENT)
public class OrbisArcanaClient {
    public OrbisArcanaClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
