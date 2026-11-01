package com.orbisarcana.network;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.network.CastPayloads.CastDenied;
import com.orbisarcana.network.CastPayloads.CastEnded;
import com.orbisarcana.network.CastPayloads.CastRequest;
import com.orbisarcana.network.CastPayloads.CastStarted;
import com.orbisarcana.network.CastPayloads.CastStop;
import com.orbisarcana.network.CastPayloads.CooldownSet;
import com.orbisarcana.network.CastPayloads.LoadoutSelect;
import com.orbisarcana.network.CastPayloads.LoadoutSet;
import com.orbisarcana.network.CastPayloads.MilestoneToggle;
import com.orbisarcana.progression.KnowledgeService;
import com.orbisarcana.spell.CastService;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration. Client handlers are registered separately in {@code client.ClientNetwork}. */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class ModNetwork {
    public static final String VERSION = "1";

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION);

        r.playToServer(CastRequest.TYPE, CastRequest.CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) CastService.request(p, msg.spell());
        });
        r.playToServer(CastStop.TYPE, CastStop.CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) CastService.stop(p, msg.castId(), msg.cancel());
        });

        r.playToServer(LoadoutSet.TYPE, LoadoutSet.CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) KnowledgeService.setSlot(p, msg.page(), msg.slot(), msg.spell());
        });
        r.playToServer(LoadoutSelect.TYPE, LoadoutSelect.CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) KnowledgeService.select(p, msg.page(), msg.slot());
        });

        r.playToServer(MilestoneToggle.TYPE, MilestoneToggle.CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) KnowledgeService.setMilestoneDisabled(p, msg.spell(), msg.level(), msg.disabled());
        });

        r.playToClient(CastStarted.TYPE, CastStarted.CODEC);
        r.playToClient(CastEnded.TYPE, CastEnded.CODEC);
        r.playToClient(CastDenied.TYPE, CastDenied.CODEC);
        r.playToClient(CooldownSet.TYPE, CooldownSet.CODEC);
    }

    private ModNetwork() {}
}
