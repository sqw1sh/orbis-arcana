package com.orbisarcana.school;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.ActiveCast;
import com.orbisarcana.core.SchoolRules;
import com.orbisarcana.network.CastPayloads.CastEnded;
import com.orbisarcana.network.CastPayloads.RitualCircle;
import com.orbisarcana.network.Net;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.registry.ModBlocks;
import com.orbisarcana.registry.ModItems;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Altar actions (§10.6): the first binding and unbinding are instant; a switch is a 10 s ritual paid
 * up front. Leaving the circle or taking damage cancels it and refunds half of the cost.
 */
@EventBusSubscriber(modid = OrbisArcana.MODID)
public final class AltarService {
    public static final double USE_DISTANCE = 6.0;
    /** The ritual circle around the altar. */
    public static final double RITUAL_RADIUS = 3.0;

    public enum Action {
        BIND,
        UNBIND
    }

    /** {@code circleId}: negative id of the world seal shown to clients (never clashes with cast ids). */
    private record Ritual(BlockPos altar, Identifier school, long startTick, SchoolRules.SwitchCost paid, int circleId) {}

    /** Far below local demo/devshot ids (-1000...). */
    private static int nextCircleId = -1_000_000;

    private static final Map<UUID, Ritual> RITUALS = new HashMap<>();

    public static void handle(ServerPlayer player, BlockPos altar, Action action, Optional<Identifier> school) {
        if (!player.level().getBlockState(altar).is(ModBlocks.RESONANCE_ALTAR.get())) return;
        if (player.distanceToSqr(Vec3.atCenterOf(altar)) > USE_DISTANCE * USE_DISTANCE) return;
        if (RITUALS.containsKey(player.getUUID())) {
            player.sendOverlayMessage(Component.translatable("message.orbisarcana.altar.busy").withStyle(ChatFormatting.RED));
            return;
        }
        switch (action) {
            case UNBIND -> {
                if (AttunementService.attunement(player).dominant().isPresent()) AttunementService.unbind(player);
            }
            case BIND -> school.filter(Schools::exists).ifPresent(s -> bind(player, altar, s));
        }
    }

    private static void bind(ServerPlayer player, BlockPos altar, Identifier school) {
        if (!knowsSpellOf(player, school)) {
            fail(player, Component.translatable("message.orbisarcana.altar.no_spell", AttunementService.schoolName(school)));
            return;
        }
        Optional<Component> problem = AttunementService.bindProblem(player, school);
        if (problem.isPresent()) {
            fail(player, problem.get());
            return;
        }
        SchoolRules.SwitchCost cost = AttunementService.costFor(player, school);
        if (cost.equals(SchoolRules.SwitchCost.FREE)) {
            AttunementService.bind(player, school);
            effects(player, altar, true);
            return;
        }
        if (!player.hasInfiniteMaterials()) {
            if (countInk(player) < cost.ink() || player.experienceLevel < cost.xpLevels()) {
                fail(player, Component.translatable("message.orbisarcana.altar.cost", cost.ink(), cost.xpLevels()));
                return;
            }
            takeInk(player, cost.ink());
            player.giveExperienceLevels(-cost.xpLevels());
        }
        Ritual ritual = new Ritual(altar, school, player.level().getGameTime(), cost, nextCircleId--);
        RITUALS.put(player.getUUID(), ritual);
        Net.toPlayerAndNearby(player, new RitualCircle(ritual.circleId(), player.getId(), altar, school, ritual.startTick(), SchoolRules.RITUAL_TICKS));
        player.sendSystemMessage(Component.translatable("message.orbisarcana.altar.ritual_start", AttunementService.schoolName(school),
                SchoolRules.RITUAL_TICKS / 20).withStyle(ChatFormatting.LIGHT_PURPLE));
        player.level().playSound(null, altar, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 0.8f);
    }

    public static boolean inRitual(ServerPlayer player) {
        return RITUALS.containsKey(player.getUUID());
    }

    /** Rituals tick with the server, independent of player ticking. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (RITUALS.isEmpty()) return;
        for (UUID id : List.copyOf(RITUALS.keySet())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            Ritual ritual = RITUALS.get(id);
            if (player == null) {
                RITUALS.remove(id);
            } else if (ritual != null) {
                tickRitual(player, ritual);
            }
        }
    }

    private static void tickRitual(ServerPlayer player, Ritual ritual) {
        long elapsed = player.level().getGameTime() - ritual.startTick();
        Vec3 centre = Vec3.atBottomCenterOf(ritual.altar());
        if (!player.isAlive() || !player.level().getBlockState(ritual.altar()).is(ModBlocks.RESONANCE_ALTAR.get())
                || player.position().distanceTo(centre) > RITUAL_RADIUS + 1) {
            cancel(player, ritual);
            return;
        }
        if (elapsed % 10 == 0) {
            player.level().sendParticles(ParticleTypes.ENCHANT, centre.x, centre.y + 1.5, centre.z, 6, 0.4, 0.4, 0.4, 0.5);
        }
        if (elapsed % 20 == 0 && elapsed < SchoolRules.RITUAL_TICKS) {
            player.sendOverlayMessage(Component.translatable("message.orbisarcana.altar.ritual_progress",
                    (SchoolRules.RITUAL_TICKS - elapsed) / 20).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (elapsed >= SchoolRules.RITUAL_TICKS) {
            RITUALS.remove(player.getUUID());
            AttunementService.bind(player, ritual.school());
            effects(player, ritual.altar(), true);
            Net.toPlayerAndNearby(player, new CastEnded(ritual.circleId(), player.getId(), ActiveCast.Outcome.RELEASED.ordinal()));
        }
    }

    /** Taking damage in the circle breaks the ritual (§10.6). */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getHealthDamage() > 0) {
            Ritual ritual = RITUALS.get(player.getUUID());
            if (ritual != null) cancel(player, ritual);
        }
    }

    private static void cancel(ServerPlayer player, Ritual ritual) {
        RITUALS.remove(player.getUUID());
        Net.toPlayerAndNearby(player, new CastEnded(ritual.circleId(), player.getId(), ActiveCast.Outcome.INTERRUPTED.ordinal()));
        if (!player.hasInfiniteMaterials()) {
            int ink = ritual.paid().ink() / 2;
            // Drops what does not fit
            if (ink > 0) player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.AETHER_INK.get(), ink), Prediction.SERVER_ONLY);
            player.giveExperienceLevels(ritual.paid().xpLevels() / 2);
        }
        fail(player, Component.translatable("message.orbisarcana.altar.ritual_broken"));
        effects(player, ritual.altar(), false);
    }

    private static boolean knowsSpellOf(ServerPlayer player, Identifier school) {
        for (Identifier spell : player.getData(ModAttachments.KNOWLEDGE).spells().keySet()) {
            if (SpellRegistry.get(player.registryAccess(), spell).filter(d -> d.school().equals(school)).isPresent()) return true;
        }
        return false;
    }

    private static int countInk(ServerPlayer player) {
        int n = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.AETHER_INK.get())) n += inv.getItem(i).getCount();
        }
        return n;
    }

    private static void takeInk(ServerPlayer player, int amount) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(ModItems.AETHER_INK.get())) {
                int take = Math.min(amount, stack.getCount());
                stack.shrink(take);
                amount -= take;
            }
        }
    }

    private static void fail(ServerPlayer player, Component message) {
        player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.RED));
    }

    private static void effects(ServerPlayer player, BlockPos altar, boolean success) {
        Vec3 c = Vec3.atCenterOf(altar);
        player.level().sendParticles(success ? ParticleTypes.END_ROD : ParticleTypes.SMOKE, c.x, c.y + 0.8, c.z, 30, 0.6, 0.6, 0.6, 0.05);
        player.level().playSound(null, altar, success ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1f, 1f);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Ritual ritual = RITUALS.get(player.getUUID());
            if (ritual != null) cancel(player, ritual);
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        RITUALS.clear();
    }

    private AltarService() {}
}
