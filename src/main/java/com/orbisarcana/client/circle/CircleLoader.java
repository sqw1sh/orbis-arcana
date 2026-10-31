package com.orbisarcana.client.circle;

import java.util.HashMap;
import java.util.Map;

import com.orbisarcana.OrbisArcana;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

/** Loads circle JSONs from resource packs ({@code assets/<ns>/orbisarcana/circle/}); reloads with F3+T. */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class CircleLoader extends SimpleJsonResourceReloadListener<CircleDefinition> {
    private static Map<Identifier, CircleDefinition> circles = Map.of();

    private CircleLoader() {
        super(CircleDefinition.CODEC, FileToIdConverter.json("orbisarcana/circle"));
    }

    public static CircleDefinition get(Identifier id) {
        return circles.getOrDefault(id, CircleDefinition.FALLBACK);
    }

    @Override
    protected void apply(Map<Identifier, CircleDefinition> loaded, ResourceManager manager, ProfilerFiller profiler) {
        circles = new HashMap<>(loaded);
        OrbisArcana.LOGGER.info("Loaded {} magic circle definition(s)", circles.size());
    }

    @SubscribeEvent
    static void register(AddClientReloadListenersEvent event) {
        event.addListener(OrbisArcana.id("circles"), new CircleLoader());
    }
}
