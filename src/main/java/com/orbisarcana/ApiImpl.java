package com.orbisarcana;

import java.util.Optional;

import com.orbisarcana.api.OrbisArcanaAPI;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.mana.ManaService;
import com.orbisarcana.progression.KnowledgeService;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.spell.SpellRegistry;
import com.orbisarcana.school.AttunementService;
import com.orbisarcana.spell.CastService;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** The implementation behind {@link OrbisArcanaAPI#get()}: thin calls into the mod's services. */
final class ApiImpl implements OrbisArcanaAPI {
    @Override
    public boolean knows(ServerPlayer player, Identifier spell) {
        return player.getData(ModAttachments.KNOWLEDGE).knows(spell);
    }

    @Override
    public Optional<Integer> mastery(ServerPlayer player, Identifier spell) {
        return player.getData(ModAttachments.KNOWLEDGE).progress(spell).map(p -> p.level());
    }

    @Override
    public boolean learn(ServerPlayer player, Identifier spell) {
        return KnowledgeService.learn(player, spell);
    }

    @Override
    public double mana(ServerPlayer player) {
        return ManaService.mana(player);
    }

    @Override
    public double maxMana(ServerPlayer player) {
        return ManaService.maxMana(player);
    }

    @Override
    public void setMana(ServerPlayer player, double mana) {
        ManaService.setMana(player, Math.max(0, Math.min(ManaService.maxMana(player), mana)));
    }

    @Override
    public Optional<Identifier> dominantSchool(ServerPlayer player) {
        return AttunementService.attunement(player).dominant();
    }

    @Override
    public int resonance(ServerPlayer player, Identifier school) {
        var p = AttunementService.attunement(player).schools().get(school);
        return p == null ? 0 : p.resonance();
    }

    @Override
    public boolean hasTalent(ServerPlayer player, Identifier school, String node) {
        return AttunementService.hasTalent(player, school, node);
    }

    @Override
    public void cast(ServerPlayer player, Identifier spell) {
        CastService.request(player, spell);
    }

    @Override
    public void reportHit(ServerPlayer caster, Identifier spell, int castId, LivingEntity target) {
        MasteryService.onHit(caster, spell, castId, target, !target.isAlive());
        SpellRegistry.get(caster.registryAccess(), spell).ifPresent(d -> EchoService.onSpellHit(caster, d.school(), target, castId));
    }

    @Override
    public void reportHeal(ServerPlayer caster, Identifier spell, int castId, LivingEntity target, float healed) {
        if (healed <= 0) return;
        MasteryService.onHeal(caster, spell, castId, target, healed);
        EchoService.onSupport(caster, healed);
    }
}
