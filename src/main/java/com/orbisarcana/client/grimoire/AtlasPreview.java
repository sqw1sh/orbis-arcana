package com.orbisarcana.client.grimoire;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.circle.ClientCircles;
import com.orbisarcana.core.Targeting;
import com.orbisarcana.spell.SpellDefinition;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

/**
 * The Atlas viewport: a small 3D scene rendered into the GUI (picture-in-picture) with the mage and the circle of
 * the spell where the cast puts it: in front of the chest (directional), under the feet (self), on the ground ahead
 * (ground, target). The mage model faces -z. The camera orbits around the scene; {@code scale} is pixels per block.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
final class AtlasPreview {
    record State(EntityRenderState mage, Identifier spellId, SpellDefinition spell, int mastery, float progress, float timeSec,
                 float flash, float cycle, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
                 @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
        State(EntityRenderState mage, Identifier spellId, SpellDefinition spell, int mastery, float progress, float timeSec, float flash,
              float cycle, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea) {
            this(mage, spellId, spell, mastery, progress, timeSec, flash, cycle, yaw, pitch, x0, y0, x1, y1, scale, scissorArea,
                    PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
        }
    }

    static final class Renderer extends PictureInPictureRenderer<State> {
        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected void renderToTexture(State s, PoseStack pose, SubmitNodeCollector collector) {
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            // GUI y grows down: flip, then orbit the camera (pitch, yaw) around a point at chest height
            pose.rotate(new Quaternionf().rotateZ((float) Math.PI));
            pose.rotate(new Quaternionf().rotateX((float) Math.toRadians(-s.pitch())));
            pose.rotate(new Quaternionf().rotateY((float) Math.toRadians(s.yaw())));
            boolean ahead = s.spell().targeting() == Targeting.GROUND || s.spell().targeting() == Targeting.TARGET;
            // the mage faces -z (towards the camera at yaw 0); ground circles lie ahead of them
            pose.translate(0, -0.9, ahead ? 1.2 : 0);

            Minecraft.getInstance().getEntityRenderDispatcher().submit(s.mage(), new CameraRenderState(), 0, 0, 0, pose, collector);

            Vec3 x = new Vec3(1, 0, 0), y = new Vec3(0, 1, 0), z = new Vec3(0, 0, 1);
            switch (s.spell().targeting()) {
                case DIRECTIONAL -> ClientCircles.submitPreview(pose, collector, s.spellId(), s.spell(), s.mastery(),
                        new Vec3(0, 1.25, -1.05), x, y, s.progress(), s.timeSec(), s.flash(), 0.95f, s.cycle());
                case SELF -> ClientCircles.submitPreview(pose, collector, s.spellId(), s.spell(), s.mastery(),
                        new Vec3(0, 0.02, 0), x, z, s.progress(), s.timeSec(), s.flash(), 0.95f, s.cycle());
                default -> ClientCircles.submitPreview(pose, collector, s.spellId(), s.spell(), s.mastery(),
                        new Vec3(0, 0.02, -2.6), x, z, s.progress(), s.timeSec(), s.flash(), 0.95f, s.cycle());
            }
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2.0F;
        }

        @Override
        protected String getTextureLabel() {
            return "orbisarcana_atlas";
        }
    }

    @SubscribeEvent
    static void register(RegisterPictureInPictureRenderersEvent event) {
        event.register(State.class, Renderer::new);
    }

    private AtlasPreview() {}
}
