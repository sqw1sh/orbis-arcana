package com.orbisarcana.item;

import com.orbisarcana.school.Schools;
import java.util.Optional;
import java.util.function.Consumer;

import com.orbisarcana.core.Rarity;
import com.orbisarcana.progression.ScrollService;
import com.orbisarcana.registry.ModDataComponents;
import com.orbisarcana.registry.ModItems;
import com.orbisarcana.spell.SpellDefinition;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Spell scroll (§6): hold right-click ~2 s to learn the spell forever (the scroll is consumed);
 * releasing early keeps it. Shift + right-click dissolves a duplicate into mastery XP (§6.3).
 */
public class SpellScrollItem extends Item {
    public static final int READ_TICKS = 40;

    public SpellScrollItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Identifier spell) {
        ItemStack stack = new ItemStack(ModItems.SPELL_SCROLL.get());
        stack.set(ModDataComponents.SPELL.get(), spell);
        return stack;
    }

    public static Optional<Identifier> spell(ItemStack stack) {
        return Optional.ofNullable(stack.get(ModDataComponents.SPELL.get()));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Optional<Identifier> spell = spell(stack);
        if (spell.isEmpty()) return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer sp) ScrollService.dissolve(sp, stack, spell.get());
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer sp && !ScrollService.canStartReading(sp, spell.get())) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            spell(stack).ifPresent(spell -> ScrollService.learn(player, stack, spell));
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return READ_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPYGLASS;
    }

    @Override
    public Component getName(ItemStack stack) {
        return spell(stack)
                .map(id -> (Component) Component.translatable("item.orbisarcana.spell_scroll.named", spellName(id)))
                .orElseGet(() -> super.getName(stack));
    }

    public static Component spellName(Identifier spell) {
        return Component.translatable("spell." + spell.getNamespace() + "." + spell.getPath());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        HolderLookup.Provider registries = context.registries();
        Optional<Identifier> id = spell(stack);
        if (id.isEmpty() || registries == null) return;
        Optional<SpellDefinition> def = SpellRegistry.get(registries, id.get());
        if (def.isEmpty()) {
            out.accept(Component.translatable("tooltip.orbisarcana.scroll.unknown").withStyle(ChatFormatting.RED));
            return;
        }
        SpellDefinition d = def.get();
        Rarity rarity = d.rarity();
        out.accept(Component.translatable("rarity.orbisarcana." + rarity.id).withColor(rarity.color)
                .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable(Schools.nameKey(d.school())).withStyle(ChatFormatting.GRAY)));
        out.accept(Component.translatable("tooltip.orbisarcana.scroll.cast",
                Component.translatable("targeting.orbisarcana." + d.targeting().id()),
                Component.translatable("cast_mode.orbisarcana." + d.cast().mode().id()),
                (int) d.cost(), String.format("%.1f", d.cooldownTicks() / 20.0)).withStyle(ChatFormatting.DARK_GRAY));
        out.accept(Component.translatable("spell." + id.get().getNamespace() + "." + id.get().getPath() + ".desc").withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("tooltip.orbisarcana.scroll.hint").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
