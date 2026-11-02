package com.orbisarcana.client.vfx;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.orbisarcana.OrbisArcana;
import com.orbisarcana.compat.iris.IrisCompat;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * VFX pipelines: textured billboards (position_tex_color, full bright), depth-tested without depth write, double-sided;
 * {@code particles_add} draws additive (fire, magic), {@code particles_blend} translucent (smoke). One render type per
 * texture and blend mode. Under Iris both use the TEXTURED program, like the circles.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class VfxRenderTypes {
    private static RenderPipeline pipeline(String name, BlendFunction blend) {
        return RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withVertexShader("core/position_tex_color")
                .withFragmentShader("core/position_tex_color")
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withLocation(OrbisArcana.id("pipeline/" + name))
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
                .withCull(false)
                .build();
    }

    static final RenderPipeline ADDITIVE = pipeline("vfx_additive", BlendFunction.LIGHTNING);
    static final RenderPipeline BLEND = pipeline("vfx_blend", BlendFunction.TRANSLUCENT);

    private static final Map<Identifier, RenderType[]> TYPES = new HashMap<>();

    static RenderType get(Identifier texture, boolean additive) {
        RenderType[] pair = TYPES.computeIfAbsent(texture, t -> new RenderType[] {
                RenderType.create("orbisarcana_vfx_add", RenderSetup.builder(ADDITIVE).withTexture("Sampler0", t).createRenderSetup()),
                RenderType.create("orbisarcana_vfx_blend", RenderSetup.builder(BLEND).withTexture("Sampler0", t).sortOnUpload().createRenderSetup())});
        return pair[additive ? 0 : 1];
    }

    @SubscribeEvent
    static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(ADDITIVE);
        event.registerPipeline(BLEND);
    }

    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            IrisCompat.assignProgram(ADDITIVE, "TEXTURED");
            IrisCompat.assignProgram(BLEND, "TEXTURED");
        });
    }

    private VfxRenderTypes() {}
}
