package com.orbisarcana.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.util.GeckoLibUtil;
import com.orbisarcana.item.ModelItem;
import com.orbisarcana.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The altar's block entity: holds no data, only draws the GeckoLib model (floating crystal, circling runes). */
public class ResonanceAltarBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ResonanceAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.RESONANCE_ALTAR_ENTITY.get(), pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("idle", test -> test.setAndContinue(ModelItem.IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
