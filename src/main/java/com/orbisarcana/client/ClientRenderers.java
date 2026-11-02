package com.orbisarcana.client;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.registry.ModEntities;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class ClientRenderers {
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Particles carry the visuals until S3
        event.registerEntityRenderer(ModEntities.SPELL_PROJECTILE.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.GUARDIAN_TREANT.get(), EntityRenderers.Treant::new);
        event.registerEntityRenderer(ModEntities.SHADOW_DECOY.get(), EntityRenderers.Decoy::new);
    }

    private ClientRenderers() {}
}
