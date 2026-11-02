package com.orbisarcana.client.circle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

/**
 * Small 3D ornaments of the circles (satellite crystals…) modelled in Blockbench ({@code art/circle/*.bbmodel}) and
 * exported as baked cube corners ({@code assets/<ns>/orbisarcana/circle_model/*.json}: per cube 8 corners in pixels,
 * Blockbench order, from {@code Cube#getGlobalVertexPositions}), so rotations match the editor exactly.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class CircleModels extends SimpleJsonResourceReloadListener<CircleModels.Model> {
    /** Faces of a cube as corner indices (Blockbench corner order: +x+y+z, +x+y-z, +x-y+z, +x-y-z, -x+y-z, -x+y+z, -x-y-z, -x-y+z). */
    static final int[][] FACES = {{0, 2, 3, 1}, {5, 4, 6, 7}, {0, 1, 4, 5}, {2, 7, 6, 3}, {0, 5, 7, 2}, {1, 3, 6, 4}};

    /** Cubes as 24 floats (8 corners x, y, z); {@code height} is the model's vertical extent in pixels. */
    public record Model(List<List<Float>> cubes, float height) {
        static final Codec<Model> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.listOf(24, 24).listOf().fieldOf("cubes").forGetter(Model::cubes)
        ).apply(i, cubes -> new Model(cubes, extent(cubes))));

        private static float extent(List<List<Float>> cubes) {
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (List<Float> c : cubes) for (int k = 1; k < 24; k += 3) {
                lo = Math.min(lo, c.get(k));
                hi = Math.max(hi, c.get(k));
            }
            return Math.max(1f, hi - lo);
        }
    }

    private static Map<Identifier, Model> models = Map.of();
    static final Identifier CRYSTAL = OrbisArcana.id("crystal");

    private CircleModels() {
        super(Model.CODEC, FileToIdConverter.json("orbisarcana/circle_model"));
    }

    static Model get(Identifier id) {
        return models.get(id);
    }

    @Override
    protected void apply(Map<Identifier, Model> loaded, ResourceManager manager, ProfilerFiller profiler) {
        models = new HashMap<>(loaded);
    }

    @SubscribeEvent
    static void register(AddClientReloadListenersEvent event) {
        event.addListener(OrbisArcana.id("circle_models"), new CircleModels());
    }
}
