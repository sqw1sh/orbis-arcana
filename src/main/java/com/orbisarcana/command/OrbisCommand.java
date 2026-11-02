package com.orbisarcana.command;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.MasteryMath;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.mana.ManaService;
import com.orbisarcana.progression.KnowledgeService;
import com.orbisarcana.progression.KnownSpells;
import com.orbisarcana.school.Attunement;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /orbis} admin and debug commands (§15.8), permission level 2. */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class OrbisCommand {
    private static final DynamicCommandExceptionType UNKNOWN_SCHOOL = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.orbisarcana.school.unknown", String.valueOf(id)));

    private static final DynamicCommandExceptionType UNKNOWN_SPELL = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.orbisarcana.spell.unknown", String.valueOf(id)));

    private static final SuggestionProvider<CommandSourceStack> ALL_SPELLS =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(SpellRegistry.ids(ctx.getSource().registryAccess()), builder);

    private static final SuggestionProvider<CommandSourceStack> SCHOOLS =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(Schools.all(), builder);

    /** Spells known by the executing player. */
    private static final SuggestionProvider<CommandSourceStack> KNOWN_SPELLS = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        return SharedSuggestionProvider.suggestResource(
                player == null ? List.of() : KnowledgeService.knowledge(player).spells().keySet(), builder);
    };

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("orbis")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(mana())
                .then(spell())
                .then(school()));
    }

    // ---- mana ----

    private static LiteralArgumentBuilder<CommandSourceStack> mana() {
        return Commands.literal("mana")
                .then(Commands.literal("get")
                        .executes(ctx -> getMana(ctx, self(ctx)))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> getMana(ctx, EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("set")
                        .then(withTargets(Commands.argument("amount", DoubleArgumentType.doubleArg(0)),
                                (ctx, players) -> setMana(ctx, players, DoubleArgumentType.getDouble(ctx, "amount")))))
                .then(withTargets(Commands.literal("fill"), (ctx, players) -> setMana(ctx, players, Double.MAX_VALUE)));
    }

    private static int getMana(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        double mana = ManaService.mana(player);
        double max = ManaService.maxMana(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.orbisarcana.mana.get",
                player.getDisplayName(), String.format("%.1f", mana), String.format("%.0f", max),
                Component.translatable(ManaService.inCombat(player) ? "commands.orbisarcana.mana.combat" : "commands.orbisarcana.mana.calm")), false);
        return (int) mana;
    }

    private static int setMana(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, double amount) {
        players.forEach(p -> ManaService.setMana(p, amount));
        return feedback(ctx, "commands.orbisarcana.mana.set", players.size());
    }

    // ---- spell ----

    private static LiteralArgumentBuilder<CommandSourceStack> spell() {
        return Commands.literal("spell")
                .then(Commands.literal("learn")
                        .then(withTargets(Commands.argument("spell", IdentifierArgument.id()).suggests(ALL_SPELLS),
                                (ctx, players) -> learn(ctx, players, existingSpell(ctx)))))
                .then(Commands.literal("forget")
                        .then(withTargets(Commands.argument("spell", IdentifierArgument.id()).suggests(KNOWN_SPELLS),
                                (ctx, players) -> forget(ctx, players, IdentifierArgument.getId(ctx, "spell")))))
                .then(Commands.literal("mastery")
                        .then(Commands.argument("spell", IdentifierArgument.id()).suggests(KNOWN_SPELLS)
                                .then(withTargets(Commands.argument("level", IntegerArgumentType.integer(1, MasteryMath.MAX_LEVEL)),
                                        (ctx, players) -> mastery(ctx, players, IdentifierArgument.getId(ctx, "spell"), IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("list")
                        .executes(ctx -> listSpells(ctx, self(ctx)))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> listSpells(ctx, EntityArgument.getPlayer(ctx, "target")))));
    }

    private static Identifier existingSpell(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "spell");
        if (SpellRegistry.get(ctx.getSource().registryAccess(), id).isEmpty()) throw UNKNOWN_SPELL.create(id);
        return id;
    }

    private static int learn(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, Identifier spell) {
        int changed = (int) players.stream().filter(p -> KnowledgeService.learn(p, spell)).count();
        return feedback(ctx, "commands.orbisarcana.spell.learn", spell.toString(), changed);
    }

    private static int forget(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, Identifier spell) {
        int changed = (int) players.stream().filter(p -> KnowledgeService.forget(p, spell)).count();
        return feedback(ctx, "commands.orbisarcana.spell.forget", spell.toString(), changed);
    }

    private static int mastery(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, Identifier spell, int level) {
        int changed = (int) players.stream().filter(p -> KnowledgeService.setMastery(p, spell, level)).count();
        return feedback(ctx, "commands.orbisarcana.spell.mastery", spell.toString(), level, changed);
    }

    private static int listSpells(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        KnownSpells known = KnowledgeService.knowledge(player);
        String list = known.spells().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> e.getKey() + " [" + e.getValue().level() + "]")
                .collect(Collectors.joining(", "));
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.orbisarcana.spell.list",
                player.getDisplayName(), known.spells().size(), list.isEmpty() ? "-" : list), false);
        return known.spells().size();
    }

    // ---- school ----

    private static LiteralArgumentBuilder<CommandSourceStack> school() {
        return Commands.literal("school")
                .then(Commands.literal("set")
                        .then(withTargets(Commands.argument("school", IdentifierArgument.id()).suggests(SCHOOLS),
                                (ctx, players) -> setSchool(ctx, players, Optional.of(school(ctx)))))
                        .then(withTargets(Commands.literal("none"), (ctx, players) -> setSchool(ctx, players, Optional.empty()))))
                .then(Commands.literal("resonance")
                        .then(Commands.argument("school", IdentifierArgument.id()).suggests(SCHOOLS)
                                .then(withTargets(Commands.argument("level", IntegerArgumentType.integer(0, Attunement.MAX_RESONANCE)),
                                        (ctx, players) -> setResonance(ctx, players, school(ctx), IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(withTargets(Commands.literal("reset"), OrbisCommand::resetSchools))
                .then(Commands.literal("info")
                        .executes(ctx -> schoolInfo(ctx, self(ctx)))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> schoolInfo(ctx, EntityArgument.getPlayer(ctx, "target")))));
    }

    private static Identifier school(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "school");
        if (!Schools.exists(id)) throw UNKNOWN_SCHOOL.create(id);
        return id;
    }

    /** Admin override: no altar, cost, Dissonance or cooldown. */
    private static int setSchool(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, Optional<Identifier> school) {
        players.forEach(p -> {
            p.getData(ModAttachments.ATTUNEMENT).setDominant(school);
            p.syncData(ModAttachments.ATTUNEMENT);
        });
        return feedback(ctx, "commands.orbisarcana.school.set", school.map(Identifier::toString).orElse("-"), players.size());
    }

    private static int setResonance(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, Identifier school, int level) {
        players.forEach(p -> {
            p.getData(ModAttachments.ATTUNEMENT).school(school).setResonance(level);
            p.syncData(ModAttachments.ATTUNEMENT);
        });
        return feedback(ctx, "commands.orbisarcana.school.resonance", school.toString(), level, players.size());
    }

    private static int resetSchools(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        players.forEach(p -> {
            p.getData(ModAttachments.ATTUNEMENT).reset();
            p.syncData(ModAttachments.ATTUNEMENT);
        });
        return feedback(ctx, "commands.orbisarcana.school.reset", players.size());
    }

    private static int schoolInfo(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        Attunement att = player.getData(ModAttachments.ATTUNEMENT);
        String schools = att.schools().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> e.getKey().getPath() + " " + e.getValue().resonance() + "/" + Attunement.MAX_RESONANCE)
                .collect(Collectors.joining(", "));
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.orbisarcana.school.info",
                player.getDisplayName(), att.dominant().map(Identifier::toString).orElse("-"),
                schools.isEmpty() ? "-" : schools), false);
        return att.dominant().isPresent() ? 1 : 0;
    }

    // ---- helpers ----

    @FunctionalInterface
    private interface PlayersAction {
        int run(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException;
    }

    /** Adds "run on self" to {@code node} and an optional trailing {@code [targets]} argument. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withTargets(T node, PlayersAction action) {
        return node
                .executes(ctx -> action.run(ctx, List.of(self(ctx))))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> action.run(ctx, EntityArgument.getPlayers(ctx, "targets"))));
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static int feedback(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
        ctx.getSource().sendSuccess(() -> Component.translatable(key, args), true);
        return 1;
    }

    private OrbisCommand() {}
}
