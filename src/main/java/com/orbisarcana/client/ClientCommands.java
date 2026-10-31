package com.orbisarcana.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.orbisarcana.OrbisArcana;
import com.orbisarcana.client.circle.ClientCircles;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client-only debug commands: {@code /orbisc circles [count]} spawns a local circle stand and reports
 * the average FPS while it is visible (§19.1 budget: 30 circles without a frame drop).
 */
@EventBusSubscriber(modid = OrbisArcana.MODID, value = Dist.CLIENT)
public final class ClientCommands {
    private static final int MEASURE_TICKS = 200;
    private static int measureLeft;
    private static long fpsSum;
    private static int fpsSamples;
    private static int measuredCount;

    @SubscribeEvent
    static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("orbisc")
                .then(Commands.literal("circles")
                        .executes(ctx -> circles(ctx, 30))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 256))
                                .executes(ctx -> circles(ctx, IntegerArgumentType.getInteger(ctx, "count"))))));
    }

    private static int circles(CommandContext<CommandSourceStack> ctx, int count) {
        int spawned = ClientCircles.spawnDemo(count);
        measuredCount = spawned;
        measureLeft = MEASURE_TICKS;
        fpsSum = 0;
        fpsSamples = 0;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.orbisarcana.circles.spawned", spawned), false);
        return spawned;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (measureLeft <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        fpsSum += mc.getFps();
        fpsSamples++;
        if (--measureLeft == 0 && mc.player != null && fpsSamples > 0) {
            mc.player.sendSystemMessage(Component.translatable("commands.orbisarcana.circles.fps", measuredCount, fpsSum / fpsSamples));
        }
    }

    private ClientCommands() {}
}
