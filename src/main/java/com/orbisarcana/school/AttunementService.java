package com.orbisarcana.school;

import java.util.Optional;

import com.orbisarcana.core.SchoolRules;
import com.orbisarcana.data.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Server-side school attunement (§10): binding, unbinding, switching with Dissonance, resonance growth
 * and the dominant-school bonuses used by casting. Costs and the ritual are handled by the altar.
 */
public final class AttunementService {
    public static Attunement attunement(ServerPlayer player) {
        return player.getData(ModAttachments.ATTUNEMENT);
    }

    public static boolean isDissonant(ServerPlayer player) {
        return player.level().getGameTime() < attunement(player).dissonanceUntil();
    }

    // ---- bonuses (§10.2, §10.6) ----

    public static double powerMult(ServerPlayer player, Identifier spellSchool) {
        Attunement a = attunement(player);
        boolean dominant = a.dominant().filter(spellSchool::equals).isPresent();
        boolean previous = a.previous().filter(spellSchool::equals).isPresent();
        int resonance = a.schools().containsKey(spellSchool) ? a.schools().get(spellSchool).resonance() : 0;
        return SchoolRules.powerMult(dominant, previous, resonance, isDissonant(player));
    }

    public static double costMult(ServerPlayer player, Identifier spellSchool) {
        return SchoolRules.costMult(attunement(player).dominant().filter(spellSchool::equals).isPresent());
    }

    // ---- binding (§10.6) ----

    /** What binding to {@code school} would cost now: free for the very first binding. */
    public static SchoolRules.SwitchCost costFor(ServerPlayer player, Identifier school) {
        return costFor(attunement(player), school);
    }

    /** Same rule from attunement data alone, so the client altar screen shows the server price. */
    public static SchoolRules.SwitchCost costFor(Attunement a, Identifier school) {
        if (SchoolRules.isFreeBinding(a.everBound())) return SchoolRules.SwitchCost.FREE;
        int resonance = a.schools().containsKey(school) ? a.schools().get(school).resonance() : 0;
        return SchoolRules.switchCost(Schools.isHigher(school), resonance);
    }

    /** Why binding is not possible right now, if so. */
    public static Optional<Component> bindProblem(ServerPlayer player, Identifier school) {
        Attunement a = attunement(player);
        if (a.dominant().filter(school::equals).isPresent()) return Optional.of(Component.translatable("message.orbisarcana.school.already"));
        if (a.everBound() && !SchoolRules.canSwitchAt(player.level().getGameTime(), a.nextSwitchAt())) {
            long left = (a.nextSwitchAt() - player.level().getGameTime()) / 20;
            return Optional.of(Component.translatable("message.orbisarcana.school.cooldown", left / 60, left % 60));
        }
        return Optional.empty();
    }

    /**
     * Makes {@code school} dominant. The first binding is free and calm; later ones start Dissonance and
     * the switch pause, and remember the old school (its resonance and talents stay frozen, §10.3).
     */
    public static void bind(ServerPlayer player, Identifier school) {
        Attunement a = attunement(player);
        long now = player.level().getGameTime();
        boolean first = SchoolRules.isFreeBinding(a.everBound());
        Optional<Identifier> old = a.dominant().or(a::previous);
        a.setDominant(Optional.of(school));
        if (!first) {
            a.setPrevious(old.filter(o -> !o.equals(school)));
            a.setDissonanceUntil(now + SchoolRules.DISSONANCE_TICKS);
            a.setNextSwitchAt(now + SchoolRules.SWITCH_COOLDOWN_TICKS);
        }
        player.syncData(ModAttachments.ATTUNEMENT);
        player.sendSystemMessage(Component.translatable(first ? "message.orbisarcana.school.bound" : "message.orbisarcana.school.switched",
                schoolName(school)).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Back to the neutral state; free, the school's progress is frozen (§10.6). */
    public static void unbind(ServerPlayer player) {
        Attunement a = attunement(player);
        a.dominant().ifPresent(old -> a.setPrevious(Optional.of(old)));
        a.setDominant(Optional.empty());
        player.syncData(ModAttachments.ATTUNEMENT);
        player.sendSystemMessage(Component.translatable("message.orbisarcana.school.unbound").withStyle(ChatFormatting.GRAY));
    }

    // ---- resonance (§10.3) ----

    /** Part of every mastery XP gain flows into the dominant school's resonance. */
    public static void onMasteryXp(ServerPlayer player, Identifier spellSchool, double masteryXp) {
        Attunement a = attunement(player);
        if (a.dominant().isEmpty() || masteryXp <= 0) return;
        Identifier dominant = a.dominant().get();
        Attunement.SchoolProgress p = a.school(dominant);
        if (p.resonance() >= SchoolRules.MAX_RESONANCE) return;

        double xp = p.xp() + masteryXp * SchoolRules.resonanceShare(dominant.equals(spellSchool), isDissonant(player));
        int level = p.resonance();
        while (level < SchoolRules.MAX_RESONANCE && xp >= SchoolRules.resonanceXpRequired(level)) {
            xp -= SchoolRules.resonanceXpRequired(level);
            level++;
            announce(player, dominant, level);
        }
        if (level != p.resonance()) p.setResonance(level);
        p.setXp(level >= SchoolRules.MAX_RESONANCE ? 0 : xp);
        player.syncData(ModAttachments.ATTUNEMENT);
    }

    private static void announce(ServerPlayer player, Identifier school, int level) {
        String reward = switch (level) {
            case 1 -> "echo";
            case 3, 6, 9 -> "talent";
            case 5 -> "echo2";
            case 10 -> "seal";
            default -> "none";
        };
        player.sendSystemMessage(Component.translatable("message.orbisarcana.resonance", schoolName(school), level,
                Component.translatable("message.orbisarcana.resonance.reward." + reward)).withStyle(ChatFormatting.LIGHT_PURPLE));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.3f);
    }

    public static Component schoolName(Identifier school) {
        return Component.translatable("school.orbisarcana." + school.getPath()).withColor(Schools.color(school));
    }

    private AttunementService() {}
}
