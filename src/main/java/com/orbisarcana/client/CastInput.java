package com.orbisarcana.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.CastMode;
import com.orbisarcana.spell.SpellRegistry;
import com.orbisarcana.network.CastPayloads.CastRequest;
import com.orbisarcana.network.CastPayloads.CastStop;
import com.orbisarcana.progression.Loadout;

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

/**
 * Controls (§13.1): V casts (hold for CHARGED), R (hold) opens the radial menu, G the grimoire,
 * Alt + wheel cycles the current set, Alt + 1/2/3 switches sets.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class CastInput {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(OrbisArcana.id("main"));

    public static final KeyMapping CAST = new KeyMapping("key.orbisarcana.cast", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping CYCLE_MODIFIER = new KeyMapping("key.orbisarcana.cycle_modifier", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, CATEGORY);

    public static final KeyMapping RADIAL = new KeyMapping("key.orbisarcana.radial", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY);
    public static final KeyMapping GRIMOIRE = new KeyMapping("key.orbisarcana.grimoire", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CATEGORY);

    private static boolean wasDown;
    private static boolean releaseQueued;

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(CAST);
        event.register(CYCLE_MODIFIER);
        event.register(RADIAL);
        event.register(GRIMOIRE);
    }

    /** Runs before vanilla key handling so Alt + 1/2/3 does not also switch the hotbar slot. */
    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) return;
        if (CYCLE_MODIFIER.isDown()) {
            for (int page = 0; page < Loadout.PAGES; page++) {
                if (mc.options.keyHotbarSlots[page].consumeClick()) {
                    while (mc.options.keyHotbarSlots[page].consumeClick()) {}
                    ClientCastState.selectPage(mc.player, page);
                }
            }
        }
        while (RADIAL.consumeClick()) {
            mc.gui.setScreen(new RadialMenuScreen());
        }
        while (GRIMOIRE.consumeClick()) {
            mc.gui.setScreen(new GrimoireScreen());
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            wasDown = false;
            return;
        }
        boolean down = CAST.isDown() && mc.gui.screen() == null;
        autoRelease(mc);
        if (ClientConfig.PRESS_TO_CAST.get()) {
            // Accessibility: one press starts, the next releases or stops, nothing has to be held
            if (down && !wasDown) {
                var own = ClientCastState.ownCast();
                if (own != null) ClientPacketDistributor.sendToServer(new CastStop(own.castId(), false));
                else ClientCastState.selected(mc.player).ifPresent(spell -> ClientPacketDistributor.sendToServer(new CastRequest(spell)));
            }
            wasDown = down;
            return;
        }
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

    /** Auto-cast (§5.3): a charged cast is released as soon as it is full. Sent once per cast. */
    private static int autoReleased = -1;

    private static void autoRelease(Minecraft mc) {
        var own = ClientCastState.ownCast();
        if (own == null || own.castId() == autoReleased || !ClientConfig.AUTO_CAST.get()) return;
        boolean charged = SpellRegistry.get(mc.player.registryAccess(), own.spell())
                .map(d -> d.cast().mode() == CastMode.CHARGED).orElse(false);
        if (charged && mc.level.getGameTime() - own.startTick() >= own.durationTicks()) {
            autoReleased = own.castId();
            ClientPacketDistributor.sendToServer(new CastStop(own.castId(), false));
        }
    }

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null || !CYCLE_MODIFIER.isDown()) return;
        double delta = event.getScrollDeltaY();
        if (delta != 0) {
            ClientCastState.cycle(mc.player, delta > 0 ? -1 : 1);
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
