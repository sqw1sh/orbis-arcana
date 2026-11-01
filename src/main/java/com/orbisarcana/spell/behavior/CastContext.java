package com.orbisarcana.spell.behavior;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.orbisarcana.spell.SpellDefinition;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Everything a behavior needs on release. {@code params} already include milestones and the
 * mastery bonus to the primary stat; {@code power} is the charge multiplier (D4).
 */
public record CastContext(
        ServerPlayer caster,
        Identifier spellId,
        int castId,
        SpellDefinition spell,
        int mastery,
        Map<String, Double> params,
        double power,
        @Nullable Vec3 groundPoint,
        @Nullable LivingEntity target) {

    public ServerLevel level() {
        return caster.level();
    }

    public double param(String name) {
        return params.getOrDefault(name, 0.0);
    }

    /** Param scaled by charge power (use for damage/heal amounts, not for counts or durations). */
    public double scaled(String name) {
        return param(name) * power;
    }

    public int ticks(String name) {
        return (int) Math.round(param(name));
    }
}
