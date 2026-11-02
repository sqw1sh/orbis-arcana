package com.orbisarcana.registry;

import java.util.Map;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.FocusRules;
import com.orbisarcana.item.FocusItem;
import com.orbisarcana.item.ManaPotionItem;
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

    // Foci (§11.1): three tiers of each type
    public static final DeferredItem<FocusItem> APPRENTICE_WAND = focus("apprentice_wand", FocusRules.Type.WAND, 1, Rarity.COMMON);
    public static final DeferredItem<FocusItem> AMETHYST_WAND = focus("amethyst_wand", FocusRules.Type.WAND, 2, Rarity.UNCOMMON);
    public static final DeferredItem<FocusItem> NETHERITE_WAND = focus("netherite_wand", FocusRules.Type.WAND, 3, Rarity.RARE);
    public static final DeferredItem<FocusItem> APPRENTICE_STAFF = focus("apprentice_staff", FocusRules.Type.STAFF, 1, Rarity.COMMON);
    public static final DeferredItem<FocusItem> AMETHYST_STAFF = focus("amethyst_staff", FocusRules.Type.STAFF, 2, Rarity.UNCOMMON);
    public static final DeferredItem<FocusItem> NETHERITE_STAFF = focus("netherite_staff", FocusRules.Type.STAFF, 3, Rarity.RARE);
    public static final DeferredItem<FocusItem> APPRENTICE_TOME = focus("apprentice_tome", FocusRules.Type.TOME, 1, Rarity.COMMON);
    public static final DeferredItem<FocusItem> AMETHYST_TOME = focus("amethyst_tome", FocusRules.Type.TOME, 2, Rarity.UNCOMMON);
    public static final DeferredItem<FocusItem> NETHERITE_TOME = focus("netherite_tome", FocusRules.Type.TOME, 3, Rarity.RARE);

    /** Mana Potion (§11.4): +40 mana, 10 s cooldown so it tops up rather than replaces regeneration. */
    public static final DeferredItem<ManaPotionItem> MANA_POTION = ModRegistries.ITEMS.registerItem("mana_potion",
            p -> new ManaPotionItem(p, 40), p -> p.stacksTo(16).rarity(Rarity.UNCOMMON)
                    .component(net.minecraft.core.component.DataComponents.CONSUMABLE, net.minecraft.world.item.component.Consumables.DEFAULT_DRINK)
                    .usingConvertsTo(net.minecraft.world.item.Items.GLASS_BOTTLE).useCooldown(10f));

    public static final java.util.List<DeferredItem<FocusItem>> FOCI = java.util.List.of(APPRENTICE_WAND, AMETHYST_WAND, NETHERITE_WAND,
            APPRENTICE_STAFF, AMETHYST_STAFF, NETHERITE_STAFF, APPRENTICE_TOME, AMETHYST_TOME, NETHERITE_TOME);

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
                        FOCI.forEach(item -> out.accept(item.get()));
                        out.accept(MANA_POTION.get());
                    })
                    .build());

    private static DeferredItem<FocusItem> focus(String name, FocusRules.Type type, int tier, Rarity rarity) {
        return ModRegistries.ITEMS.registerItem(name, p -> new FocusItem(p, type, tier),
                p -> tier == 3 ? p.stacksTo(1).rarity(rarity).fireResistant() : p.stacksTo(1).rarity(rarity));
    }

    private static DeferredItem<Item> seal(String school) {
        return ModRegistries.ITEMS.registerSimpleItem("seal_" + school, p -> p.rarity(Rarity.RARE));
    }

    /** Forces class loading so the deferred entries above are registered. */
    public static void init() {}

    private ModItems() {}
}
