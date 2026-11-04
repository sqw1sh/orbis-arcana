package com.orbisarcana.item;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.world.item.WrittenBookItem;

/** "First Seals" (§4): the tutorial written book, with a 3D model. */
public class FirstSealsItem extends WrittenBookItem implements ModelItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public FirstSealsItem(Properties properties) {
        super(properties);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
