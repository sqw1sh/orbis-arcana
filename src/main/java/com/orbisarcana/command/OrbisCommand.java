package com.orbisarcana.command;

import java.util.Collection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.orbisarcana.OrbisArcana;
import com.orbisarcana.mana.ManaService;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /orbis} admin and debug commands (§15.8). */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class OrbisCommand {
    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("orbis")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("mana")
                        .then(Commands.literal("get")
                                .executes(ctx -> getMana(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> getMana(ctx, EntityArgument.getPlayer(ctx, "target")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> setMana(ctx, java.util.List.of(ctx.getSource().getPlayerOrException()), DoubleArgumentType.getDouble(ctx, "amount")))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> setMana(ctx, EntityArgument.getPlayers(ctx, "targets"), DoubleArgumentType.getDouble(ctx, "amount"))))))
                        .then(Commands.literal("fill")
                                .executes(ctx -> setMana(ctx, java.util.List.of(ctx.getSource().getPlayerOrException()), Double.MAX_VALUE))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> setMana(ctx, EntityArgument.getPlayers(ctx, "targets"), Double.MAX_VALUE))))));
    }

    private static int getMana(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        double mana = ManaService.mana(player);
        double max = ManaService.maxMana(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.orbisarcana.mana.get",
                player.getDisplayName(), String.format("%.1f", mana), String.format("%.0f", max),
                Component.translatable(ManaService.inCombat(player) ? "commands.orbisarcana.mana.combat" : "commands.orbisarcana.mana.calm")), false);
        return (int) mana;
    }

    private static int setMana(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, double amount) throws CommandSyntaxException {
        for (ServerPlayer p : players) {
            ManaService.setMana(p, amount);
        }
        int count = players.size();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.orbisarcana.mana.set", count), true);
        return count;
    }

    private OrbisCommand() {}
}
