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
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.CastService;

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

    private OrbisGameTests() {}
}
