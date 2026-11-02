package com.orbisarcana.compat.curios;

import java.util.List;

import com.orbisarcana.item.Accessories;
import com.orbisarcana.item.AccessoryItem;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * The only place that talks to Curios (§11.2, §17). Loaded only when Curios is present: a few times a second it tells
 * {@link Accessories} what the player wears.
 */
public final class CuriosCompat {
    private static final int CHECK_TICKS = 10;

    public static void init() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            if (event.getEntity() instanceof ServerPlayer p && p.tickCount % CHECK_TICKS == 0) update(p);
        });
    }

    /** Re-reads the player's curios (also used by tests). */
    public static void update(ServerPlayer p) {
        List<AccessoryItem> worn = CuriosApi.getCuriosInventory(p)
                .map(inv -> inv.findCurios(stack -> stack.getItem() instanceof AccessoryItem).stream()
                        .map(SlotResult::stack).map(s -> (AccessoryItem) s.getItem()).toList())
                .orElse(List.of());
        Accessories.setWorn(p, worn);
    }

    private CuriosCompat() {}
}
