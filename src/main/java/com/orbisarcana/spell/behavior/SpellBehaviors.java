package com.orbisarcana.spell.behavior;

import java.util.Optional;
import java.util.function.Supplier;

import com.orbisarcana.OrbisArcana;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Registry {@code orbisarcana:spell_behavior} (§16) and the built-in behaviors. Server-only, not synced. */
public final class SpellBehaviors {
    public static final ResourceKey<Registry<SpellBehavior>> KEY = ResourceKey.createRegistryKey(OrbisArcana.id("spell_behavior"));
    public static final Registry<SpellBehavior> REGISTRY = new RegistryBuilder<>(KEY).sync(false).create();

    private static final DeferredRegister<SpellBehavior> BEHAVIORS = DeferredRegister.create(KEY, OrbisArcana.MODID);

    public static final Supplier<SpellBehavior> PROJECTILE = BEHAVIORS.register("projectile", () -> ProjectileBehavior::release);
    public static final Supplier<SpellBehavior> ZONE = BEHAVIORS.register("zone", () -> ZoneBehavior::release);
    public static final Supplier<SpellBehavior> HEAL = BEHAVIORS.register("heal", () -> HealBehavior::release);
    public static final Supplier<SpellBehavior> DASH = BEHAVIORS.register("dash", () -> DashBehavior::release);
    public static final Supplier<SpellBehavior> VORTEX = BEHAVIORS.register("vortex", () -> VortexBehavior::release);
    public static final Supplier<SpellBehavior> CONE = BEHAVIORS.register("cone", () -> ConeBehavior.INSTANCE);
    public static final Supplier<SpellBehavior> PRISON = BEHAVIORS.register("prison", () -> PrisonBehavior::release);
    public static final Supplier<SpellBehavior> WAVE = BEHAVIORS.register("wave", () -> WaveBehavior::release);
    public static final Supplier<SpellBehavior> SHIELD = BEHAVIORS.register("shield", () -> ShieldBehavior::release);

    public static void register(IEventBus modBus) {
        modBus.addListener((NewRegistryEvent event) -> event.register(REGISTRY));
        BEHAVIORS.register(modBus);
    }

    public static Optional<SpellBehavior> get(Identifier id) {
        return REGISTRY.getOptional(id);
    }

    private SpellBehaviors() {}
}
