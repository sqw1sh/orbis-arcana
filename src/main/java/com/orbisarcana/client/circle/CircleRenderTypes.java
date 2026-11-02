package com.orbisarcana.client.circle;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.orbisarcana.OrbisArcana;
import com.orbisarcana.compat.iris.IrisCompat;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * Render type for magic circles (§9.7, roadmap V3): textured quads from the circle atlas ({@link CircleAtlas}), glowing
 * (additive by alpha, full bright, no lighting), depth-tested but no depth write, double-sided. Uses the vanilla
 * position_tex_color shader (texture x vertex colour, as the End sky); under Iris the pack's TEXTURED program draws it,
 * which keeps the vertex colour and the fine lines (BEACON_BEAM bloomed them away, BLOCK format lost the colour).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class CircleRenderTypes {
    public static final RenderPipeline CIRCLE_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withLocation(OrbisArcana.id("pipeline/magic_circle"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();

    /** No sorting: additive blending is order-independent, and sorting tens of thousands of vertices per frame was the main cost. */
    public static final RenderType CIRCLE = RenderType.create("orbisarcana_magic_circle",
            RenderSetup.builder(CIRCLE_PIPELINE).withTexture("Sampler0", CircleAtlas.TEXTURE).createRenderSetup());

    @SubscribeEvent
    static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(CIRCLE_PIPELINE);
    }

    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> IrisCompat.assignProgram(CIRCLE_PIPELINE, "TEXTURED"));
    }

    private CircleRenderTypes() {}
}
