package com.orbisarcana.progression;

import java.util.List;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.Rarity;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.item.SpellScrollItem;
import com.orbisarcana.registry.ModItems;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Onboarding (§4): on the first login a player gets the "First Seals" book and one common scroll of a random elemental
 * school. The grimoire is open from the start and no school is bound.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class Onboarding {
    public static final int PAGES = 8;

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) welcome(p);
    }

    /** Gives the starter kit once per player; returns whether it was given now. */
    public static boolean welcome(ServerPlayer p) {
        if (p.getData(ModAttachments.ONBOARDED)) return false;
        p.setData(ModAttachments.ONBOARDED, true);
        p.getInventory().placeItemBackInInventory(new ItemStack(ModItems.FIRST_SEALS.get()), Prediction.SERVER_ONLY);
        List<Identifier> commons = SpellRegistry.ids(p.registryAccess()).stream().sorted()
                .filter(id -> SpellRegistry.get(p.registryAccess(), id)
                        .filter(d -> d.enabled() && d.rarity() == Rarity.COMMON && !Schools.isHigher(d.school())).isPresent())
                .toList();
        if (!commons.isEmpty()) {
            Identifier spell = commons.get(p.getRandom().nextInt(commons.size()));
            p.getInventory().placeItemBackInInventory(SpellScrollItem.of(spell), Prediction.SERVER_ONLY);
        }
        return true;
    }

    /**
     * The book's pages: translated on the client, with the player's own key bindings filled in. Built once for the
     * item's default components.
     */
    public static WrittenBookContent book() {
        List<Filterable<Component>> pages = new java.util.ArrayList<>();
        for (int i = 1; i <= PAGES; i++) {
            String key = "book.orbisarcana.first_seals.page" + i;
            Component page = switch (i) {
                case 3 -> Component.translatable(key, Component.keybind("key.orbisarcana.grimoire"),
                        Component.keybind("key.orbisarcana.radial"), Component.keybind("key.orbisarcana.cast"));
                default -> Component.translatable(key);
            };
            pages.add(Filterable.passThrough(page));
        }
        return new WrittenBookContent(Filterable.passThrough("First Seals"), "Orbis Arcana", 0, pages, true);
    }

    private Onboarding() {}
}
