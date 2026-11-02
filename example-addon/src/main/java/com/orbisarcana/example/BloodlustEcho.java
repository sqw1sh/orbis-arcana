package com.orbisarcana.example;

import com.orbisarcana.api.SchoolEcho;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Echo of the Blood school, "Bloodlust": every hit of a Blood spell heals the caster 1 HP, 2 at tier 2. */
final class BloodlustEcho implements SchoolEcho {
    @Override
    public void onSpellHit(ServerPlayer caster, LivingEntity target, int castId, int tier) {
        caster.heal(tier);
        caster.level().sendParticles(ParticleTypes.DAMAGE_INDICATOR, caster.getX(), caster.getY() + 1, caster.getZ(), 3, 0.3, 0.4, 0.3, 0.05);
    }
}
