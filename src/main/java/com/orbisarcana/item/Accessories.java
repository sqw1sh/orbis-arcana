package com.orbisarcana.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.registry.ModAttributes;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Effects of worn accessories (§11.2). The Curios compat ({@code compat.curios}) reports what a player wears; this
 * class never touches Curios, so the rest of the mod runs without it. Two copies of one accessory do not stack.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class Accessories {
    public static final double SCHOOL_RING_POWER = 0.10;
    public static final double MANA_AMULET = 30;
    public static final double FLOW_AMULET = 0.25;
    public static final double FOCUS_AMULET = 0.15;
    public static final double ECHO_RING = 1.2;
    public static final double SCRIBE_RING = 0.10;
    private static final Identifier MODIFIER = OrbisArcana.id("accessories");

    private static final Map<UUID, Set<AccessoryItem>> WORN = new HashMap<>();

    /** Sets what {@code p} wears now and refreshes the attribute bonuses. */
    public static void setWorn(Player p, List<AccessoryItem> items) {
        Set<AccessoryItem> worn = Set.copyOf(items);
        Set<AccessoryItem> old = WORN.getOrDefault(p.getUUID(), Set.of());
        if (worn.equals(old)) return;
        if (worn.isEmpty()) WORN.remove(p.getUUID());
        else WORN.put(p.getUUID(), worn);
        Set<AccessoryItem.Kind> kinds = worn.stream().map(AccessoryItem::kind).collect(Collectors.toSet());
        set(p, ModAttributes.MAX_MANA, kinds.contains(AccessoryItem.Kind.MANA_AMULET) ? MANA_AMULET : 0);
        set(p, ModAttributes.MANA_REGEN, kinds.contains(AccessoryItem.Kind.FLOW_AMULET) ? FLOW_AMULET : 0);
        set(p, ModAttributes.CAST_STABILITY, kinds.contains(AccessoryItem.Kind.FOCUS_AMULET) ? FOCUS_AMULET : 0);
        set(p, ModAttributes.CAST_SPEED, kinds.contains(AccessoryItem.Kind.SCRIBE_RING) ? SCRIBE_RING : 0);
    }

    /** Spell power multiplier of a school from rings. */
    public static double schoolPowerMult(Player p, Identifier school) {
        return WORN.getOrDefault(p.getUUID(), Set.of()).stream()
                .anyMatch(a -> a.kind() == AccessoryItem.Kind.SCHOOL_RING && school.equals(a.school())) ? 1 + SCHOOL_RING_POWER : 1.0;
    }

    /** Echo charge multiplier from the Ring of Echo. */
    public static double echoGainMult(Player p) {
        return WORN.getOrDefault(p.getUUID(), Set.of()).stream().anyMatch(a -> a.kind() == AccessoryItem.Kind.ECHO_RING) ? ECHO_RING : 1.0;
    }

    private static void set(Player p, Holder<Attribute> attribute, double amount) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(MODIFIER);
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    /** A respawned player is a new entity without the transient modifiers: let the next check apply them again. */
    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        WORN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WORN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        WORN.clear();
    }

    private Accessories() {}
}
