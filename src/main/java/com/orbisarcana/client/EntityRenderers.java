package com.orbisarcana.client;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.entity.ShadowDecoy;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Renderers of the mod's creatures: vanilla models with the mod's own textures. */
final class EntityRenderers {
    /** Guardian Treant: the golem body in bark and moss. */
    static final class Treant extends IronGolemRenderer {
        private static final Identifier TEXTURE = OrbisArcana.id("textures/entity/guardian_treant.png");

        Treant(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public Identifier getTextureLocation(IronGolemRenderState state) {
            return TEXTURE;
        }
    }

    /** Shadow decoy: a see-through humanoid shadow. */
    static final class Decoy extends HumanoidMobRenderer<ShadowDecoy, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
        private static final Identifier TEXTURE = OrbisArcana.id("textures/entity/shadow_decoy.png");

        Decoy(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.3f);
        }

        @Override
        public HumanoidRenderState createRenderState() {
            return new HumanoidRenderState();
        }

        @Override
        public Identifier getTextureLocation(HumanoidRenderState state) {
            return TEXTURE;
        }

        @Override
        protected RenderType getRenderType(HumanoidRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
            return RenderTypes.entityTranslucent(TEXTURE);
        }
    }

    private EntityRenderers() {}
}
