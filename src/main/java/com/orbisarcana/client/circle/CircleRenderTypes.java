package com.orbisarcana.client.circle;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.orbisarcana.OrbisArcana;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * Render type for magic circles (§9.7): position+colour quads, additive blending, depth-tested
 * but no depth write, double-sided. Reuses the vanilla lightning shader.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class CircleRenderTypes {
    public static final RenderPipeline CIRCLE_PIPELINE = RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
            .withLocation(OrbisArcana.id("pipeline/magic_circle"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();

    public static final RenderType CIRCLE = RenderType.create("orbisarcana_magic_circle",
            RenderSetup.builder(CIRCLE_PIPELINE).sortOnUpload().createRenderSetup());

    @SubscribeEvent
    static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(CIRCLE_PIPELINE);
    }

    private CircleRenderTypes() {}
}
