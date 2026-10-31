package com.orbisarcana.registry;

import com.orbisarcana.OrbisArcana;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/**
 * One damage type per school (§15.7), defined in {@code data/orbisarcana/damage_type/<school>_magic.json}.
 * They are not tagged {@code is_fire}, so fire-immune mobs still take fire magic damage.
 */
public final class ModDamageTypes {
    public static ResourceKey<DamageType> forSchool(Identifier school) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, OrbisArcana.id(school.getPath() + "_magic"));
    }

    public static DamageSource source(Entity victimLevelHolder, Identifier school, Entity direct, Entity caster) {
        return victimLevelHolder.damageSources().source(forSchool(school), direct, caster);
    }

    private ModDamageTypes() {}
}
