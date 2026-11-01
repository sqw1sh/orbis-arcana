package com.orbisarcana.spell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.OrbisArcana;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Magic shields as absorption (§10.5, §14.2). Absorption is capped by the {@code MAX_ABSORPTION} attribute
 * (0 by default), so like the vanilla Absorption effect a shield raises that cap with a transient modifier
 * for as long as it lasts. Each shield reports how much it absorbed and whether it broke.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class Shields {
    private static final net.minecraft.resources.Identifier MODIFIER = OrbisArcana.id("shield");

    /** Called once when a shield ends: {@code absorbed} damage it took, {@code broken} if it was used up. */
    @FunctionalInterface
    public interface EndHandler {
        void onEnd(LivingEntity holder, float absorbed, boolean broken);
    }

    private static final class Shield {
        final float amount;
        final long endTick;
        final @Nullable EndHandler onEnd;

        Shield(float amount, long endTick, @Nullable EndHandler onEnd) {
            this.amount = amount;
            this.endTick = endTick;
            this.onEnd = onEnd;
        }
    }

    private static final Map<LivingEntity, List<Shield>> ACTIVE = new HashMap<>();

    /** Adds a shield of {@code amount} HP for {@code ticks}. */
    public static void grant(LivingEntity target, float amount, int ticks, @Nullable EndHandler onEnd) {
        if (amount <= 0 || !(target.level() instanceof ServerLevel level)) return;
        ACTIVE.computeIfAbsent(target, t -> new ArrayList<>()).add(new Shield(amount, level.getGameTime() + ticks, onEnd));
        updateCap(target);
        target.setAbsorptionAmount(target.getAbsorptionAmount() + amount);
    }

    /** Total active shield HP on {@code target} (for tests and UI). */
    public static float total(LivingEntity target) {
        return ACTIVE.getOrDefault(target, List.of()).stream().map(s -> s.amount).reduce(0f, Float::sum);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || ACTIVE.isEmpty()) return;
        long now = level.getGameTime();
        ACTIVE.entrySet().removeIf(e -> {
            LivingEntity holder = e.getKey();
            if (holder.level() != level) return false;
            List<Shield> shields = e.getValue();
            if (!holder.isAlive() || holder.isRemoved()) {
                shields.clear();
                return true;
            }
            boolean broken = holder.getAbsorptionAmount() <= 0.01f;
            List<Shield> ended = new ArrayList<>();
            for (Shield s : shields) {
                if (broken || now >= s.endTick) ended.add(s);
            }
            if (ended.isEmpty()) return false;
            // Absorption left over is shared by the shields still up, newest last
            float remainingTotal = holder.getAbsorptionAmount();
            shields.removeAll(ended);
            float keptAmount = shields.stream().map(s -> s.amount).reduce(0f, Float::sum);
            float leftForEnded = Math.max(0, remainingTotal - keptAmount);
            for (Shield s : ended) {
                float left = Math.min(s.amount, leftForEnded);
                leftForEnded -= left;
                if (s.onEnd != null) s.onEnd.onEnd(holder, s.amount - left, broken);
            }
            updateCap(holder);
            holder.setAbsorptionAmount(Math.min(holder.getAbsorptionAmount(), keptAmount + vanillaCap(holder)));
            return shields.isEmpty();
        });
    }

    private static float vanillaCap(LivingEntity holder) {
        AttributeInstance cap = holder.getAttribute(Attributes.MAX_ABSORPTION);
        if (cap == null) return 0;
        AttributeModifier ours = cap.getModifier(MODIFIER);
        return (float) (cap.getValue() - (ours == null ? 0 : ours.amount()));
    }

    private static void updateCap(LivingEntity holder) {
        AttributeInstance cap = holder.getAttribute(Attributes.MAX_ABSORPTION);
        if (cap == null) return;
        cap.removeModifier(MODIFIER);
        float total = total(holder);
        if (total > 0) cap.addTransientModifier(new AttributeModifier(MODIFIER, total, AttributeModifier.Operation.ADD_VALUE));
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.clear();
    }

    private Shields() {}
}
