package com.orbisarcana.registry;

import java.util.Map;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.FocusRules;
import com.orbisarcana.item.AccessoryItem;
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

    /** Aether Shard (§11.4): from Aether Crystal ore, the base of magic crafting. */
    public static final DeferredItem<Item> AETHER_SHARD = ModRegistries.ITEMS.registerSimpleItem("aether_shard", p -> p);

    /** Aether Ink (§11.4): currency of altar rituals. Comes from taking duplicate scrolls apart on the altar and from crafting. */
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

    /** "First Seals" (§4): the tutorial book, a written book whose pages are translated on the client. */
    public static final DeferredItem<net.minecraft.world.item.WrittenBookItem> FIRST_SEALS = ModRegistries.ITEMS.registerItem("first_seals",
            net.minecraft.world.item.WrittenBookItem::new, p -> p.stacksTo(1)
                    .component(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT, com.orbisarcana.progression.Onboarding.book()));

    /** Mana Potion (§11.4): +40 mana, 10 s cooldown so it tops up rather than replaces regeneration. */
    public static final DeferredItem<ManaPotionItem> MANA_POTION = ModRegistries.ITEMS.registerItem("mana_potion",
            p -> new ManaPotionItem(p, 40), p -> p.stacksTo(16).rarity(Rarity.UNCOMMON)
                    .component(net.minecraft.core.component.DataComponents.CONSUMABLE, net.minecraft.world.item.component.Consumables.DEFAULT_DRINK)
                    .usingConvertsTo(net.minecraft.world.item.Items.GLASS_BOTTLE).useCooldown(10f));

    public static final java.util.List<DeferredItem<FocusItem>> FOCI = java.util.List.of(APPRENTICE_WAND, AMETHYST_WAND, NETHERITE_WAND,
            APPRENTICE_STAFF, AMETHYST_STAFF, NETHERITE_STAFF, APPRENTICE_TOME, AMETHYST_TOME, NETHERITE_TOME);

    // Accessories (§11.2), worn through Curios (optional)
    public static final DeferredItem<AccessoryItem> RING_OF_FLAME = accessory("ring_of_flame", AccessoryItem.Kind.SCHOOL_RING, Schools.FIRE, Rarity.UNCOMMON);
    public static final DeferredItem<AccessoryItem> RING_OF_STONE = accessory("ring_of_stone", AccessoryItem.Kind.SCHOOL_RING, Schools.EARTH, Rarity.UNCOMMON);
    public static final DeferredItem<AccessoryItem> RING_OF_TIDE = accessory("ring_of_tide", AccessoryItem.Kind.SCHOOL_RING, Schools.WATER, Rarity.UNCOMMON);
    public static final DeferredItem<AccessoryItem> RING_OF_WIND = accessory("ring_of_wind", AccessoryItem.Kind.SCHOOL_RING, Schools.AIR, Rarity.UNCOMMON);
    public static final DeferredItem<AccessoryItem> RING_OF_FROST = accessory("ring_of_frost", AccessoryItem.Kind.SCHOOL_RING, Schools.ICE, Rarity.UNCOMMON);
    public static final DeferredItem<AccessoryItem> RING_OF_STORM = accessory("ring_of_storm", AccessoryItem.Kind.SCHOOL_RING, Schools.LIGHTNING, Rarity.UNCOMMON);
    public static final DeferredItem<AccessoryItem> RING_OF_LEAF = accessory("ring_of_leaf", AccessoryItem.Kind.SCHOOL_RING, Schools.NATURE, Rarity.UNCOMMON);
    public static final DeferredItem<AccessoryItem> MANA_AMULET = accessory("mana_amulet", AccessoryItem.Kind.MANA_AMULET, null, Rarity.RARE);
    public static final DeferredItem<AccessoryItem> FLOW_AMULET = accessory("flow_amulet", AccessoryItem.Kind.FLOW_AMULET, null, Rarity.RARE);
    public static final DeferredItem<AccessoryItem> FOCUS_AMULET = accessory("focus_amulet", AccessoryItem.Kind.FOCUS_AMULET, null, Rarity.RARE);
    public static final DeferredItem<AccessoryItem> RING_OF_ECHO = accessory("ring_of_echo", AccessoryItem.Kind.ECHO_RING, null, Rarity.RARE);
    public static final DeferredItem<AccessoryItem> SCRIBE_RING = accessory("scribe_ring", AccessoryItem.Kind.SCRIBE_RING, null, Rarity.RARE);
    public static final java.util.List<DeferredItem<AccessoryItem>> ACCESSORIES = java.util.List.of(RING_OF_FLAME, RING_OF_STONE, RING_OF_TIDE, RING_OF_WIND, RING_OF_FROST, RING_OF_STORM, RING_OF_LEAF, MANA_AMULET, FLOW_AMULET, FOCUS_AMULET, RING_OF_ECHO, SCRIBE_RING);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = ModRegistries.CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.orbisarcana"))
                    .icon(() -> SPELL_SCROLL.get().getDefaultInstance())
                    .displayItems((params, out) -> {
                        SpellRegistry.ids(params.holders()).stream().sorted()
                                .forEach(id -> out.accept(SpellScrollItem.of(id)));
                        out.accept(FIRST_SEALS.get());
                        out.accept(ModBlocks.RESONANCE_ALTAR_ITEM.get());
                        out.accept(ModBlocks.AETHER_ORE_ITEM.get());
                        out.accept(ModBlocks.DEEPSLATE_AETHER_ORE_ITEM.get());
                        out.accept(AETHER_SHARD.get());
                        out.accept(AETHER_INK.get());
                        out.accept(SEAL_LIGHT.get());
                        out.accept(SEAL_DARK.get());
                        out.accept(SEAL_ASTRAL.get());
                        FOCI.forEach(item -> out.accept(item.get()));
                        out.accept(MANA_POTION.get());
                        // Without Curios accessories cannot be worn, so they stay out of sight (§11.2)
                        if (net.neoforged.fml.ModList.get().isLoaded("curios")) ACCESSORIES.forEach(item -> out.accept(item.get()));
                    })
                    .build());

    private static DeferredItem<AccessoryItem> accessory(String name, AccessoryItem.Kind kind, Identifier school, Rarity rarity) {
        return ModRegistries.ITEMS.registerItem(name, p -> new AccessoryItem(p, kind, school), p -> p.stacksTo(1).rarity(rarity));
    }

    private static DeferredItem<FocusItem> focus(String name, FocusRules.Type type, int tier, Rarity rarity) {
        return ModRegistries.ITEMS.registerItem(name, p -> new FocusItem(p, type, tier), p -> {
            p.stacksTo(1).rarity(rarity).component(ModDataComponents.FOCUS, new com.orbisarcana.api.Focus(type, tier));
            return tier == 3 ? p.fireResistant() : p;
        });
    }

    private static DeferredItem<Item> seal(String school) {
        return ModRegistries.ITEMS.registerSimpleItem("seal_" + school, p -> p.rarity(Rarity.RARE));
    }

    /** Blockbench → GeckoLib pipeline check (art/models/debug_orb.bbmodel); not in the creative tab. */
    public static final DeferredItem<com.orbisarcana.item.DebugOrbItem> DEBUG_ORB = ModRegistries.ITEMS.registerItem("debug_orb",
            com.orbisarcana.item.DebugOrbItem::new, p -> p.stacksTo(1));

    /** Forces class loading so the deferred entries above are registered. */
    public static void init() {}

    private ModItems() {}
}
