package com.orbisarcana.spell;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.data.ModAttachments;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.NewDatapackRegistryEvent;

/**
 * Datapack registry {@code orbisarcana:spell} (§15.4). Synced to clients; changes apply on world restart.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class SpellRegistry {
    public static final ResourceKey<Registry<SpellDefinition>> KEY = ResourceKey.createRegistryKey(OrbisArcana.id("spell"));

    public static void register(IEventBus modBus) {
        modBus.addListener((NewDatapackRegistryEvent event) -> event.worldRegistry(KEY, SpellDefinition.CODEC, SpellDefinition.CODEC));
    }

    public static Optional<SpellDefinition> get(HolderLookup.Provider registries, Identifier id) {
        return registries.lookup(KEY)
                .flatMap(lookup -> lookup.get(ResourceKey.create(KEY, id)))
                .map(holder -> holder.value());
    }

    public static Set<Identifier> ids(HolderLookup.Provider registries) {
        return registries.lookup(KEY)
                .map(lookup -> lookup.listElementIds().map(ResourceKey::identifier).collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    /** Spells of removed addons go to the archive on login and come back if the addon returns (§15.3). */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var knowledge = player.getData(ModAttachments.KNOWLEDGE);
        Set<Identifier> existing = ids(player.registryAccess());
        int archived = knowledge.archiveUnknown(existing);
        int restored = knowledge.restoreArchived(existing);
        if (archived + restored > 0) {
            OrbisArcana.LOGGER.info("{}: archived {} unknown spell(s), restored {}", player.getGameProfile().name(), archived, restored);
            player.syncData(ModAttachments.KNOWLEDGE);
        }
    }

    private SpellRegistry() {}
}
