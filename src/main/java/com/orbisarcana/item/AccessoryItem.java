package com.orbisarcana.item;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.orbisarcana.client.model.GeoModels;
import com.orbisarcana.school.Schools;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A ring or amulet (§11.2). Worn only through Curios, which is optional: without it these items have no slot and are
 * neither crafted nor found. What they do is read by {@link Accessories}. Drawn as an animated GeckoLib model (roadmap V7).
 */
public class AccessoryItem extends Item implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public enum Kind {
        /** +10% power of {@link #school}. */
        SCHOOL_RING,
        MANA_AMULET,
        FLOW_AMULET,
        FOCUS_AMULET,
        /** Echoes charge 20% faster. */
        ECHO_RING,
        /** +10% cast speed. */
        SCRIBE_RING
    }

    private final Kind kind;
    private final @Nullable Identifier school;

    public AccessoryItem(Properties properties, Kind kind, @Nullable Identifier school) {
        super(properties);
        this.kind = kind;
        this.school = school;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("idle", test -> test.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        // only called on the client, when the item is first rendered
        consumer.accept(GeoModels.item(this));
    }

    public Kind kind() {
        return kind;
    }

    public @Nullable Identifier school() {
        return school;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        if (kind == Kind.SCHOOL_RING && school != null) {
            out.accept(Component.translatable("accessory.orbisarcana.school_ring",
                    Component.translatable(Schools.nameKey(school))).withStyle(ChatFormatting.BLUE));
        } else {
            out.accept(Component.translatable("accessory.orbisarcana." + kind.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.BLUE));
        }
        if (!net.neoforged.fml.ModList.get().isLoaded("curios")) {
            out.accept(Component.translatable("accessory.orbisarcana.needs_curios").withStyle(ChatFormatting.RED));
        }
    }
}
