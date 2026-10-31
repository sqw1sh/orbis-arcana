package com.orbisarcana.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.orbisarcana.core.CastMode;
import com.orbisarcana.core.Rarity;
import com.orbisarcana.core.Targeting;

/** Parses every bundled spell JSON and checks milestone param merging. */
class SpellDefinitionTest {
    private static final Path SPELLS = Path.of(System.getProperty("orbisarcana.resources", "src/main/resources"),
            "data/orbisarcana/orbisarcana/spell");

    private static SpellDefinition parse(String json) {
        return SpellDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void allBundledSpellsParse() throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(SPELLS)) {
            files = s.filter(p -> p.toString().endsWith(".json")).toList();
        }
        assertTrue(files.size() >= 4);
        for (Path f : files) {
            JsonElement json = JsonParser.parseString(Files.readString(f));
            var result = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json);
            assertTrue(result.isSuccess(), f.getFileName() + ": " + result.error().map(e -> e.message()).orElse(""));
        }
    }

    @Test
    void fireballMilestones() throws IOException {
        SpellDefinition fb = parse(Files.readString(SPELLS.resolve("fireball.json")));
        assertEquals(Rarity.COMMON, fb.rarity());
        assertEquals(Targeting.DIRECTIONAL, fb.targeting());
        assertEquals(CastMode.CHARGED, fb.cast().mode());

        Map<String, Double> lvl1 = fb.paramsAt(1, l -> false);
        assertEquals(1.0, lvl1.get("count"));
        Map<String, Double> lvl12 = fb.paramsAt(12, l -> false);
        assertEquals(2.0, lvl12.get("count"));
        assertEquals(0.8, lvl12.get("size"));
        assertEquals(0.0, lvl12.get("explode_radius"));
        Map<String, Double> lvl15noFive = fb.paramsAt(15, l -> l == 5);
        assertEquals(1.0, lvl15noFive.get("count"));
        assertEquals(2.5, lvl15noFive.get("explode_radius"));
    }

    @Test
    void rejectsUndeclaredMilestoneParam() {
        String json = """
                {"school":"orbisarcana:fire","rarity":"orbisarcana:common","behavior":"orbisarcana:projectile",
                 "targeting":"directional","cast":{"mode":"instant"},"cost":5,
                 "params":{"damage":1},"milestones":{"5":{"params":{"oops":2}}}}""";
        var result = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("oops"));
    }

    @Test
    void rejectsBadMilestoneLevelAndRarity() {
        String badLevel = """
                {"school":"orbisarcana:fire","rarity":"orbisarcana:common","behavior":"orbisarcana:projectile",
                 "targeting":"directional","cast":{"mode":"instant"},"cost":5,
                 "params":{"damage":1},"milestones":{"7":{"params":{"damage":2}}}}""";
        assertTrue(SpellDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(badLevel)).isError());
        String badRarity = badLevel.replace("orbisarcana:common", "orbisarcana:shiny").replace("\"7\"", "\"5\"");
        assertTrue(SpellDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(badRarity)).isError());
    }
}
