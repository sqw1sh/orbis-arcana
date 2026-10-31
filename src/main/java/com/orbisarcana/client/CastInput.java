package com.orbisarcana.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.orbisarcana.OrbisArcana;
import com.orbisarcana.network.CastPayloads.CastRequest;
import com.orbisarcana.network.CastPayloads.CastStop;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** Cast key (hold for CHARGED) and modifier + mouse wheel to switch spells (§13.1). */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class CastInput {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(OrbisArcana.id("main"));

    public static final KeyMapping CAST = new KeyMapping("key.orbisarcana.cast", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping CYCLE_MODIFIER = new KeyMapping("key.orbisarcana.cycle_modifier", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, CATEGORY);

    private static boolean wasDown;
    private static boolean releaseQueued;

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(CAST);
        event.register(CYCLE_MODIFIER);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            wasDown = false;
            return;
        }
        boolean down = CAST.isDown() && mc.gui.screen() == null;
        if (down && !wasDown) {
            releaseQueued = false;
            if (ClientCastState.ownCast() == null) {
                ClientCastState.selected(mc.player).ifPresent(spell -> ClientPacketDistributor.sendToServer(new CastRequest(spell)));
            }
        } else if (!down && wasDown) {
            var own = ClientCastState.ownCast();
            if (own != null) {
                ClientPacketDistributor.sendToServer(new CastStop(own.castId(), false));
            } else {
                releaseQueued = true;
            }
        }
        wasDown = down;
    }

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null || !CYCLE_MODIFIER.isDown()) return;
        double delta = event.getScrollDeltaY();
        if (delta != 0) {
            ClientCastState.cycle(delta > 0 ? -1 : 1);
            event.setCanceled(true);
        }
    }

    /** True once if the key was released before the server confirmed the cast start. */
    static boolean releaseQueued() {
        boolean q = releaseQueued;
        releaseQueued = false;
        return q;
    }

    private CastInput() {}
}
