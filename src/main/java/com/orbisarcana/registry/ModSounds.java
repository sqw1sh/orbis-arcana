package com.orbisarcana.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.school.Schools;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sounds of casting (§S9). Each is its own event with a subtitle; {@code sounds.json} points them at vanilla sounds for
 * now, so a resource pack can replace any of them without touching vanilla ones.
 */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, OrbisArcana.MODID);

    public static final Supplier<SoundEvent> CAST_CHARGE = sound("cast.charge");
    public static final Supplier<SoundEvent> CAST_RELEASE = sound("cast.release");
    public static final Supplier<SoundEvent> CAST_INTERRUPT = sound("cast.interrupt");
    public static final Supplier<SoundEvent> CAST_FAIL = sound("cast.fail");
    public static final Supplier<SoundEvent> TOGGLE_ON = sound("toggle.on");
    public static final Supplier<SoundEvent> TOGGLE_OFF = sound("toggle.off");

    /** Release sound of each built-in school; addon schools use {@link #CAST_RELEASE}. */
    private static final Map<Identifier, Supplier<SoundEvent>> RELEASE_BY_SCHOOL = new HashMap<>();

    static {
        for (Identifier school : Schools.BUILT_IN) RELEASE_BY_SCHOOL.put(school, sound("cast.release." + school.getPath()));
    }

    private static Supplier<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(OrbisArcana.id(name)));
    }

    public static SoundEvent release(Identifier school) {
        return RELEASE_BY_SCHOOL.getOrDefault(school, CAST_RELEASE).get();
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private ModSounds() {}
}
