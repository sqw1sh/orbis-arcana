package com.orbisarcana.item;

import java.util.function.Consumer;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.orbisarcana.client.model.GeoModels;

import net.minecraft.world.item.Item;

/**
 * An item drawn as a GeckoLib model from {@code art/specs/<id>.json} (roadmap V7), looping its {@code animation.idle}.
 * Implementors only keep the instance cache ({@code GeckoLibUtil.createInstanceCache(this)}).
 */
public interface ModelItem extends GeoItem {
    RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.idle");

    @Override
    default void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("idle", test -> test.setAndContinue(IDLE)));
    }

    @Override
    default void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        // only called on the client, when the item is first rendered
        consumer.accept(GeoModels.item((Item & GeoAnimatable) this));
    }
}
