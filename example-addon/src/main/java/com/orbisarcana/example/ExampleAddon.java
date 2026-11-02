package com.orbisarcana.example;

import com.orbisarcana.api.OrbisArcanaAPI;
import com.orbisarcana.api.School;
import com.orbisarcana.spell.behavior.SpellBehavior;
import com.orbisarcana.spell.behavior.SpellBehaviors;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Example addon for Orbis Arcana (§16). It adds:
 * <ul>
 *   <li>the school of Blood, whose Echo "Bloodlust" heals the caster on every hit ({@link BloodlustEcho});</li>
 *   <li>the behavior {@code orbisexample:life_drain} ({@link LifeDrainBehavior});</li>
 *   <li>the spell Blood Bolt, pure data: {@code data/orbisexample/orbisarcana/spell/blood_bolt.json}, its circle in
 *       {@code assets/orbisexample/orbisarcana/circle/} and its texts in the lang files.</li>
 * </ul>
 */
@Mod(ExampleAddon.MODID)
public final class ExampleAddon {
    public static final String MODID = "orbisexample";
    public static final Identifier BLOOD = id("blood");
    public static final Identifier BLOOD_BOLT = id("blood_bolt");

    private static final DeferredRegister<School> SCHOOLS = DeferredRegister.create(OrbisArcanaAPI.SCHOOL_REGISTRY, MODID);
    private static final DeferredRegister<SpellBehavior> BEHAVIORS = DeferredRegister.create(SpellBehaviors.KEY, MODID);

    static {
        SCHOOLS.register("blood", () -> new School(0xB01030, false, new BloodlustEcho()));
        BEHAVIORS.register("life_drain", () -> LifeDrainBehavior::release);
    }

    public ExampleAddon(IEventBus modBus) {
        SCHOOLS.register(modBus);
        BEHAVIORS.register(modBus);
        ExampleGameTests.register(modBus);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
