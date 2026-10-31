package com.orbisarcana.client.circle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

/** Every bundled circle JSON must parse; each spell should have a circle. */
class CircleDefinitionTest {
    private static final Path RES = Path.of(System.getProperty("orbisarcana.resources", "src/main/resources"));
    private static final Path CIRCLES = RES.resolve("assets/orbisarcana/orbisarcana/circle");
    private static final Path SPELLS = RES.resolve("data/orbisarcana/orbisarcana/spell");

    private static List<Path> json(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".json")).toList();
        }
    }

    @Test
    void allCirclesParse() throws IOException {
        for (Path f : json(CIRCLES)) {
            var result = CircleDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(f)));
            assertTrue(result.isSuccess(), f.getFileName() + ": " + result.error().map(e -> e.message()).orElse(""));
        }
    }

    @Test
    void everySpellHasACircle() throws IOException {
        for (Path spell : json(SPELLS)) {
            assertTrue(Files.exists(CIRCLES.resolve(spell.getFileName())), "no circle for " + spell.getFileName());
        }
    }

    @Test
    void milestoneLayersParse() throws IOException {
        var def = CircleDefinition.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString(Files.readString(CIRCLES.resolve("fireball.json")))).getOrThrow();
        assertEquals(4, def.layers().size());
        assertEquals(2, def.milestoneLayers().get(15).size());
        assertEquals(0xFFC040, def.milestoneLayers().get(15).get(0).color().orElseThrow());
    }
}
