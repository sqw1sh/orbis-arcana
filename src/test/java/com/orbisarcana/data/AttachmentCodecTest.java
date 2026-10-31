package com.orbisarcana.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.orbisarcana.progression.KnownSpells;
import com.orbisarcana.progression.Loadout;
import com.orbisarcana.school.Attunement;

import net.minecraft.resources.Identifier;

/** Round-trips the player attachments through their save codecs. */
class AttachmentCodecTest {
    private static final Identifier FIREBALL = Identifier.fromNamespaceAndPath("orbisarcana", "fireball");
    private static final Identifier SHARD = Identifier.fromNamespaceAndPath("orbisarcana", "ice_shard");
    private static final Identifier FIRE = Identifier.fromNamespaceAndPath("orbisarcana", "fire");

    private static <T> T roundTrip(MapCodec<T> codec, T value) {
        JsonElement json = codec.codec().encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        return codec.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test
    void knowledgeRoundTripAndArchive() {
        KnownSpells k = new KnownSpells();
        assertTrue(k.learn(FIREBALL));
        assertFalse(k.learn(FIREBALL));
        k.learn(SHARD);
        k.progress(FIREBALL).orElseThrow().setLevel(10);
        k.progress(FIREBALL).orElseThrow().setMilestoneDisabled(5, true);

        KnownSpells back = roundTrip(KnownSpells.CODEC, k);
        assertEquals(10, back.progress(FIREBALL).orElseThrow().level());
        assertFalse(back.progress(FIREBALL).orElseThrow().isMilestoneEnabled(5));
        assertTrue(back.progress(FIREBALL).orElseThrow().isMilestoneEnabled(10));

        assertEquals(1, back.archiveUnknown(Set.of(FIREBALL)));
        assertFalse(back.knows(SHARD));
        back = roundTrip(KnownSpells.CODEC, back);
        assertTrue(back.archived().containsKey(SHARD));
        back.learn(SHARD);
        assertTrue(back.archived().isEmpty());
    }

    @Test
    void attunementRoundTripKeepsFrozenSchools() {
        Attunement a = new Attunement();
        a.setDominant(Optional.of(FIRE));
        a.school(FIRE).setResonance(6);
        a.school(FIRE).setResonance(2);
        a.school(FIRE).talents().add("embers");
        a.setNextSwitchAt(24000);

        Attunement back = roundTrip(Attunement.CODEC, a);
        assertEquals(Optional.of(FIRE), back.dominant());
        assertEquals(2, back.school(FIRE).resonance());
        assertEquals(6, back.school(FIRE).peak());
        assertTrue(back.school(FIRE).talents().contains("embers"));
        assertEquals(24000, back.nextSwitchAt());

        back.setDominant(Optional.empty());
        assertEquals(2, roundTrip(Attunement.CODEC, back).school(FIRE).resonance());
    }

    @Test
    void loadoutRoundTrip() {
        Loadout l = new Loadout();
        l.set(0, 0, Optional.of(FIREBALL));
        l.set(2, 7, Optional.of(SHARD));
        l.setPage(2);
        l.setSelected(7);

        Loadout back = roundTrip(Loadout.CODEC, l);
        assertEquals(Optional.of(FIREBALL), back.get(0, 0));
        assertEquals(Optional.of(SHARD), back.selectedSpell());
        back.removeEverywhere(SHARD);
        assertEquals(Optional.empty(), back.get(2, 7));
        back.setPage(-1);
        assertEquals(2, back.page());
    }
}
