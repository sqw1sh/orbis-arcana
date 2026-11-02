package com.orbisarcana.progression;

import java.util.Optional;

import com.orbisarcana.OrbisConfig;
import com.orbisarcana.core.LearnRules;
import com.orbisarcana.core.MasteryMath;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.item.SpellScrollItem;
import com.orbisarcana.registry.ModItems;
import com.orbisarcana.school.Attunement;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.SpellDefinition;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Server logic of spell scrolls (§6): learning with requirements and dissolving duplicates. */
public final class ScrollService {
    /** Checks before the 2 s reading starts, so the player is told why right away. */
    public static boolean canStartReading(ServerPlayer player, Identifier spell) {
        Optional<LearnRules.Denial> denial = check(player, spell);
        denial.ifPresent(d -> player.sendOverlayMessage(denialMessage(d, spell)));
        return denial.isEmpty();
    }

    public static void learn(ServerPlayer player, ItemStack scroll, Identifier spellId) {
        Optional<SpellDefinition> spell = SpellRegistry.get(player.registryAccess(), spellId);
        Optional<LearnRules.Denial> denial = check(player, spellId);
        if (spell.isEmpty() || denial.isPresent()) {
            denial.ifPresent(d -> player.sendOverlayMessage(denialMessage(d, spellId)));
            return;
        }
        if (needsSeal(player, spell.get())) consumeSeal(player, spell.get().school());
        KnowledgeService.learn(player, spellId);
        scroll.consume(1, player);
        player.sendSystemMessage(Component.translatable("message.orbisarcana.learned", SpellScrollItem.spellName(spellId).copy()
                .withColor(spell.get().rarity().color)).withStyle(ChatFormatting.LIGHT_PURPLE));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.2f);
    }

    /** Shift + right-click on a known spell's scroll: +15% of the next level, never above level 9 (§6.3). */
    public static void dissolve(ServerPlayer player, ItemStack scroll, Identifier spellId) {
        Optional<SpellDefinition> spell = SpellRegistry.get(player.registryAccess(), spellId);
        var progress = player.getData(ModAttachments.KNOWLEDGE).progress(spellId);
        if (spell.isEmpty()) return;
        if (progress.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.orbisarcana.dissolve.unknown").withStyle(ChatFormatting.RED));
            return;
        }
        var p = progress.get();
        double xp = LearnRules.dissolveXp(p.level(), spell.get().rarity());
        if (xp <= 0) {
            player.sendOverlayMessage(Component.translatable("message.orbisarcana.dissolve.capped", LearnRules.DISSOLVE_MAX_LEVEL).withStyle(ChatFormatting.RED));
            return;
        }
        var result = MasteryMath.addXp(p.level(), p.xp(), xp, spell.get().rarity(), LearnRules.DISSOLVE_MAX_LEVEL);
        p.setLevel(result.level());
        p.setXp(result.xp());
        player.syncData(ModAttachments.KNOWLEDGE);
        scroll.consume(1, player);
        player.sendOverlayMessage(Component.translatable("message.orbisarcana.dissolved",
                String.format("%.1f", xp), SpellScrollItem.spellName(spellId), result.level()).withStyle(ChatFormatting.AQUA));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1f);
    }

    /**
     * Takes a duplicate scroll apart on the altar (§6.3): the scroll becomes Aether Ink. Only scrolls of known spells,
     * so a scroll still to be learned is never lost by accident. Returns whether it happened.
     */
    public static boolean dismantle(ServerPlayer player, ItemStack scroll) {
        Optional<Identifier> spellId = SpellScrollItem.spell(scroll);
        if (spellId.isEmpty()) return false;
        Optional<SpellDefinition> spell = SpellRegistry.get(player.registryAccess(), spellId.get());
        if (spell.isEmpty()) return false;
        if (!player.getData(ModAttachments.KNOWLEDGE).knows(spellId.get())) {
            player.sendOverlayMessage(Component.translatable("message.orbisarcana.dismantle.unknown").withStyle(ChatFormatting.RED));
            return false;
        }
        int ink = LearnRules.dismantleInk(spell.get().rarity());
        scroll.consume(1, player);
        player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.AETHER_INK.get(), ink), net.minecraft.util.Prediction.SERVER_ONLY);
        player.sendOverlayMessage(Component.translatable("message.orbisarcana.dismantled", SpellScrollItem.spellName(spellId.get()), ink)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.2f);
        return true;
    }

    private static Optional<LearnRules.Denial> check(ServerPlayer player, Identifier spellId) {
        Optional<SpellDefinition> spell = SpellRegistry.get(player.registryAccess(), spellId);
        if (spell.isEmpty()) return Optional.empty(); // unknown spells are reported by the tooltip; nothing to learn
        KnownSpells knowledge = player.getData(ModAttachments.KNOWLEDGE);
        Identifier school = spell.get().school();
        Attunement.SchoolProgress resonance = player.getData(ModAttachments.ATTUNEMENT).schools().get(school);
        return LearnRules.check(new LearnRules.Context(
                spell.get().rarity(),
                knowledge.knows(spellId),
                bestMasteryInSchool(player, school),
                resonance == null ? 0 : resonance.resonance(),
                resonance == null ? 0 : resonance.peak(),
                needsSeal(player, spell.get()),
                findSeal(player, school) >= 0,
                OrbisConfig.LEARN_REQUIREMENTS.getAsBoolean()));
    }

    private static int bestMasteryInSchool(ServerPlayer player, Identifier school) {
        int best = 0;
        for (var e : player.getData(ModAttachments.KNOWLEDGE).spells().entrySet()) {
            Optional<SpellDefinition> def = SpellRegistry.get(player.registryAccess(), e.getKey());
            if (def.isPresent() && def.get().school().equals(school)) best = Math.max(best, e.getValue().level());
        }
        return best;
    }

    /** Higher schools need a seal for their first spell (§6.2). */
    private static boolean needsSeal(ServerPlayer player, SpellDefinition spell) {
        if (!Schools.isHigher(spell.school())) return false;
        for (Identifier known : player.getData(ModAttachments.KNOWLEDGE).spells().keySet()) {
            Optional<SpellDefinition> def = SpellRegistry.get(player.registryAccess(), known);
            if (def.isPresent() && def.get().school().equals(spell.school())) return false;
        }
        return true;
    }

    private static int findSeal(ServerPlayer player, Identifier school) {
        var seal = ModItems.SEALS.get(school);
        if (seal == null) return -1;
        Item item = seal.get();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) return i;
        }
        return -1;
    }

    private static void consumeSeal(ServerPlayer player, Identifier school) {
        int slot = findSeal(player, school);
        if (slot >= 0) player.getInventory().getItem(slot).consume(1, player);
    }

    private static Component denialMessage(LearnRules.Denial denial, Identifier spell) {
        return Component.translatable("message.orbisarcana.learn_denied." + denial.id(), SpellScrollItem.spellName(spell))
                .withStyle(ChatFormatting.RED);
    }

    private ScrollService() {}
}
