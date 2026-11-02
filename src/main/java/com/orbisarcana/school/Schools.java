package com.orbisarcana.school;

import java.util.ArrayList;
import java.util.List;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.api.OrbisArcanaAPI;
import com.orbisarcana.api.School;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * Schools (§10.1): the {@code orbisarcana:school} registry (§16, addons add their own) and the ids of the 10 built-in
 * ones. Built-in Echoes are implemented in {@link EchoService}; addon schools bring theirs in {@link School#echo()}.
 */
public final class Schools {
    public static final Registry<School> REGISTRY = new RegistryBuilder<>(OrbisArcanaAPI.SCHOOL_REGISTRY).sync(true).create();
    private static final DeferredRegister<School> SCHOOLS = DeferredRegister.create(OrbisArcanaAPI.SCHOOL_REGISTRY, OrbisArcana.MODID);

    public static final Identifier FIRE = builtIn("fire", 0xFF6A2A, false);
    public static final Identifier EARTH = builtIn("earth", 0xC09050, false);
    public static final Identifier WATER = builtIn("water", 0x3FA9F5, false);
    public static final Identifier AIR = builtIn("air", 0xC8F5EE, false);
    public static final Identifier ICE = builtIn("ice", 0x9EE4FF, false);
    public static final Identifier LIGHTNING = builtIn("lightning", 0xFFE95A, false);
    public static final Identifier NATURE = builtIn("nature", 0x6BD66B, false);
    public static final Identifier LIGHT = builtIn("light", 0xFFF2B0, true);
    public static final Identifier DARK = builtIn("dark", 0x9B5CD6, true);
    public static final Identifier ASTRAL = builtIn("astral", 0x7A8CFF, true);

    /** The built-in schools, in display order. */
    public static final List<Identifier> BUILT_IN = List.of(FIRE, EARTH, WATER, AIR, ICE, LIGHTNING, NATURE, LIGHT, DARK, ASTRAL);

    private static Identifier builtIn(String name, int color, boolean higher) {
        SCHOOLS.register(name, () -> new School(color, higher));
        return OrbisArcana.id(name);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((NewRegistryEvent event) -> event.register(REGISTRY));
        SCHOOLS.register(modBus);
    }

    /** Every registered school: built-in ones first, then addon ones in registration order. */
    public static List<Identifier> all() {
        List<Identifier> ids = new ArrayList<>(BUILT_IN);
        for (School s : REGISTRY) {
            Identifier id = REGISTRY.getKey(s);
            if (id != null && !ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    /** Primary circle colour of a school (§5.4: the school sets the colour, rarity only the outline). */
    public static int color(Identifier id) {
        return REGISTRY.getOptional(id).map(School::color).orElse(0xFFFFFF);
    }

    public static boolean exists(Identifier id) {
        return REGISTRY.containsKey(id);
    }

    public static boolean isHigher(Identifier id) {
        return REGISTRY.getOptional(id).map(School::higher).orElse(false);
    }

    /** Lang key of the school's name: {@code school.<namespace>.<path>}. */
    public static String nameKey(Identifier id) {
        return "school." + id.getNamespace() + "." + id.getPath();
    }

    private Schools() {}
}
