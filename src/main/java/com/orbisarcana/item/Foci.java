package com.orbisarcana.item;

import java.util.List;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.FocusRules;
import com.orbisarcana.registry.ModAttributes;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Applies held foci (§11.1) as transient modifiers of the magic attributes, so the cast code, Curios accessories and
 * other mods all go through the same attributes. Re-checked a few times a second on the server.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class Foci {
    private static final Identifier MAIN = OrbisArcana.id("focus_main_hand");
    private static final Identifier OFF = OrbisArcana.id("focus_off_hand");
    private static final int CHECK_TICKS = 5;

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % CHECK_TICKS == 0) update(p);
    }

    /** Re-applies the foci in the player's hands (also used by tests). */
    public static void update(Player p) {
        ItemStack main = p.getMainHandItem();
        ItemStack off = p.getOffhandItem();
        FocusRules.Stats mainStats = stats(main, FocusRules.Hand.MAIN, off.isEmpty());
        FocusRules.Stats offStats = stats(off, FocusRules.Hand.OFF, true);
        // Two foci of one type do not stack: the main hand wins
        if (main.getItem() instanceof FocusItem a && off.getItem() instanceof FocusItem b && a.type() == b.type()) offStats = FocusRules.Stats.NONE;
        apply(p, MAIN, mainStats);
        apply(p, OFF, offStats);
    }

    private static FocusRules.Stats stats(ItemStack stack, FocusRules.Hand hand, boolean offHandEmpty) {
        if (!(stack.getItem() instanceof FocusItem focus) || !FocusRules.works(focus.type(), hand, offHandEmpty)) return FocusRules.Stats.NONE;
        return FocusRules.stats(focus.type(), focus.tier());
    }

    private static void apply(Player p, Identifier id, FocusRules.Stats s) {
        set(p, ModAttributes.SPELL_POWER, id, s.power());
        set(p, ModAttributes.CAST_SPEED, id, s.castSpeed());
        set(p, ModAttributes.SPELL_RANGE, id, s.range());
        set(p, ModAttributes.CAST_STABILITY, id, s.stability());
        set(p, ModAttributes.SPELL_COST, id, s.cost());
        set(p, ModAttributes.MASTERY_XP, id, s.masteryXp());
    }

    private static void set(Player p, Holder<Attribute> attribute, Identifier id, double amount) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier old = inst.getModifier(id);
        if (old != null && old.amount() == amount) return;
        if (old != null) inst.removeModifier(id);
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    /** All magic attributes a focus touches (for tests and tooltips). */
    public static List<Holder<Attribute>> attributes() {
        return List.of(ModAttributes.SPELL_POWER, ModAttributes.CAST_SPEED, ModAttributes.SPELL_RANGE,
                ModAttributes.CAST_STABILITY, ModAttributes.SPELL_COST, ModAttributes.MASTERY_XP);
    }

    private Foci() {}
}
