package com.orbisarcana.client;

import com.orbisarcana.OrbisArcana;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.GeoEntityRenderer;
import com.orbisarcana.registry.ModBlocks;
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
        event.registerEntityRenderer(ModEntities.GUARDIAN_TREANT.get(), ctx -> new GeoEntityRenderer<>(ctx, ModEntities.GUARDIAN_TREANT.get()));
        event.registerEntityRenderer(ModEntities.SHADOW_DECOY.get(), EntityRenderers.Decoy::new);
        event.registerBlockEntityRenderer(ModBlocks.RESONANCE_ALTAR_ENTITY.get(), ctx -> new GeoBlockRenderer<>(ctx, ModBlocks.RESONANCE_ALTAR_ENTITY.get()));
    }

    private ClientRenderers() {}
}
