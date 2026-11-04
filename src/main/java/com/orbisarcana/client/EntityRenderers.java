package com.orbisarcana.client;

import com.geckolib.renderer.GeoEntityRenderer;
import com.orbisarcana.entity.ShadowDecoy;
import com.orbisarcana.registry.ModEntities;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Renderers of the mod's creatures: GeckoLib models from art/specs (tools/specs/world.cjs). */
final class EntityRenderers {
    /** Shadow decoy: the hooded shadow, see-through, a little smaller than its model. */
    static final class Decoy extends GeoEntityRenderer<ShadowDecoy, EntityRenderState> {
        /** ARGB tint: the shadow lets about a third of the world through. */
        private static final int SHADE = 0xB0FFFFFF;

        Decoy(EntityRendererProvider.Context context) {
            super(context, ModEntities.SHADOW_DECOY.get());
            withScale(0.9f);
        }

        @Override
        public RenderType getRenderType(EntityRenderState state, Identifier texture) {
            return RenderTypes.entityTranslucent(texture);
        }

        @Override
        public int getRenderColor(ShadowDecoy animatable, Void data, float partialTick) {
            return SHADE;
        }
    }

    private EntityRenderers() {}
}
