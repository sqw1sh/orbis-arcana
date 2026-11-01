package com.orbisarcana.network;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.network.CastPayloads.AltarAction;
import com.orbisarcana.network.CastPayloads.CastDenied;
import com.orbisarcana.network.CastPayloads.CastEnded;
import com.orbisarcana.network.CastPayloads.CastRequest;
import com.orbisarcana.network.CastPayloads.CastStarted;
import com.orbisarcana.network.CastPayloads.CastStop;
import com.orbisarcana.network.CastPayloads.CooldownSet;
import com.orbisarcana.network.CastPayloads.EchoState;
import com.orbisarcana.network.CastPayloads.HeatMark;
import com.orbisarcana.network.CastPayloads.ToggleState;
import com.orbisarcana.network.CastPayloads.LoadoutSelect;
import com.orbisarcana.network.CastPayloads.LoadoutSet;
import com.orbisarcana.network.CastPayloads.MilestoneToggle;
import com.orbisarcana.network.CastPayloads.RitualCircle;
import com.orbisarcana.network.CastPayloads.TalentPick;
import com.orbisarcana.school.AttunementService;
import com.orbisarcana.progression.KnowledgeService;
import com.orbisarcana.school.AltarService;
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

        r.playToServer(AltarAction.TYPE, AltarAction.CODEC, (msg, ctx) -> {
            AltarService.Action[] actions = AltarService.Action.values();
            if (ctx.player() instanceof ServerPlayer p && msg.action() >= 0 && msg.action() < actions.length) {
                AltarService.handle(p, msg.altar(), actions[msg.action()], msg.school());
            }
        });

        r.playToServer(TalentPick.TYPE, TalentPick.CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) AttunementService.pickTalent(p, msg.school(), msg.node());
        });

        r.playToClient(CastStarted.TYPE, CastStarted.CODEC);
        r.playToClient(CastEnded.TYPE, CastEnded.CODEC);
        r.playToClient(RitualCircle.TYPE, RitualCircle.CODEC);
        r.playToClient(CastDenied.TYPE, CastDenied.CODEC);
        r.playToClient(CooldownSet.TYPE, CooldownSet.CODEC);
        r.playToClient(EchoState.TYPE, EchoState.CODEC);
        r.playToClient(HeatMark.TYPE, HeatMark.CODEC);
        r.playToClient(ToggleState.TYPE, ToggleState.CODEC);
    }

    private ModNetwork() {}
}
