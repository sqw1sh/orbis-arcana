package com.orbisarcana.registry;

import java.util.Map;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.item.SpellScrollItem;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

public final class ModItems {
    public static final DeferredItem<SpellScrollItem> SPELL_SCROLL = ModRegistries.ITEMS.registerItem(
            "spell_scroll", SpellScrollItem::new, p -> p.stacksTo(16));

    /** Aether Ink (§11.4): currency of altar rituals. Sources (dissolving duplicates, crafting) arrive in S7. */
    public static final DeferredItem<Item> AETHER_INK = ModRegistries.ITEMS.registerSimpleItem("aether_ink", p -> p.rarity(Rarity.UNCOMMON));

    /** Seal of Comprehension (§11.4): key to a higher school, consumed by its first scroll. Recipes in S7. */
    public static final DeferredItem<Item> SEAL_LIGHT = seal("light");
    public static final DeferredItem<Item> SEAL_DARK = seal("dark");
    public static final DeferredItem<Item> SEAL_ASTRAL = seal("astral");

    public static final Map<Identifier, DeferredItem<Item>> SEALS = Map.of(
            Schools.LIGHT, SEAL_LIGHT, Schools.DARK, SEAL_DARK, Schools.ASTRAL, SEAL_ASTRAL);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = ModRegistries.CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.orbisarcana"))
                    .icon(() -> SPELL_SCROLL.get().getDefaultInstance())
                    .displayItems((params, out) -> {
                        SpellRegistry.ids(params.holders()).stream().sorted()
                                .forEach(id -> out.accept(SpellScrollItem.of(id)));
                        out.accept(ModBlocks.RESONANCE_ALTAR_ITEM.get());
                        out.accept(AETHER_INK.get());
                        out.accept(SEAL_LIGHT.get());
                        out.accept(SEAL_DARK.get());
                        out.accept(SEAL_ASTRAL.get());
                    })
                    .build());

    private static DeferredItem<Item> seal(String school) {
        return ModRegistries.ITEMS.registerSimpleItem("seal_" + school, p -> p.rarity(Rarity.RARE));
    }

    /** Forces class loading so the deferred entries above are registered. */
    public static void init() {}

    private ModItems() {}
}
