package com.orbisarcana.item;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.world.item.Item;

/** A plain item with a 3D model (aether shard, aether ink, seals). */
public class ArtItem extends Item implements ModelItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ArtItem(Properties properties) {
        super(properties);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
