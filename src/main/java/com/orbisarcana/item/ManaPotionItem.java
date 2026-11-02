package com.orbisarcana.item;

import java.util.function.Consumer;

import com.orbisarcana.mana.ManaService;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Mana Potion (§11.4): drinking restores {@code mana} at once. Drink time and the bottle left behind come from components. */
public class ManaPotionItem extends Item {
    private final double mana;

    public ManaPotionItem(Properties properties, double mana) {
        super(properties);
        this.mana = mana;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer p) ManaService.setMana(p, Math.min(ManaService.maxMana(p), ManaService.mana(p) + mana));
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.orbisarcana.mana_potion.tip", (int) mana).withStyle(ChatFormatting.BLUE));
    }
}
