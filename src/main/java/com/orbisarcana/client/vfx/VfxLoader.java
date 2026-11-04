package com.orbisarcana.client.vfx;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orbisarcana.OrbisArcana;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

/**
 * Loads VFX effects (Snowstorm JSON from Blockbench, or volumetric {@link VfxMesh} definitions) from
 * {@code assets/<ns>/orbisarcana/vfx/*.json}; the id is the file name ({@code orbisarcana:fire_burst}). Reloads with F3+T; resource packs can replace any effect.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class VfxLoader extends SimplePreparableReloadListener<Map<Identifier, VfxEffect>> {
    private static final FileToIdConverter FILES = FileToIdConverter.json("orbisarcana/vfx");
    private static Map<Identifier, VfxEffect> effects = Map.of();
    private static Map<Identifier, VfxMesh> meshes = Map.of();

    static @Nullable VfxEffect get(Identifier id) {
        return effects.get(id);
    }

    static @Nullable VfxMesh mesh(Identifier id) {
        return meshes.get(id);
    }

    @Override
    protected Map<Identifier, VfxEffect> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, VfxEffect> out = new HashMap<>();
        Map<Identifier, VfxMesh> loadedMeshes = new HashMap<>();
        for (var entry : FILES.listMatchingResources(manager).entrySet()) {
            Identifier id = FILES.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (VfxMesh.isMesh(json)) loadedMeshes.put(id, new VfxMesh(id, json));
                else out.put(id, new VfxEffect(id, json));
            } catch (Exception e) {
                OrbisArcana.LOGGER.warn("VFX: cannot load {}: {}", entry.getKey(), e.toString());
            }
        }
        pendingMeshes = loadedMeshes;
        return out;
    }

    /** Meshes prepared with the particle effects, published together in apply. */
    private static volatile Map<Identifier, VfxMesh> pendingMeshes = Map.of();

    @Override
    protected void apply(Map<Identifier, VfxEffect> loaded, ResourceManager manager, ProfilerFiller profiler) {
        effects = loaded;
        meshes = pendingMeshes;
        OrbisArcana.LOGGER.info("Loaded {} VFX effect(s), {} volumetric", loaded.size(), meshes.size());
    }

    @SubscribeEvent
    static void register(AddClientReloadListenersEvent event) {
        event.addListener(OrbisArcana.id("vfx"), new VfxLoader());
    }
}
