package com.orbisarcana.client.model;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.GeoItemRenderer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * GeckoLib renderers of the mod's 3D items. Assets follow GeckoLib's defaults for an item id {@code <ns>:<id>}:
 * {@code geckolib/models/item/<id>.geo.json}, {@code geckolib/animations/item/<id>.animation.json},
 * {@code textures/item/<id>.png} (exported from {@code art/models/<id>.bbmodel}, see tools/art.cjs).
 */
public final class GeoModels {
    public static <T extends Item & GeoAnimatable> GeoRenderProvider item(T item) {
        return new GeoRenderProvider() {
            private GeoItemRenderer<T> renderer;

            @Override
            public GeoItemRenderer<T> getGeoItemRenderer() {
                if (renderer == null) renderer = new GeoItemRenderer<>(new DefaultedItemGeoModel<>(BuiltInRegistries.ITEM.getKey(item)));
                return renderer;
            }
        };
    }

    private GeoModels() {}
}
