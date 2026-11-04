package com.orbisarcana.client.sound;

import java.util.HashMap;
import java.util.Map;

import com.orbisarcana.core.CastMode;
import com.orbisarcana.network.CastPayloads.CastStarted;
import com.orbisarcana.registry.ModSounds;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Client side of the cast sounds (roadmap V6): the school's charge hum loops while a spell charges or channels,
 * follows the caster, swells with the charge and fades out when the cast ends. Releases, impacts and interrupts are
 * played by the server for everyone.
 */
public final class CastSounds {
    private static final Map<Integer, ChargeLoop> LOOPS = new HashMap<>();

    /** Any player's cast began: start its charge loop (instant casts have none). */
    public static void castStarted(CastStarted msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var def = SpellRegistry.get(mc.level.registryAccess(), msg.spell());
        if (def.isEmpty()) return;
        boolean channel = def.get().cast().mode() == CastMode.CHANNEL;
        if (msg.durationTicks() <= 0 && !channel) return;
        Entity caster = mc.level.getEntity(msg.casterId());
        if (caster == null) return;
        ChargeLoop loop = new ChargeLoop(ModSounds.charge(def.get().school()), caster, channel ? 0 : msg.durationTicks());
        LOOPS.put(msg.castId(), loop);
        mc.getSoundManager().play(loop);
    }

    public static void castEnded(int castId) {
        ChargeLoop loop = LOOPS.remove(castId);
        if (loop != null) loop.ending = true;
    }

    /** A click of the mod's interface. */
    public static void ui(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.6f));
    }

    private static final class ChargeLoop extends AbstractTickableSoundInstance {
        private final Entity caster;
        private final int chargeTicks;
        private int age;
        private boolean ending;

        ChargeLoop(SoundEvent sound, Entity caster, int chargeTicks) {
            super(sound, SoundSource.PLAYERS, RandomSource.create());
            this.caster = caster;
            this.chargeTicks = chargeTicks;
            looping = true;
            delay = 0;
            volume = 0.01f;
            pitch = 0.9f;
            follow();
        }

        @Override
        public void tick() {
            if (caster.isRemoved()) ending = true;
            age++;
            if (ending) {
                volume -= 0.12f;
                if (volume <= 0.01f) stop();
                return;
            }
            // swell over the first ~0.4 s and rise a little in pitch as the charge completes
            float charge = chargeTicks > 0 ? Math.min(1f, (float) age / chargeTicks) : 0.6f;
            volume = Math.min(1f, age / 8f) * (0.55f + 0.35f * charge);
            pitch = 0.9f + 0.2f * charge;
            follow();
        }

        private void follow() {
            x = caster.getX();
            y = caster.getY() + caster.getBbHeight() * 0.6;
            z = caster.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    private CastSounds() {}
}
