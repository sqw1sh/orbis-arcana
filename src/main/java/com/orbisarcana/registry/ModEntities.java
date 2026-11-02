package com.orbisarcana.registry;

import com.orbisarcana.entity.GuardianTreant;
import com.orbisarcana.entity.ShadowDecoy;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.entity.SpellProjectile;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(OrbisArcana.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SpellProjectile>> SPELL_PROJECTILE = ENTITIES.registerEntityType(
            "spell_projectile", SpellProjectile::new, MobCategory.MISC,
            b -> b.sized(0.4f, 0.4f).clientTrackingRange(4).updateInterval(10).noSave());

    /** Thorn Seed guardian (§14.7): not saved, it only lives for its spell. */
    public static final DeferredHolder<EntityType<?>, EntityType<GuardianTreant>> GUARDIAN_TREANT = ENTITIES.registerEntityType(
            "guardian_treant", GuardianTreant::new, MobCategory.MISC,
            b -> b.sized(1.4f, 2.7f).clientTrackingRange(10).noSave());

    /** Shadow Step decoy (§14.9): not saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<ShadowDecoy>> SHADOW_DECOY = ENTITIES.registerEntityType(
            "shadow_decoy", ShadowDecoy::new, MobCategory.MISC,
            b -> b.sized(0.6f, 1.8f).clientTrackingRange(10).noSave());

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> {
            event.put(GUARDIAN_TREANT.get(), IronGolem.createAttributes().build());
            event.put(SHADOW_DECOY.get(), ShadowDecoy.createAttributes().build());
        });
    }

    private ModEntities() {}
}
