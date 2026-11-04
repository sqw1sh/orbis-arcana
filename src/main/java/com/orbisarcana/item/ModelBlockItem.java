package com.orbisarcana.item;

import java.util.function.Consumer;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;
import com.orbisarcana.client.model.GeoModels;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/** The item of a block drawn by GeckoLib: the block's own model and animations ({@code geckolib/models/block/<id>}). */
public class ModelBlockItem extends BlockItem implements ModelItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ModelBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        // only called on the client, when the item is first rendered
        consumer.accept(GeoModels.blockItem(this));
    }
}
