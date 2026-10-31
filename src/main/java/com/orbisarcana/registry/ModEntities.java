package com.orbisarcana.registry;

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

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    private ModEntities() {}
}
