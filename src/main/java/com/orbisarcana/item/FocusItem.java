package com.orbisarcana.item;

import java.util.function.Consumer;

import com.orbisarcana.core.FocusRules;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A focus (§11.1): wand, staff or tome of a tier. Its bonuses are applied by {@link Foci} while held. */
public class FocusItem extends Item {
    private final FocusRules.Type type;
    private final int tier;

    public FocusItem(Properties properties, FocusRules.Type type, int tier) {
        super(properties);
        this.type = type;
        this.tier = tier;
    }

    public FocusRules.Type type() {
        return type;
    }

    public int tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        String key = "focus.orbisarcana." + type.name().toLowerCase(java.util.Locale.ROOT);
        out.accept(Component.translatable(key + ".hand").withStyle(ChatFormatting.GRAY));
        FocusRules.Stats s = FocusRules.stats(type, tier);
        line(out, "power", s.power());
        line(out, "cast_speed", s.castSpeed());
        line(out, "range", s.range());
        line(out, "stability", s.stability());
        line(out, "cost", s.cost());
        line(out, "mastery_xp", s.masteryXp());
    }

    private static void line(Consumer<Component> out, String stat, double delta) {
        if (delta == 0) return;
        // A lower cost is good, so it reads green like the bonuses
        boolean good = stat.equals("cost") ? delta < 0 : delta > 0;
        String sign = delta > 0 ? "+" : "−";
        out.accept(Component.translatable("focus.orbisarcana.stat." + stat, sign + Math.round(Math.abs(delta) * 100) + "%")
                .withStyle(good ? ChatFormatting.BLUE : ChatFormatting.RED));
    }
}
