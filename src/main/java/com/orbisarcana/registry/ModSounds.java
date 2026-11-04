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
 * Sounds of the mod (roadmap V6), all synthesised by {@code tools/sfx.cjs} from {@code art/sounds/*.json}: per school a
 * charge loop, a release and an impact (three variants each); cast interrupt and denial, toggles, interface, talent
 * learning, mastery level-ups and milestones. Each is its own event with a subtitle, so a resource pack can replace any.
 * Addon schools use the generic events.
 */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, OrbisArcana.MODID);

    /** Generic charge loop (addon schools). */
    public static final Supplier<SoundEvent> CAST_CHARGE = sound("cast.charge");
    public static final Supplier<SoundEvent> CAST_RELEASE = sound("cast.release");
    public static final Supplier<SoundEvent> CAST_IMPACT = sound("cast.impact");
    public static final Supplier<SoundEvent> CAST_INTERRUPT = sound("cast.interrupt");
    public static final Supplier<SoundEvent> CAST_FAIL = sound("cast.fail");
    public static final Supplier<SoundEvent> TOGGLE_ON = sound("toggle.on");
    public static final Supplier<SoundEvent> TOGGLE_OFF = sound("toggle.off");
    public static final Supplier<SoundEvent> UI_CLICK = sound("ui.click");
    public static final Supplier<SoundEvent> UI_TAB = sound("ui.tab");
    public static final Supplier<SoundEvent> UI_ASSIGN = sound("ui.assign");
    public static final Supplier<SoundEvent> TALENT_LEARN = sound("talent.learn");
    public static final Supplier<SoundEvent> MASTERY_UP = sound("mastery.up");
    public static final Supplier<SoundEvent> MASTERY_MILESTONE = sound("mastery.milestone");

    private static final Map<Identifier, Supplier<SoundEvent>> CHARGE = new HashMap<>(), RELEASE = new HashMap<>(), IMPACT = new HashMap<>();

    static {
        for (Identifier school : Schools.BUILT_IN) {
            String s = school.getPath();
            CHARGE.put(school, sound("cast.charge." + s));
            RELEASE.put(school, sound("cast.release." + s));
            IMPACT.put(school, sound("cast.impact." + s));
        }
    }

    private static Supplier<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(OrbisArcana.id(name)));
    }

    /** Looping hum while a spell of the school charges or channels. */
    public static SoundEvent charge(Identifier school) {
        return CHARGE.getOrDefault(school, CAST_CHARGE).get();
    }

    public static SoundEvent release(Identifier school) {
        return RELEASE.getOrDefault(school, CAST_RELEASE).get();
    }

    public static SoundEvent impact(Identifier school) {
        return IMPACT.getOrDefault(school, CAST_IMPACT).get();
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private ModSounds() {}
}
