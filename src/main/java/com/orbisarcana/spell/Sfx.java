package com.orbisarcana.spell;

import java.util.Map;
import java.util.WeakHashMap;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.registry.ModSounds;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Spell sounds that follow from what happens (roadmap V8): every hit of a school's magic plays the school's short hit
 * sound on the victim (at most every few ticks per victim, so channels and volleys do not stack), and signature
 * moments of a spell play the school's heavy impact.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class Sfx {
    private static final int HIT_GAP_TICKS = 5;
    private static final Map<LivingEntity, Long> LAST_HIT = new WeakHashMap<>();

    /** The school's heavy impact at {@code at}; {@code scale} above 1 is louder and carries farther. */
    public static void impact(ServerLevel level, Vec3 at, Identifier school, float scale) {
        level.playSound(null, at.x, at.y, at.z, ModSounds.impact(school), SoundSource.PLAYERS, 1.6f * scale,
                0.95f + level.getRandom().nextFloat() * 0.1f);
    }

    /** A spell's own heavy impact (sound event {@code id}) at {@code at}. */
    public static void impactSound(ServerLevel level, Vec3 at, Identifier id, float scale) {
        level.playSound(null, at.x, at.y, at.z, event(id), SoundSource.PLAYERS, 1.6f * scale, 0.95f + level.getRandom().nextFloat() * 0.1f);
    }

    /** The sound event of {@code id} (a direct holder: the client looks the sound up in sounds.json by id). */
    public static net.minecraft.sounds.SoundEvent event(Identifier id) {
        return net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id);
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || event.getHealthDamage() <= 0) return;
        var key = event.getSource().typeHolder().unwrapKey();
        if (key.isEmpty()) return;
        Identifier id = key.get().identifier();
        if (!id.getNamespace().equals(OrbisArcana.MODID) || !id.getPath().endsWith("_magic")) return;
        long now = level.getGameTime();
        Long last = LAST_HIT.get(victim);
        if (last != null && now - last < HIT_GAP_TICKS) return;
        LAST_HIT.put(victim, now);
        Identifier school = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - "_magic".length()));
        level.playSound(null, victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(), ModSounds.hit(school), SoundSource.PLAYERS,
                1f, 0.92f + level.getRandom().nextFloat() * 0.16f);
    }

    private Sfx() {}
}
