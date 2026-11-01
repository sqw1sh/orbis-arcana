package com.orbisarcana.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.core.ActiveCast.Outcome;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.mana.ManaService;
import com.orbisarcana.item.SpellScrollItem;
import com.orbisarcana.progression.KnowledgeService;
import com.orbisarcana.progression.MasteryService;
import com.orbisarcana.progression.ScrollService;
import com.orbisarcana.school.AltarService;
import com.orbisarcana.school.AttunementService;
import com.orbisarcana.school.EchoService;
import com.orbisarcana.school.Schools;
import com.orbisarcana.school.TalentEffects;
import com.orbisarcana.registry.ModBlocks;
import com.orbisarcana.registry.ModItems;
import com.orbisarcana.spell.CastService;
import com.orbisarcana.spell.behavior.BurningGround;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * GameTests (§19.2). Run with {@code ./gradlew runGameTestServer} or {@code /test runall} in a dev client.
 * Every test uses {@code orbisarcana:test_platform} (9x6x9 stone floor) and a mock player placed in it.
 */
@SuppressWarnings("removal") // makeMockServerPlayerInLevel is the only helper that joins the player list
public final class OrbisGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, OrbisArcana.MODID);
    private static final Identifier STRUCTURE = OrbisArcana.id("test_platform");
    private static final List<Test> TESTS = new ArrayList<>();

    private static final Identifier FIREBALL = OrbisArcana.id("fireball");
    private static final Identifier HEALING_DEW = OrbisArcana.id("healing_dew");
    private static final Identifier LIFE_BLOOM = OrbisArcana.id("life_bloom");
    private static final Identifier WIND_STEP = OrbisArcana.id("wind_step");

    private record Test(String name, int maxTicks) {}

    static {
        test("fireball_damages_target", 100, OrbisGameTests::fireballDamagesTarget);
        test("healing_dew_heals_self", 60, OrbisGameTests::healingDewHealsSelf);
        test("life_bloom_heals_in_zone", 120, OrbisGameTests::lifeBloomHealsInZone);
        test("wind_step_dashes", 40, OrbisGameTests::windStepDashes);
        test("cast_denied_when_unknown", 20, OrbisGameTests::deniedWhenUnknown);
        test("cast_denied_without_mana", 20, OrbisGameTests::deniedWithoutMana);
        test("interrupt_refunds_mana", 40, OrbisGameTests::interruptRefundsMana);
        test("cooldown_blocks_recast", 40, OrbisGameTests::cooldownBlocksRecast);
        test("data_survives_death", 40, OrbisGameTests::dataSurvivesDeath);
        test("scroll_teaches_and_is_consumed", 20, OrbisGameTests::scrollTeaches);
        test("known_scroll_is_kept", 20, OrbisGameTests::knownScrollKept);
        test("dissolve_grants_capped_xp", 20, OrbisGameTests::dissolveCapped);
        test("learning_fills_radial_menu", 20, OrbisGameTests::learnFillsLoadout);
        test("loadout_rejects_unknown_spell", 20, OrbisGameTests::loadoutRejectsUnknown);
        test("hit_grants_mastery_xp", 100, OrbisGameTests::hitGrantsXp);
        test("armor_stand_grants_no_xp", 20, OrbisGameTests::armorStandNoXp);
        test("heal_grants_mastery_xp", 60, OrbisGameTests::healGrantsXp);
        test("xp_levels_up_spell", 20, OrbisGameTests::xpLevelsUp);
        test("wind_step_has_two_charges_at_15", 40, OrbisGameTests::twoCharges);
        test("disabled_milestone_removes_charge", 40, OrbisGameTests::disabledMilestone);
        test("burning_ground_hurts_inside", 60, OrbisGameTests::burningGround);
        test("altar_first_binding_is_free", 20, OrbisGameTests::altarFirstBinding);
        test("altar_switch_ritual_and_dissonance", 260, OrbisGameTests::altarSwitchRitual);
        test("altar_ritual_breaks_when_leaving", 60, OrbisGameTests::altarRitualBroken);
        test("mastery_feeds_resonance_and_dominant_discount", 40, OrbisGameTests::resonanceAndDiscount);
        test("talents_follow_points_and_reset", 20, OrbisGameTests::talentsAndReset);
        test("water_root_heals_more", 60, OrbisGameTests::waterRoot);
        test("heat_bursts_at_five_stacks", 20, OrbisGameTests::heatBurst);
        test("flow_makes_next_cast_instant", 20, OrbisGameTests::flowInstant);
        test("crown_of_flame_every_third_cast", 20, OrbisGameTests::crownOfFlame);
        test("flow_real_cast_needs_resonance_1", 20, OrbisGameTests::flowRealCast);
        test("ash_spiral_burns_in_area", 120, OrbisGameTests::ashSpiral);
        test("flame_dash_ignites_path_at_10", 30, OrbisGameTests::flameDash);
    }

    private static void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
        FUNCTIONS.register(name, () -> body);
        TESTS.add(new Test(name, maxTicks));
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(OrbisGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(OrbisArcana.id("default"));
        for (Test t : TESTS) {
            Identifier id = OrbisArcana.id(t.name());
            ResourceKey<Consumer<GameTestHelper>> fn = ResourceKey.create(Registries.TEST_FUNCTION, id);
            event.registerTest(id, data -> new FunctionGameTestInstance(fn, data), new TestData<>(env, STRUCTURE, t.maxTicks(), 1, true));
        }
    }

    // ---- helpers ----

    /** Mock player standing at relative {@code pos}, looking with the given yaw/pitch, full mana. */
    private static ServerPlayer player(GameTestHelper h, Vec3 pos, float yRot, float xRot) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Vec3 abs = h.absoluteVec(pos);
        p.setPos(abs.x, abs.y, abs.z);
        p.setYRot(yRot);
        p.setYHeadRot(yRot);
        p.setXRot(xRot);
        ManaService.setMana(p, ManaService.maxMana(p));
        return p;
    }

    private static void cleanup(GameTestHelper h, ServerPlayer p) {
        h.getLevel().getServer().getPlayerList().remove(p);
    }

    /** Succeeds once {@code check} passes, then removes the mock player. */
    private static void succeedWhen(GameTestHelper h, ServerPlayer p, Runnable check) {
        h.succeedWhen(() -> {
            check.run();
            cleanup(h, p);
        });
    }

    private static void release(GameTestHelper h, ServerPlayer p, int afterTicks) {
        h.runAfterDelay(afterTicks, () -> CastService.stop(p, CastService.activeCastId(p), false));
    }

    // ---- tests ----

    private static void fireballDamagesTarget(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 4.5));
        pig.setNoAi(true);
        // From x=1.5 looking +X (yaw -90), slightly down at the pig
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 12f);
        KnowledgeService.learn(p, FIREBALL);

        CastService.request(p, FIREBALL);
        h.assertTrue(CastService.isCasting(p), "fireball cast should start");
        release(h, p, 16);
        succeedWhen(h, p, () -> {
            h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "pig should be hurt by the fireball");
            h.assertTrue(ManaService.mana(p) < ManaService.maxMana(p), "mana should be spent");
        });
    }

    private static void healingDewHealsSelf(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, -90f); // looking at the sky: no target -> self
        KnowledgeService.learn(p, HEALING_DEW);
        p.setHealth(8f);

        CastService.request(p, HEALING_DEW);
        release(h, p, 18);
        succeedWhen(h, p, () -> h.assertTrue(p.getHealth() >= 14f, "healing dew should heal the caster by 6"));
    }

    private static void lifeBloomHealsInZone(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 90f); // looking straight down at the floor
        KnowledgeService.learn(p, LIFE_BLOOM);
        p.setHealth(8f);

        CastService.request(p, LIFE_BLOOM);
        h.assertTrue(CastService.isCasting(p), "life bloom cast should start");
        release(h, p, 22);
        succeedWhen(h, p, () -> h.assertTrue(p.getHealth() > 9f, "zone should heal the caster standing in it"));
    }

    private static void windStepDashes(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 0f);
        KnowledgeService.learn(p, WIND_STEP);

        CastService.request(p, WIND_STEP);
        Vec3 v = p.getDeltaMovement();
        h.assertTrue(v.horizontalDistance() > 0.5, "wind step should push the caster, got " + v);
        h.assertFalse(CastService.isCasting(p), "instant cast should finish immediately");
        cleanup(h, p);
        h.succeed();
    }

    private static void deniedWhenUnknown(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        CastService.request(p, FIREBALL); // not learned
        h.assertFalse(CastService.isCasting(p), "unlearned spell must not start");
        h.assertValueEqual(ManaService.mana(p), ManaService.maxMana(p), "mana");
        cleanup(h, p);
        h.succeed();
    }

    private static void deniedWithoutMana(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        KnowledgeService.learn(p, FIREBALL);
        ManaService.setMana(p, 5);
        CastService.request(p, FIREBALL);
        h.assertFalse(CastService.isCasting(p), "cast must not start without mana");
        cleanup(h, p);
        h.succeed();
    }

    private static void interruptRefundsMana(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        KnowledgeService.learn(p, FIREBALL);
        double before = ManaService.mana(p);
        CastService.request(p, FIREBALL);
        CastService.interrupt(p, Outcome.INTERRUPTED); // progress 0 -> 10% of cost (10) lost
        h.assertFalse(CastService.isCasting(p), "cast should be interrupted");
        double lost = before - ManaService.mana(p);
        h.assertTrue(Math.abs(lost - 1.0) < 0.2, "interrupt at start should lose ~1 mana, lost " + lost);
        cleanup(h, p);
        h.succeed();
    }

    private static void cooldownBlocksRecast(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 0f);
        KnowledgeService.learn(p, WIND_STEP);
        CastService.request(p, WIND_STEP);
        h.runAfterDelay(10, () -> {
            double mana = ManaService.mana(p);
            CastService.request(p, WIND_STEP);
            h.assertFalse(CastService.isCasting(p), "recast during cooldown must be denied");
            h.assertTrue(ManaService.mana(p) >= mana, "denied cast must not spend mana");
            cleanup(h, p);
            h.succeed();
        });
    }

    private static void dataSurvivesDeath(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        KnowledgeService.learn(p, FIREBALL);
        KnowledgeService.setMastery(p, FIREBALL, 7);
        p.getData(ModAttachments.ATTUNEMENT).setDominant(Optional.of(Schools.FIRE));
        p.getData(ModAttachments.ATTUNEMENT).school(Schools.FIRE).setResonance(4);

        p.kill(h.getLevel());
        ServerPlayer respawned = h.getLevel().getServer().getPlayerList().respawn(p, false, Entity.RemovalReason.KILLED);

        var progress = respawned.getData(ModAttachments.KNOWLEDGE).progress(FIREBALL);
        h.assertTrue(progress.isPresent(), "known spell lost on death");
        h.assertValueEqual(progress.get().level(), 7, "mastery after death");
        h.assertValueEqual(respawned.getData(ModAttachments.ATTUNEMENT).dominant(), Optional.of(Schools.FIRE), "dominant school");
        h.assertValueEqual(respawned.getData(ModAttachments.ATTUNEMENT).school(Schools.FIRE).resonance(), 4, "resonance");
        h.assertTrue(Math.abs(ManaService.mana(respawned) - ManaService.maxMana(respawned) * 0.5) < 0.01,
                "mana after death should be 50%, got " + ManaService.mana(respawned));
        cleanup(h, respawned);
        h.succeed();
    }

    private static void scrollTeaches(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        ItemStack scroll = SpellScrollItem.of(LIFE_BLOOM);
        scroll.setCount(2);
        ScrollService.learn(p, scroll, LIFE_BLOOM);
        h.assertTrue(p.getData(ModAttachments.KNOWLEDGE).knows(LIFE_BLOOM), "scroll should teach the spell");
        // The mock player is in creative, where items are not used up (vanilla rule)
        h.assertValueEqual(scroll.getCount(), p.hasInfiniteMaterials() ? 2 : 1, "scrolls left");
        cleanup(h, p);
        h.succeed();
    }

    private static void knownScrollKept(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        KnowledgeService.learn(p, FIREBALL);
        ItemStack scroll = SpellScrollItem.of(FIREBALL);
        h.assertFalse(ScrollService.canStartReading(p, FIREBALL), "known spell must not start reading");
        ScrollService.learn(p, scroll, FIREBALL);
        h.assertValueEqual(scroll.getCount(), 1, "scroll of a known spell must not be consumed");
        cleanup(h, p);
        h.succeed();
    }

    private static void dissolveCapped(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        KnowledgeService.learn(p, FIREBALL);
        ItemStack scrolls = SpellScrollItem.of(FIREBALL);
        scrolls.setCount(16);
        ScrollService.dissolve(p, scrolls, FIREBALL);
        var progress = p.getData(ModAttachments.KNOWLEDGE).progress(FIREBALL).orElseThrow();
        h.assertTrue(progress.xp() > 0, "dissolving should add mastery XP");
        int left = p.hasInfiniteMaterials() ? 16 : 15;
        h.assertValueEqual(scrolls.getCount(), left, "dissolved scrolls left");

        KnowledgeService.setMastery(p, FIREBALL, 9);
        ScrollService.dissolve(p, scrolls, FIREBALL);
        h.assertValueEqual(scrolls.getCount(), left, "no scroll consumed at the level-9 cap");
        h.assertValueEqual(progress.level(), 9, "level stays at the cap");
        cleanup(h, p);
        h.succeed();
    }

    private static void learnFillsLoadout(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        KnowledgeService.learn(p, FIREBALL);
        KnowledgeService.learn(p, WIND_STEP);
        var loadout = p.getData(ModAttachments.LOADOUT);
        h.assertValueEqual(loadout.get(0, 0), Optional.of(FIREBALL), "slot 1");
        h.assertValueEqual(loadout.get(0, 1), Optional.of(WIND_STEP), "slot 2");
        KnowledgeService.select(p, 0, 1);
        h.assertValueEqual(loadout.selectedSpell(), Optional.of(WIND_STEP), "selected spell");
        // Moving a spell keeps it in one slot only
        KnowledgeService.setSlot(p, 2, 7, Optional.of(FIREBALL));
        h.assertValueEqual(loadout.get(0, 0), Optional.<Identifier>empty(), "old slot cleared");
        h.assertValueEqual(loadout.get(2, 7), Optional.of(FIREBALL), "new slot");
        cleanup(h, p);
        h.succeed();
    }

    private static void loadoutRejectsUnknown(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        h.assertFalse(KnowledgeService.setSlot(p, 0, 0, Optional.of(FIREBALL)), "unknown spell must be rejected");
        h.assertFalse(KnowledgeService.setSlot(p, 5, 0, Optional.empty()), "bad page must be rejected");
        h.assertValueEqual(p.getData(ModAttachments.LOADOUT).get(0, 0), Optional.<Identifier>empty(), "slot stays empty");
        cleanup(h, p);
        h.succeed();
    }

    private static double xp(ServerPlayer p, Identifier spell) {
        return p.getData(ModAttachments.KNOWLEDGE).progress(spell).map(sp -> sp.xp() + sp.level() * 1000.0).orElse(0.0);
    }

    private static void hitGrantsXp(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 4.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 12f);
        KnowledgeService.learn(p, FIREBALL);
        double before = xp(p, FIREBALL);
        CastService.request(p, FIREBALL);
        release(h, p, 16);
        succeedWhen(h, p, () -> h.assertTrue(xp(p, FIREBALL) > before, "hitting a mob should grant mastery XP"));
    }

    private static void armorStandNoXp(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), 0f, 0f);
        KnowledgeService.learn(p, FIREBALL);
        ArmorStand stand = h.spawn(EntityTypes.ARMOR_STAND, new Vec3(4.5, 1, 4.5));
        double before = xp(p, FIREBALL);
        MasteryService.onHit(p, FIREBALL, 1, stand, true);
        h.assertValueEqual(xp(p, FIREBALL), before, "armor stand XP");
        cleanup(h, p);
        h.succeed();
    }

    private static void healGrantsXp(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, -90f);
        KnowledgeService.learn(p, HEALING_DEW);
        p.setHealth(8f);
        double before = xp(p, HEALING_DEW);
        CastService.request(p, HEALING_DEW);
        release(h, p, 18);
        succeedWhen(h, p, () -> h.assertTrue(xp(p, HEALING_DEW) > before, "real healing should grant mastery XP"));
    }

    private static void xpLevelsUp(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        KnowledgeService.learn(p, FIREBALL);
        MasteryService.award(p, FIREBALL, 40); // weight 40 x cycleWeight -> several levels
        int level = p.getData(ModAttachments.KNOWLEDGE).progress(FIREBALL).orElseThrow().level();
        h.assertTrue(level > 1, "a large award should level the spell up, level " + level);
        cleanup(h, p);
        h.succeed();
    }

    /** Casts wind step now and again after the global cooldown; reports whether each started. */
    private static void castTwice(GameTestHelper h, ServerPlayer p, java.util.function.BiConsumer<Boolean, Boolean> check) {
        double before = ManaService.mana(p);
        CastService.request(p, WIND_STEP);
        boolean first = ManaService.mana(p) < before;
        h.runAfterDelay(8, () -> {
            double mid = ManaService.mana(p);
            CastService.request(p, WIND_STEP);
            check.accept(first, ManaService.mana(p) < mid);
        });
    }

    private static void twoCharges(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 0f);
        KnowledgeService.learn(p, WIND_STEP);
        KnowledgeService.setMastery(p, WIND_STEP, 15);
        castTwice(h, p, (first, second) -> {
            h.assertTrue(first && second, "two charges should allow two casts in a row");
            double mana = ManaService.mana(p);
            h.runAfterDelay(8, () -> {
                CastService.request(p, WIND_STEP);
                h.assertTrue(ManaService.mana(p) >= mana, "third cast must wait for a charge");
                cleanup(h, p);
                h.succeed();
            });
        });
    }

    private static void disabledMilestone(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 0f);
        KnowledgeService.learn(p, WIND_STEP);
        h.assertFalse(KnowledgeService.setMilestoneDisabled(p, WIND_STEP, 15, true), "unreached milestone cannot be toggled");
        KnowledgeService.setMastery(p, WIND_STEP, 15);
        h.assertTrue(KnowledgeService.setMilestoneDisabled(p, WIND_STEP, 15, true), "reached milestone can be disabled");
        castTwice(h, p, (first, second) -> {
            h.assertTrue(first, "first cast starts");
            h.assertFalse(second, "with milestone 15 off there is only one charge");
            cleanup(h, p);
            h.succeed();
        });
    }

    private static void burningGround(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(1.5, 1, 1.5), 0f, 0f);
        KnowledgeService.learn(p, FIREBALL);
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(5.5, 1, 5.5));
        pig.setNoAi(true);
        BurningGround.spawn(h.getLevel(), h.absoluteVec(new Vec3(5.5, 1, 5.5)), 1.5, 40, p, Schools.FIRE, FIREBALL, 77);
        succeedWhen(h, p, () -> {
            h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "burning ground should hurt a creature standing in it");
            h.assertTrue(xp(p, FIREBALL) > 0, "burn damage counts for the spell mastery");
        });
    }

    private static final BlockPos ALTAR = new BlockPos(4, 1, 4);

    /** Survival player next to an altar, knowing a fire and an air spell. */
    private static ServerPlayer altarPlayer(GameTestHelper h) {
        h.setBlock(ALTAR, ModBlocks.RESONANCE_ALTAR.get());
        ServerPlayer p = player(h, new Vec3(5.5, 1, 4.5), 90f, 0f);
        KnowledgeService.learn(p, FIREBALL);
        KnowledgeService.learn(p, WIND_STEP);
        return p;
    }

    private static int ink(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(ModItems.AETHER_INK.get())) n += p.getInventory().getItem(i).getCount();
        }
        return n;
    }

    private static void altarFirstBinding(GameTestHelper h) {
        ServerPlayer p = altarPlayer(h);
        AltarService.handle(p, h.absolutePos(ALTAR), AltarService.Action.BIND, Optional.of(Schools.FIRE));
        var a = p.getData(ModAttachments.ATTUNEMENT);
        h.assertValueEqual(a.dominant(), Optional.of(Schools.FIRE), "dominant after first binding");
        h.assertFalse(AttunementService.isDissonant(p), "first binding starts no Dissonance");
        h.assertFalse(AltarService.inRitual(p), "first binding needs no ritual");
        AltarService.handle(p, h.absolutePos(ALTAR), AltarService.Action.BIND, Optional.of(Schools.ICE));
        h.assertValueEqual(a.dominant(), Optional.of(Schools.FIRE), "no binding to a school without a known spell");
        cleanup(h, p);
        h.succeed();
    }

    private static void altarSwitchRitual(GameTestHelper h) {
        ServerPlayer p = altarPlayer(h);
        BlockPos altar = h.absolutePos(ALTAR);
        AltarService.handle(p, altar, AltarService.Action.BIND, Optional.of(Schools.FIRE));
        AltarService.handle(p, altar, AltarService.Action.BIND, Optional.of(Schools.AIR));
        // The mock player is in creative (infinite materials), so the ritual starts without ink
        h.assertTrue(AltarService.inRitual(p), "switch starts a ritual");
        h.runAfterDelay(205, () -> {
            var a = p.getData(ModAttachments.ATTUNEMENT);
            h.assertValueEqual(a.dominant(), Optional.of(Schools.AIR), "dominant after the ritual");
            h.assertValueEqual(a.previous(), Optional.of(Schools.FIRE), "previous school");
            h.assertTrue(AttunementService.isDissonant(p), "switch starts Dissonance");
            h.assertTrue(AttunementService.powerMult(p, Schools.AIR) < 1.10, "new school weakened by Dissonance");
            h.assertTrue(AttunementService.bindProblem(p, Schools.FIRE).isPresent(), "switch pause blocks another switch");
            cleanup(h, p);
            h.succeed();
        });
    }

    private static void altarRitualBroken(GameTestHelper h) {
        ServerPlayer p = altarPlayer(h);
        BlockPos altar = h.absolutePos(ALTAR);
        AltarService.handle(p, altar, AltarService.Action.BIND, Optional.of(Schools.FIRE));
        AltarService.handle(p, altar, AltarService.Action.BIND, Optional.of(Schools.AIR));
        h.assertTrue(AltarService.inRitual(p), "ritual started");
        Vec3 away = h.absoluteVec(new Vec3(4.5, 1, 4.5)).add(8, 0, 0);
        p.setPos(away.x, away.y, away.z); // leaving the circle breaks it
        h.runAfterDelay(3, () -> {
            h.assertFalse(AltarService.inRitual(p), "ritual broken");
            h.assertValueEqual(p.getData(ModAttachments.ATTUNEMENT).dominant(), Optional.of(Schools.FIRE), "dominant unchanged");
            cleanup(h, p);
            h.succeed();
        });
    }

    private static void resonanceAndDiscount(GameTestHelper h) {
        ServerPlayer p = altarPlayer(h);
        AltarService.handle(p, h.absolutePos(ALTAR), AltarService.Action.BIND, Optional.of(Schools.FIRE));
        MasteryService.award(p, FIREBALL, 30);
        var fire = p.getData(ModAttachments.ATTUNEMENT).school(Schools.FIRE);
        h.assertTrue(fire.resonance() > 0 || fire.xp() > 0, "fire mastery feeds fire resonance");
        h.assertValueEqual(AttunementService.costMult(p, Schools.FIRE), 0.95, "dominant cost multiplier");
        h.assertValueEqual(AttunementService.costMult(p, Schools.AIR), 1.0, "other school cost multiplier");
        double before = ManaService.mana(p);
        CastService.request(p, WIND_STEP);
        double spentAir = before - ManaService.mana(p);
        h.assertTrue(Math.abs(spentAir - 12.0) < 0.01, "wind step costs full mana, spent " + spentAir);
        cleanup(h, p);
        h.succeed();
    }

    private static void talentsAndReset(GameTestHelper h) {
        ServerPlayer p = altarPlayer(h);
        BlockPos altar = h.absolutePos(ALTAR);
        h.assertTrue(AttunementService.pickTalent(p, Schools.FIRE, "a1").isPresent(), "no talents without the dominant school");
        AltarService.handle(p, altar, AltarService.Action.BIND, Optional.of(Schools.FIRE));
        var fire = p.getData(ModAttachments.ATTUNEMENT).school(Schools.FIRE);
        fire.setResonance(3);
        h.assertTrue(AttunementService.pickTalent(p, Schools.FIRE, "a2").isPresent(), "second node needs the first");
        h.assertTrue(AttunementService.pickTalent(p, Schools.FIRE, "a1").isEmpty(), "first node with one point");
        h.assertTrue(AttunementService.pickTalent(p, Schools.FIRE, "b1").isPresent(), "no points left at resonance 3");
        h.assertTrue(AttunementService.hasTalent(p, Schools.FIRE, "a1"), "picked talent is active");
        AltarService.handle(p, altar, AltarService.Action.RESET_TALENTS, Optional.of(Schools.FIRE));
        h.assertTrue(fire.talents().isEmpty(), "talents reset at the altar");
        cleanup(h, p);
        h.succeed();
    }

    private static void waterRoot(GameTestHelper h) {
        h.setBlock(ALTAR, ModBlocks.RESONANCE_ALTAR.get());
        ServerPlayer p = player(h, new Vec3(5.5, 1, 4.5), 0f, -90f);
        KnowledgeService.learn(p, HEALING_DEW);
        AltarService.handle(p, h.absolutePos(ALTAR), AltarService.Action.BIND, Optional.of(Schools.WATER));
        p.getData(ModAttachments.ATTUNEMENT).school(Schools.WATER).setResonance(1);
        p.setHealth(4f);
        CastService.request(p, HEALING_DEW);
        release(h, p, 18);
        // 6 HP x mastery 1 x dominant water power 1.10 x Living Wave 1.10 = 7.26
        succeedWhen(h, p, () -> h.assertTrue(p.getHealth() >= 4f + 7.2f, "Living Wave and dominant power should heal ~7.26, health " + p.getHealth()));
    }

    /** Player bound to {@code school} with the given resonance. */
    private static ServerPlayer boundTo(GameTestHelper h, Identifier school, Identifier spell, int resonance) {
        h.setBlock(ALTAR, ModBlocks.RESONANCE_ALTAR.get());
        ServerPlayer p = player(h, new Vec3(5.5, 1, 4.5), 0f, 0f);
        KnowledgeService.learn(p, spell);
        AltarService.handle(p, h.absolutePos(ALTAR), AltarService.Action.BIND, Optional.of(school));
        p.getData(ModAttachments.ATTUNEMENT).school(school).setResonance(resonance);
        return p;
    }

    private static void heatBurst(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.FIRE, FIREBALL, 1);
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(2.5, 1, 2.5));
        pig.setNoAi(true);
        for (int i = 0; i < 4; i++) EchoService.onSpellHit(p, Schools.FIRE, pig, i);
        EchoService.onSpellHit(p, Schools.FIRE, pig, 3); // same cast again: no extra stack
        h.assertValueEqual(pig.getHealth(), pig.getMaxHealth(), "no burst before the 5th stack");
        EchoService.onSpellHit(p, Schools.FIRE, pig, 4);
        h.assertTrue(pig.getHealth() <= pig.getMaxHealth() - 3.9f, "Heat burst at 5 stacks, health " + pig.getHealth());
        cleanup(h, p);
        h.succeed();
    }

    private static void flowInstant(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.WATER, HEALING_DEW, 1);
        EchoService.onHealCast(p, 101);
        EchoService.onHealCast(p, 101); // zone pulses of one cast count once
        EchoService.onHealCast(p, 102);
        h.assertFalse(EchoService.consumeFlow(p), "two stacks are not enough at tier 1");
        EchoService.onHealCast(p, 103);
        h.assertTrue(EchoService.consumeFlow(p), "three stacks make the next cast instant");
        h.assertFalse(EchoService.consumeFlow(p), "stacks are spent");
        cleanup(h, p);
        h.succeed();
    }

    private static void crownOfFlame(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.FIRE, FIREBALL, 3);
        AttunementService.pickTalent(p, Schools.FIRE, "a1");
        h.assertFalse(TalentEffects.crownOfFlameFree(p, Schools.FIRE), "needs the A2 node");
        p.getData(ModAttachments.ATTUNEMENT).school(Schools.FIRE).setResonance(6);
        AttunementService.pickTalent(p, Schools.FIRE, "a2");
        boolean first = TalentEffects.crownOfFlameFree(p, Schools.FIRE);
        boolean second = TalentEffects.crownOfFlameFree(p, Schools.FIRE);
        boolean third = TalentEffects.crownOfFlameFree(p, Schools.FIRE);
        h.assertTrue(!first && !second && third, "every third fire cast is free");
        h.assertFalse(TalentEffects.crownOfFlameFree(p, Schools.WATER), "only fire spells count");
        cleanup(h, p);
        h.succeed();
    }

    /** End-to-end Flow: three healing casts, then a charged fireball fires at once; nothing at resonance 0. */
    private static void flowRealCast(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.WATER, HEALING_DEW, 0);
        KnowledgeService.learn(p, FIREBALL);
        for (int i = 0; i < 3; i++) EchoService.onHealCast(p, 200 + i);
        CastService.request(p, FIREBALL);
        h.assertTrue(CastService.isCasting(p), "at resonance 0 there is no Echo: the fireball still charges");
        CastService.interrupt(p, com.orbisarcana.core.ActiveCast.Outcome.CANCELLED);
        p.getData(ModAttachments.ATTUNEMENT).school(Schools.WATER).setResonance(1);
        for (int i = 0; i < 3; i++) EchoService.onHealCast(p, 300 + i);
        h.runAfterDelay(6, () -> { // after the global cooldown
            CastService.request(p, FIREBALL);
            h.assertFalse(CastService.isCasting(p), "with Flow the charged fireball is released immediately");
            cleanup(h, p);
            h.succeed();
        });
    }

    private static final Identifier ASH_SPIRAL = OrbisArcana.id("ash_spiral");
    private static final Identifier FLAME_DASH = OrbisArcana.id("flame_dash");

    private static void ashSpiral(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 6.5));
        pig.setNoAi(true);
        // Looking down at the pig two blocks ahead
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 40f);
        KnowledgeService.learn(p, ASH_SPIRAL);
        CastService.request(p, ASH_SPIRAL);
        h.assertTrue(CastService.isCasting(p), "ash spiral starts charging");
        release(h, p, 28);
        succeedWhen(h, p, () -> h.assertTrue(pig.getHealth() <= pig.getMaxHealth() - 3f, "vortex should hurt the pig repeatedly, health " + pig.getHealth()));
    }

    private static void flameDash(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 4.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 0f);
        KnowledgeService.learn(p, FLAME_DASH);
        KnowledgeService.setMastery(p, FLAME_DASH, 10);
        CastService.request(p, FLAME_DASH);
        succeedWhen(h, p, () -> h.assertTrue(pig.isOnFire(), "a pig on the dash path is set on fire"));
    }

    private OrbisGameTests() {}
}
