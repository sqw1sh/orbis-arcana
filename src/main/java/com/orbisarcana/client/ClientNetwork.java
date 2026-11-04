package com.orbisarcana.client;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.circle.ClientCircles;
import com.orbisarcana.core.ActiveCast.Outcome;
import com.orbisarcana.core.CastDenial;
import com.orbisarcana.network.CastPayloads.CastDenied;
import com.orbisarcana.network.CastPayloads.CastEnded;
import com.orbisarcana.network.CastPayloads.CastStarted;
import com.orbisarcana.network.CastPayloads.CastStop;
import com.orbisarcana.network.CastPayloads.CooldownSet;
import com.orbisarcana.network.CastPayloads.EchoState;
import com.orbisarcana.network.CastPayloads.HeatMark;
import com.orbisarcana.network.CastPayloads.ToggleState;
import com.orbisarcana.network.CastPayloads.RitualCircle;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client handlers for cast packets. */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class ClientNetwork {
    @SubscribeEvent
    static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(CastStarted.TYPE, (msg, ctx) -> {
            ClientCircles.start(msg);
            com.orbisarcana.client.anim.CasterAnimations.castStarted(msg);
            com.orbisarcana.client.sound.CastSounds.castStarted(msg);
            var player = Minecraft.getInstance().player;
            if (player == null || msg.casterId() != player.getId()) return;
            ClientCastState.setOwnCast(new ClientCastState.OwnCast(msg.castId(), msg.spell(), msg.startTick(), msg.durationTicks()));
            // Key already let go before the server answered: release right away
            if (CastInput.releaseQueued()) {
                ClientPacketDistributor.sendToServer(new CastStop(msg.castId(), false));
            }
        });
        event.register(CastEnded.TYPE, (msg, ctx) -> {
            Outcome[] outcomes = Outcome.values();
            if (msg.outcome() >= 0 && msg.outcome() < outcomes.length) {
                ClientCircles.end(msg.castId(), outcomes[msg.outcome()]);
                com.orbisarcana.client.anim.CasterAnimations.castEnded(msg.castId(), outcomes[msg.outcome()]);
                com.orbisarcana.client.sound.CastSounds.castEnded(msg.castId());
            }
            var own = ClientCastState.ownCast();
            if (own != null && own.castId() == msg.castId()) ClientCastState.setOwnCast(null);
        });
        event.register(RitualCircle.TYPE, (msg, ctx) -> ClientCircles.startRitual(msg));
        event.register(com.orbisarcana.network.VfxPlay.TYPE, (msg, ctx) -> com.orbisarcana.client.vfx.VfxManager.spawn(msg.effect(),
                new net.minecraft.world.phys.Vec3(msg.x(), msg.y(), msg.z()), new net.minecraft.world.phys.Vec3(msg.dx(), msg.dy(), msg.dz()), msg.tint(), msg.scale(), msg.attach(),
                new org.joml.Vector3f(msg.sx(), msg.sy(), msg.sz()), msg.density()));
        event.register(ToggleState.TYPE, (msg, ctx) -> {
            ClientCircles.toggle(msg.playerId(), msg.spell(), msg.on(), msg.mastery());
            ClientCastState.setToggle(msg.playerId(), msg.spell(), msg.on());
        });
        event.register(HeatMark.TYPE, (msg, ctx) -> ClientCircles.heatMark(msg.entityId(), msg.stacks(), msg.school()));
        event.register(EchoState.TYPE, (msg, ctx) -> ClientCastState.setEcho(msg.school(), msg.stacks(), msg.max()));
        event.register(CastDenied.TYPE, (msg, ctx) -> {
            CastDenial[] values = CastDenial.values();
            if (msg.reason() < 0 || msg.reason() >= values.length) return;
            CastDenial reason = values[msg.reason()];
            ClientCastState.deny(reason);
            if (reason != CastDenial.GLOBAL_COOLDOWN && reason != CastDenial.ALREADY_CASTING) {
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.orbisarcana.registry.ModSounds.CAST_FAIL.get(), 1f));
            }
        });
        event.register(CooldownSet.TYPE, (msg, ctx) ->
                ClientCastState.setCooldown(msg.spell(), new ClientCastState.Cooldown(msg.fullAt(), msg.cooldownTicks())));
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCastState.reset();
    }

    private ClientNetwork() {}
}
