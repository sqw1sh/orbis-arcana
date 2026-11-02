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
import com.orbisarcana.item.Foci;
import com.orbisarcana.registry.ModAttributes;
import com.orbisarcana.registry.ModItems;
import com.orbisarcana.spell.CastService;
import com.orbisarcana.spell.Frost;
import com.orbisarcana.spell.Shields;
import com.orbisarcana.spell.Toggles;
import com.orbisarcana.spell.SpellRegistry;
import com.orbisarcana.spell.Stars;
import com.orbisarcana.spell.behavior.CurseBehavior;
import com.orbisarcana.spell.behavior.OrbitBehavior;
import com.orbisarcana.spell.behavior.ShiftBehavior;
import com.orbisarcana.spell.behavior.RootsBehavior;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
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
        test("tidal_stream_channel_drains_and_hits", 80, OrbisGameTests::tidalStream);
        test("water_prison_holds_target", 80, OrbisGameTests::waterPrison);
        test("stone_spear_hits_in_line", 60, OrbisGameTests::stoneSpear);
        test("fault_line_runs_from_point", 80, OrbisGameTests::faultLine);
        test("granite_oath_gives_real_absorption", 20, OrbisGameTests::graniteOath);
        test("overheal_shield_really_absorbs", 60, OrbisGameTests::overhealShield);
        test("bulwark_bursts_on_attack_cast", 60, OrbisGameTests::bulwarkBurst);
        test("wind_blade_cuts", 40, OrbisGameTests::windBlade);
        test("wind_blade_returns_and_cuts_again", 80, OrbisGameTests::windBladeReturn);
        test("gust_dome_deflects_projectiles", 80, OrbisGameTests::gustDome);
        test("impulse_lengthens_dash", 20, OrbisGameTests::impulseDash);
        test("light_step_cancels_short_falls", 20, OrbisGameTests::lightStep);
        test("ice_shard_hits_and_slows", 40, OrbisGameTests::iceShard);
        test("ice_shard_blasts_frozen_target", 40, OrbisGameTests::iceShardBlast);
        test("ice_bastion_raises_and_melts", 200, OrbisGameTests::iceBastion);
        test("frost_pattern_slows_then_freezes", 300, OrbisGameTests::frostPattern);
        test("frost_echo_freezes_at_five", 20, OrbisGameTests::frostEcho);
        test("cold_blood_keeps_speed_while_casting", 20, OrbisGameTests::coldBlood);
        test("discharge_hits_what_you_aim_at", 20, OrbisGameTests::discharge);
        test("discharge_jumps_at_10", 20, OrbisGameTests::dischargeJumps);
        test("storm_anchor_strikes_when_enemy_near", 60, OrbisGameTests::stormAnchor);
        test("current_shell_toggles_and_drains", 60, OrbisGameTests::currentShell);
        test("current_shell_shocks_on_melee", 20, OrbisGameTests::shellShock);
        test("conductivity_arcs_to_marked", 20, OrbisGameTests::conductivity);
        test("root_shackles_hold_and_spread", 40, OrbisGameTests::rootShackles);
        test("thorn_seed_sprouts_a_thicket", 100, OrbisGameTests::thornSeed);
        test("thorn_seed_grows_a_guardian", 100, OrbisGameTests::thornGuardian);
        test("sprouting_matures_and_heals", 160, OrbisGameTests::sprouting);
        test("dawn_ray_burns_the_first_enemy", 60, OrbisGameTests::dawnRay);
        test("dawn_ray_pierces_at_m5", 60, OrbisGameTests::dawnRayPierce);
        test("purifying_aura_softens_and_wards", 40, OrbisGameTests::purifyingAura);
        test("heavens_judgment_strikes_and_marks", 100, OrbisGameTests::heavensJudgment);
        test("heavens_judgment_hallows_ground", 140, OrbisGameTests::hallowedGround);
        test("radiance_pulse_cleanses_and_veils", 20, OrbisGameTests::radiance);
        test("shadow_dart_hits_and_echoes", 60, OrbisGameTests::shadowDart);
        test("withering_curse_weakens_and_spreads", 80, OrbisGameTests::witheringCurse);
        test("shadow_step_teleports_and_returns", 40, OrbisGameTests::shadowStep);
        test("dusk_mark_absorbed_for_mana", 20, OrbisGameTests::dusk);
        test("star_spike_pierces_and_places_a_star", 40, OrbisGameTests::starSpike);
        test("guardian_orbit_catches_and_launches", 60, OrbisGameTests::guardianOrbit);
        test("constellation_shift_drags_enemies", 100, OrbisGameTests::constellationShift);
        test("constellation_pulses_on_three_stars", 20, OrbisGameTests::constellation);
        test("foci_apply_attributes_by_hand", 20, OrbisGameTests::foci);
        test("mana_potion_restores_mana", 20, OrbisGameTests::manaPotion);
        test("altar_dismantles_duplicates_into_ink", 20, OrbisGameTests::dismantle);
        test("aether_ore_drops_shards", 20, OrbisGameTests::aetherOre);
        test("dungeon_chests_hold_scrolls", 20, OrbisGameTests::dungeonLoot);
        test("ancient_cities_hold_dark_scrolls", 20, OrbisGameTests::ancientCityLoot);
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

    private static final Identifier TIDAL_STREAM = OrbisArcana.id("tidal_stream");
    private static final Identifier WATER_PRISON = OrbisArcana.id("water_prison");

    private static void tidalStream(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 4.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 15f);
        KnowledgeService.learn(p, TIDAL_STREAM);
        double before = ManaService.mana(p);
        CastService.request(p, TIDAL_STREAM);
        h.assertTrue(CastService.isCasting(p), "channel starts");
        h.assertValueEqual(ManaService.mana(p), before, "a channel reserves nothing up front");
        h.runAfterDelay(30, () -> {
            h.assertTrue(CastService.isCasting(p), "still channelling while held");
            h.assertTrue(ManaService.mana(p) < before - 3, "mana drains over time, left " + ManaService.mana(p));
            CastService.stop(p, CastService.activeCastId(p), false);
            h.assertFalse(CastService.isCasting(p), "releasing the key ends the channel");
            h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the stream hurts the pig");
            cleanup(h, p);
            h.succeed();
        });
    }

    private static void waterPrison(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(5.5, 1, 4.5));
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 10f);
        KnowledgeService.learn(p, WATER_PRISON);
        double xpBefore = xp(p, WATER_PRISON);
        CastService.request(p, WATER_PRISON);
        h.assertTrue(CastService.isCasting(p), "prison needs a target and starts charging");
        release(h, p, 20);
        h.runAfterDelay(24, () -> {
            Vec3 anchor = pig.position();
            pig.setDeltaMovement(1.0, 0, 0);
            h.runAfterDelay(4, () -> {
                h.assertTrue(pig.position().distanceTo(anchor) < 0.4, "the bubble keeps the pig in place");
                h.assertTrue(xp(p, WATER_PRISON) > xpBefore, "control grants mastery XP");
                cleanup(h, p);
                h.succeed();
            });
        });
    }

    private static final Identifier STONE_SPEAR = OrbisArcana.id("stone_spear");
    private static final Identifier FAULT_LINE = OrbisArcana.id("fault_line");
    private static final Identifier GRANITE_OATH = OrbisArcana.id("granite_oath");

    private static void stoneSpear(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 4.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 0f);
        KnowledgeService.learn(p, STONE_SPEAR);
        CastService.request(p, STONE_SPEAR);
        release(h, p, 15);
        succeedWhen(h, p, () -> h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the spike line hurts the pig"));
    }

    private static void faultLine(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 7.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(4.5, 1, 2.5), 0f, 45f); // looking down at the floor ahead
        KnowledgeService.learn(p, FAULT_LINE);
        CastService.request(p, FAULT_LINE);
        h.assertTrue(CastService.isCasting(p), "fault line needs a ground point and starts");
        release(h, p, 26);
        succeedWhen(h, p, () -> h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the crack reaches the pig"));
    }

    private static void graniteOath(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        KnowledgeService.learn(p, GRANITE_OATH);
        CastService.request(p, GRANITE_OATH);
        h.assertTrue(p.getAbsorptionAmount() >= 5.9f, "granite oath gives a 6 HP shield, absorption " + p.getAbsorptionAmount());
        h.assertTrue(Shields.total(p) >= 5.9f, "the shield is tracked");
        cleanup(h, p);
        h.succeed();
    }

    /** Regression: absorption is capped by MAX_ABSORPTION (0 by default), so this shield used to do nothing. */
    private static void overhealShield(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, -90f);
        KnowledgeService.learn(p, HEALING_DEW);
        KnowledgeService.setMastery(p, HEALING_DEW, 15);
        CastService.request(p, HEALING_DEW); // full health: all of it is overheal
        release(h, p, 18);
        succeedWhen(h, p, () -> h.assertTrue(p.getAbsorptionAmount() > 0.5f, "overheal becomes a shield, absorption " + p.getAbsorptionAmount()));
    }

    private static void bulwarkBurst(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.EARTH, STONE_SPEAR, 1);
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 4.5)); // beside the player, not in the spear line
        pig.setNoAi(true);
        p.setYRot(180f);
        EchoService.setBulwark(p, 10);
        CastService.request(p, STONE_SPEAR);
        release(h, p, 15);
        succeedWhen(h, p, () -> {
            h.assertTrue(pig.getHealth() <= pig.getMaxHealth() - 4.9f, "a charged Bulwark bursts on the attack cast, health " + pig.getHealth());
            h.assertValueEqual(EchoService.bulwark(p), 0.0, "Bulwark spent");
        });
    }

    private static final Identifier WIND_BLADE = OrbisArcana.id("wind_blade");
    private static final Identifier GUST_DOME = OrbisArcana.id("gust_dome");

    private static void windBlade(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 4.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 0f);
        KnowledgeService.learn(p, WIND_BLADE);
        CastService.request(p, WIND_BLADE);
        h.assertFalse(CastService.isCasting(p), "wind blade is instant");
        succeedWhen(h, p, () -> h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the arc cuts the pig"));
    }

    private static void windBladeReturn(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 4.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 0f);
        KnowledgeService.learn(p, WIND_BLADE);
        KnowledgeService.setMastery(p, WIND_BLADE, 10);
        CastService.request(p, WIND_BLADE);
        h.runAfterDelay(8, () -> h.assertTrue(pig.isAlive(), "one pass does not kill a pig"));
        succeedWhen(h, p, () -> h.assertFalse(pig.isAlive(), "the returning arc cuts the pig a second time, health " + pig.getHealth()));
    }

    private static void gustDome(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 90f); // looking straight down: dome on the caster
        KnowledgeService.learn(p, GUST_DOME);
        CastService.request(p, GUST_DOME);
        h.assertTrue(CastService.isCasting(p), "gust dome needs a ground point and starts");
        release(h, p, 24);
        Entity[] ball = new Entity[1];
        h.runAfterDelay(27, () -> {
            ball[0] = h.spawn(EntityTypes.SNOWBALL, new Vec3(8.5, 2, 4.5));
            ball[0].setDeltaMovement(-1.0, 0, 0);
        });
        succeedWhen(h, p, () -> {
            h.assertTrue(ball[0] != null, "snowball thrown");
            h.assertTrue(ball[0].getDeltaMovement().x > 0, "the dome turns the snowball away, motion " + ball[0].getDeltaMovement());
        });
    }

    private static void impulseDash(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.AIR, WIND_STEP, 1);
        EchoService.setImpulse(p, 10);
        CastService.request(p, WIND_STEP);
        double speed = p.getDeltaMovement().horizontalDistance();
        h.assertTrue(speed > 1.6, "a full Impulse makes the dash 40% longer, speed " + speed);
        h.assertValueEqual(EchoService.impulse(p), 0.0, "Impulse spent");
        cleanup(h, p);
        h.succeed();
    }

    private static void lightStep(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.AIR, WIND_STEP, 1);
        var shortFall = new LivingFallEvent(p, 8, 1f);
        NeoForge.EVENT_BUS.post(shortFall);
        h.assertTrue(shortFall.isCanceled(), "Light Step: no fall damage from 8 blocks");
        var longFall = new LivingFallEvent(p, 14, 1f);
        NeoForge.EVENT_BUS.post(longFall);
        h.assertFalse(longFall.isCanceled(), "a 14 block fall still hurts");
        cleanup(h, p);
        h.succeed();
    }

    private static final Identifier ICE_SHARD = OrbisArcana.id("ice_shard");
    private static final Identifier ICE_BASTION = OrbisArcana.id("ice_bastion");
    private static final Identifier FROST_PATTERN = OrbisArcana.id("frost_pattern");

    private static void iceShard(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 4.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 11f); // aimed at the pig's body
        KnowledgeService.learn(p, ICE_SHARD);
        CastService.request(p, ICE_SHARD);
        succeedWhen(h, p, () -> {
            h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the shard hurts the pig");
            h.assertTrue(pig.hasEffect(MobEffects.SLOWNESS), "and slows it");
        });
    }

    private static void iceShardBlast(GameTestHelper h) {
        Pig frozen = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 4.5));
        Pig beside = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 6.2));
        frozen.setNoAi(true);
        beside.setNoAi(true);
        Frost.freeze(frozen, 100, null);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 11f);
        KnowledgeService.learn(p, ICE_SHARD);
        KnowledgeService.setMastery(p, ICE_SHARD, 15);
        CastService.request(p, ICE_SHARD);
        succeedWhen(h, p, () -> h.assertTrue(beside.getHealth() < beside.getMaxHealth(), "the ice blast reaches the pig beside"));
    }

    private static boolean anyFrostWall(GameTestHelper h) {
        for (int x = 0; x < 9; x++) for (int y = 1; y < 5; y++) for (int z = 0; z < 9; z++) {
            if (h.getBlockState(new BlockPos(x, y, z)).is(ModBlocks.FROST_WALL.get())) return true;
        }
        return false;
    }

    private static void iceBastion(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 1.5), 0f, 45f); // facing +z, looking at the floor ahead
        KnowledgeService.learn(p, ICE_BASTION);
        CastService.request(p, ICE_BASTION);
        h.assertTrue(CastService.isCasting(p), "ice bastion needs a ground point and starts");
        release(h, p, 20);
        boolean[] raised = new boolean[1];
        h.runAfterDelay(22, () -> raised[0] = anyFrostWall(h));
        h.runAfterDelay(23, () -> h.assertTrue(raised[0], "the wall stands after the cast"));
        succeedWhen(h, p, () -> {
            h.assertTrue(raised[0], "the wall stood");
            h.assertFalse(anyFrostWall(h), "and melts when its time is up");
        });
    }

    private static void frostPattern(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 6.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(4.5, 1, 2.5), 0f, 45f);
        KnowledgeService.learn(p, FROST_PATTERN);
        KnowledgeService.setMastery(p, FROST_PATTERN, 15);
        CastService.request(p, FROST_PATTERN);
        release(h, p, 26);
        boolean[] slowed = new boolean[1];
        h.runAfterDelay(40, () -> {
            var slow = pig.getEffect(MobEffects.SLOWNESS);
            slowed[0] = slow != null && slow.getAmplifier() >= 2;
        });
        succeedWhen(h, p, () -> {
            h.assertTrue(slowed[0], "the pattern strongly slows");
            h.assertTrue(Frost.isFrozen(pig), "the pattern freezes everyone inside at the end (m15)");
        });
    }

    private static void frostEcho(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.ICE, ICE_SHARD, 1);
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(2.5, 1, 2.5));
        pig.setNoAi(true);
        for (int i = 0; i < 4; i++) EchoService.onSpellHit(p, Schools.ICE, pig, 300 + i);
        h.assertFalse(Frost.isFrozen(pig), "four Frost stacks only slow");
        h.assertTrue(pig.getEffect(MobEffects.SLOWNESS).getAmplifier() == 3, "slow grows with stacks");
        EchoService.onSpellHit(p, Schools.ICE, pig, 304);
        h.assertTrue(Frost.isFrozen(pig), "the fifth stack freezes");
        cleanup(h, p);
        h.succeed();
    }

    private static void coldBlood(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.ICE, FROST_PATTERN, 1);
        p.setXRot(60f);
        double base = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        CastService.request(p, FROST_PATTERN);
        h.assertTrue(CastService.isCasting(p), "charging");
        h.assertValueEqual(p.getAttributeValue(Attributes.MOVEMENT_SPEED), base, "Cold Blood: no casting slow for Ice");
        CastService.stop(p, CastService.activeCastId(p), true);
        cleanup(h, p);
        h.succeed();
    }

    private static final Identifier DISCHARGE = OrbisArcana.id("discharge");
    private static final Identifier STORM_ANCHOR = OrbisArcana.id("storm_anchor");
    private static final Identifier CURRENT_SHELL = OrbisArcana.id("current_shell");

    private static void discharge(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 4.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 11f);
        KnowledgeService.learn(p, DISCHARGE);
        CastService.request(p, DISCHARGE);
        h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the bolt hits at once");
        cleanup(h, p);
        h.succeed();
    }

    private static void dischargeJumps(GameTestHelper h) {
        Pig first = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 4.5));
        Pig second = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 7.5));
        Pig third = h.spawn(EntityTypes.PIG, new Vec3(3.5, 1, 7.5));
        for (Pig pig : List.of(first, second, third)) pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 11f);
        KnowledgeService.learn(p, DISCHARGE);
        KnowledgeService.setMastery(p, DISCHARGE, 10);
        CastService.request(p, DISCHARGE);
        for (Pig pig : List.of(first, second, third)) h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the bolt jumps to every pig");
        cleanup(h, p);
        h.succeed();
    }

    private static void stormAnchor(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 2.5), 0f, 45f);
        KnowledgeService.learn(p, STORM_ANCHOR);
        CastService.request(p, STORM_ANCHOR);
        h.assertFalse(CastService.isCasting(p), "instant");
        Pig[] pig = new Pig[1];
        h.runAfterDelay(15, () -> {
            pig[0] = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 5.5));
            pig[0].setNoAi(true);
        });
        succeedWhen(h, p, () -> {
            h.assertTrue(pig[0] != null && pig[0].getHealth() < pig[0].getMaxHealth(), "the anchor strikes the pig that came near");
        });
    }

    private static void currentShell(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        KnowledgeService.learn(p, CURRENT_SHELL);
        double base = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double full = ManaService.mana(p);
        CastService.request(p, CURRENT_SHELL);
        h.assertTrue(Toggles.isOn(p, CURRENT_SHELL), "first press turns it on");
        h.assertFalse(CastService.isCasting(p), "a toggle does not take the cast slot");
        h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > base * 1.1, "faster while on");
        h.runAfterDelay(20, () -> {
            h.assertTrue(ManaService.mana(p) < full - 2, "drains mana every tick, mana " + ManaService.mana(p));
            CastService.request(p, CURRENT_SHELL);
            h.assertFalse(Toggles.isOn(p, CURRENT_SHELL), "second press turns it off");
            h.assertValueEqual(p.getAttributeValue(Attributes.MOVEMENT_SPEED), base, "speed back");
            h.assertTrue(p.getData(ModAttachments.RESOURCES).cooldowns().containsKey(CURRENT_SHELL), "cooldown starts when it ends");
            cleanup(h, p);
            h.succeed();
        });
    }

    private static void shellShock(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(5.5, 1, 4.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), -90f, 0f);
        KnowledgeService.learn(p, CURRENT_SHELL);
        CastService.request(p, CURRENT_SHELL);
        p.attack(pig);
        h.assertTrue(pig.getHealth() <= pig.getMaxHealth() - 2f, "the blow shocks, health " + pig.getHealth());
        Toggles.disableAll(p);
        cleanup(h, p);
        h.succeed();
    }

    private static void conductivity(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.LIGHTNING, DISCHARGE, 1);
        Pig a = h.spawn(EntityTypes.PIG, new Vec3(2.5, 1, 2.5));
        Pig b = h.spawn(EntityTypes.PIG, new Vec3(2.5, 1, 6.5));
        a.setNoAi(true);
        b.setNoAi(true);
        long now = h.getLevel().getGameTime();
        EchoService.onSpellHit(p, Schools.LIGHTNING, a, 401);
        h.assertTrue(EchoService.markedBy(a, p, now), "a hit marks the target");
        EchoService.onSpellHit(p, Schools.LIGHTNING, b, 402);
        h.assertTrue(a.getHealth() < a.getMaxHealth(), "the next hit arcs to the marked pig");
        h.assertFalse(EchoService.markedBy(a, p, now), "the jump spends the mark");
        h.assertTrue(EchoService.markedBy(b, p, now), "and marks the new target");
        cleanup(h, p);
        h.succeed();
    }

    private static final Identifier ROOT_SHACKLES = OrbisArcana.id("root_shackles");
    private static final Identifier THORN_SEED = OrbisArcana.id("thorn_seed");

    private static void rootShackles(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 7.5));
        Pig neighbour = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 7.5));
        pig.setNoAi(true);
        neighbour.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(4.5, 1, 2.5), 0f, 15f);
        KnowledgeService.learn(p, ROOT_SHACKLES);
        KnowledgeService.setMastery(p, ROOT_SHACKLES, 5);
        CastService.request(p, ROOT_SHACKLES);
        h.assertTrue(CastService.isCasting(p), "a target under the crosshair");
        release(h, p, 16);
        succeedWhen(h, p, () -> {
            h.assertTrue(RootsBehavior.isHeld(pig), "the roots hold the target");
            h.assertTrue(RootsBehavior.isHeld(neighbour), "and spread to the neighbour (m5)");
        });
    }

    private static void thornSeed(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 6.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(4.5, 1, 2.5), 0f, 15f);
        KnowledgeService.learn(p, THORN_SEED);
        CastService.request(p, THORN_SEED);
        release(h, p, 26);
        succeedWhen(h, p, () -> h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the thicket pricks the pig"));
    }

    private static void thornGuardian(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 2.5), 0f, 40f);
        KnowledgeService.learn(p, THORN_SEED);
        KnowledgeService.setMastery(p, THORN_SEED, 15);
        CastService.request(p, THORN_SEED);
        release(h, p, 26);
        succeedWhen(h, p, () -> {
            var golems = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(4, 1, 4))).inflate(8));
            h.assertTrue(!golems.isEmpty(), "a guardian grows (m15)");
            golems.forEach(Entity::discard);
        });
    }

    private static void sprouting(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.NATURE, ROOT_SHACKLES, 1);
        p.setHealth(10f);
        EchoService.onEffectiveCast(p, Schools.NATURE, p.position(), 500);
        EchoService.onEffectiveCast(p, Schools.NATURE, p.position(), 500); // one sprout per cast
        h.assertValueEqual(EchoService.sprouts(p), 1, "one sprout per cast");
        succeedWhen(h, p, () -> h.assertTrue(p.getHealth() > 10f, "the sprout matures and heals the wounded player"));
    }

    private static final Identifier DAWN_RAY = OrbisArcana.id("dawn_ray");
    private static final Identifier PURIFYING_AURA = OrbisArcana.id("purifying_aura");
    private static final Identifier HEAVENS_JUDGMENT = OrbisArcana.id("heavens_judgment");

    private static void dawnRay(GameTestHelper h) {
        Pig front = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 4.5));
        Pig back = h.spawn(EntityTypes.PIG, new Vec3(7.5, 1, 4.5));
        front.setNoAi(true);
        back.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 10f);
        KnowledgeService.learn(p, DAWN_RAY);
        CastService.request(p, DAWN_RAY);
        h.assertTrue(CastService.isCasting(p), "channel starts");
        h.runAfterDelay(21, () -> {
            CastService.stop(p, CastService.activeCastId(p), false);
            h.assertTrue(front.getHealth() < front.getMaxHealth(), "the beam burns the first pig");
            h.assertValueEqual(back.getHealth(), back.getMaxHealth(), "and stops there");
            cleanup(h, p);
            h.succeed();
        });
    }

    private static void dawnRayPierce(GameTestHelper h) {
        Pig front = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 4.5));
        Pig back = h.spawn(EntityTypes.PIG, new Vec3(7.5, 1, 4.5));
        front.setNoAi(true);
        back.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 7f);
        KnowledgeService.learn(p, DAWN_RAY);
        KnowledgeService.setMastery(p, DAWN_RAY, 5);
        CastService.request(p, DAWN_RAY);
        h.runAfterDelay(21, () -> {
            CastService.stop(p, CastService.activeCastId(p), false);
            h.assertTrue(front.getHealth() < front.getMaxHealth(), "the beam burns the first pig");
            h.assertTrue(back.getHealth() < back.getMaxHealth(), "and pierces to the second (m5)");
            cleanup(h, p);
            h.succeed();
        });
    }

    private static void purifyingAura(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        KnowledgeService.learn(p, PURIFYING_AURA);
        KnowledgeService.setMastery(p, PURIFYING_AURA, 10);
        p.connection.markClientLoaded(); // mock players are invulnerable until the client "loads"
        CastService.request(p, PURIFYING_AURA);
        h.assertTrue(Toggles.isOn(p, PURIFYING_AURA), "the aura turns on");
        p.hurtServer(h.getLevel(), h.getLevel().damageSources().fellOutOfWorld(), 10f); // mock players are creative
        h.assertTrue(p.getHealth() >= 10.9f && p.getHealth() < 12f, "the aura takes 10% off, health " + p.getHealth());
        h.assertTrue(Shields.total(p) > 0, "a hit in the aura grants a ward shield (m10)");
        CastService.request(p, PURIFYING_AURA);
        h.assertFalse(Toggles.isOn(p, PURIFYING_AURA), "second press turns it off");
        cleanup(h, p);
        h.succeed();
    }

    private static void heavensJudgment(GameTestHelper h) {
        var golem = h.spawn(EntityTypes.IRON_GOLEM, new Vec3(4.5, 1, 7.5)); // survives the column, so the mark shows
        golem.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(4.5, 1, 2.5), 0f, 35f);
        KnowledgeService.learn(p, HEAVENS_JUDGMENT);
        CastService.request(p, HEAVENS_JUDGMENT);
        h.assertTrue(CastService.isCasting(p), "a charged cast starts");
        release(h, p, 62);
        succeedWhen(h, p, () -> {
            h.assertTrue(golem.getHealth() < golem.getMaxHealth(), "the column strikes the golem");
            h.assertTrue(golem.hasEffect(MobEffects.GLOWING), "and marks it");
            golem.discard();
        });
    }

    private static void hallowedGround(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 90f);
        KnowledgeService.learn(p, HEAVENS_JUDGMENT);
        KnowledgeService.setMastery(p, HEAVENS_JUDGMENT, 15);
        p.setHealth(10f);
        CastService.request(p, HEAVENS_JUDGMENT);
        release(h, p, 62);
        succeedWhen(h, p, () -> h.assertTrue(p.getHealth() > 10.5f, "hallowed ground heals the caster standing in it (m15)"));
    }

    private static void radiance(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.LIGHT, DAWN_RAY, 3);
        h.assertTrue(AttunementService.pickTalent(p, Schools.LIGHT, "b1").isEmpty(), "Veil picked");
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
        EchoService.onSupport(p, 10f);
        h.assertTrue(p.hasEffect(MobEffects.POISON), "half a charge does nothing yet");
        EchoService.onSupport(p, 10f);
        h.assertFalse(p.hasEffect(MobEffects.POISON), "a full charge pulses and cleanses");
        h.assertTrue(Shields.total(p) >= 4f, "Veil shields the allies around");
        h.assertValueEqual(EchoService.radiance(p), 0.0, "the charge is spent");
        cleanup(h, p);
        h.succeed();
    }

    private static final Identifier SHADOW_DART = OrbisArcana.id("shadow_dart");
    private static final Identifier WITHERING_CURSE = OrbisArcana.id("withering_curse");
    private static final Identifier SHADOW_STEP = OrbisArcana.id("shadow_step");

    private static void shadowDart(GameTestHelper h) {
        var golem = h.spawn(EntityTypes.IRON_GOLEM, new Vec3(7.5, 1, 4.5));
        golem.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 5f);
        KnowledgeService.learn(p, SHADOW_DART);
        KnowledgeService.setMastery(p, SHADOW_DART, 10);
        CastService.request(p, SHADOW_DART);
        h.assertFalse(CastService.isCasting(p), "instant");
        float[] afterFirst = new float[1];
        h.runAfterDelay(8, () -> {
            afterFirst[0] = golem.getHealth();
            h.assertTrue(afterFirst[0] < golem.getMaxHealth(), "the darts hit");
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(golem.getHealth() < afterFirst[0], "the echo hit lands a second later (m10)");
            golem.discard();
            cleanup(h, p);
            h.succeed();
        });
    }

    private static void witheringCurse(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 7.5));
        Pig neighbour = h.spawn(EntityTypes.PIG, new Vec3(6.5, 1, 7.5));
        pig.setNoAi(true);
        neighbour.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(4.5, 1, 2.5), 0f, 15f);
        KnowledgeService.learn(p, WITHERING_CURSE);
        KnowledgeService.setMastery(p, WITHERING_CURSE, 5);
        CastService.request(p, WITHERING_CURSE);
        h.assertTrue(CastService.isCasting(p), "a target under the crosshair");
        release(h, p, 26);
        h.runAfterDelay(30, () -> {
            h.assertTrue(CurseBehavior.isCursed(pig), "the pig is cursed");
            pig.kill(h.getLevel());
            h.assertTrue(CurseBehavior.isCursed(neighbour), "on death the curse jumps to the neighbour (m5)");
        });
        succeedWhen(h, p, () -> h.assertTrue(neighbour.getHealth() < neighbour.getMaxHealth(), "and withers it"));
    }

    private static void shadowStep(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 0f);
        KnowledgeService.learn(p, SHADOW_STEP);
        KnowledgeService.setMastery(p, SHADOW_STEP, 10);
        Vec3 start = p.position();
        CastService.request(p, SHADOW_STEP);
        h.assertTrue(p.position().x > start.x + 3, "stepped forward, at " + p.position());
        h.runAfterDelay(5, () -> {
            CastService.request(p, SHADOW_STEP);
            h.assertTrue(p.position().distanceTo(start) < 0.5, "a second press steps back (m10), at " + p.position());
            cleanup(h, p);
            h.succeed();
        });
    }

    private static void dusk(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.DARK, SHADOW_DART, 1);
        var golem = h.spawn(EntityTypes.IRON_GOLEM, new Vec3(2.5, 1, 2.5));
        golem.setNoAi(true);
        golem.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), p);
        h.assertTrue(EchoService.duskBy(golem, p), "a debuff from the player leaves a Dusk mark");
        ManaService.setMana(p, 10);
        golem.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), 2f);
        h.assertFalse(EchoService.duskBy(golem, p), "the blow absorbs the mark");
        h.assertTrue(ManaService.mana(p) >= 12.9, "and gives mana, " + ManaService.mana(p));
        h.assertTrue(golem.getHealth() <= golem.getMaxHealth() - 4f, "plus extra damage, health " + golem.getHealth());
        golem.discard();
        cleanup(h, p);
        h.succeed();
    }

    private static final Identifier STAR_SPIKE = OrbisArcana.id("star_spike");
    private static final Identifier GUARDIAN_ORBIT = OrbisArcana.id("guardian_orbit");
    private static final Identifier CONSTELLATION_SHIFT = OrbisArcana.id("constellation_shift");

    private static void starSpike(GameTestHelper h) {
        Pig front = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 4.5));
        Pig back = h.spawn(EntityTypes.PIG, new Vec3(7.5, 1, 4.5));
        front.setNoAi(true);
        back.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(1.5, 1, 4.5), -90f, 7f);
        KnowledgeService.learn(p, STAR_SPIKE);
        CastService.request(p, STAR_SPIKE);
        release(h, p, 20);
        succeedWhen(h, p, () -> {
            h.assertTrue(front.getHealth() < front.getMaxHealth() && back.getHealth() < back.getMaxHealth(), "the beam pierces both pigs");
            h.assertValueEqual(Stars.of(p).size(), 1, "and leaves a star");
        });
    }

    private static void guardianOrbit(GameTestHelper h) {
        var golem = h.spawn(EntityTypes.IRON_GOLEM, new Vec3(7.5, 1, 4.5));
        golem.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(3.5, 1, 4.5), -90f, 0f);
        KnowledgeService.learn(p, GUARDIAN_ORBIT);
        KnowledgeService.setMastery(p, GUARDIAN_ORBIT, 15);
        CastService.request(p, GUARDIAN_ORBIT);
        h.assertValueEqual(OrbitBehavior.stars(p), 3, "three stars at m5");
        var arrow = EntityTypes.ARROW.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        Vec3 from = h.absoluteVec(new Vec3(1.0, 2.1, 4.5));
        arrow.setPos(from.x, from.y, from.z);
        arrow.setDeltaMovement(0.6, 0, 0);
        arrow.setNoGravity(true);
        h.getLevel().addFreshEntity(arrow);
        h.runAfterDelay(20, () -> {
            h.assertTrue(OrbitBehavior.stars(p) < 3, "a star stops the arrow");
            CastService.request(p, GUARDIAN_ORBIT);
            h.assertValueEqual(OrbitBehavior.stars(p), 0, "a second press launches the rest (m15)");
            h.assertTrue(golem.getHealth() < golem.getMaxHealth(), "into the target");
            golem.discard();
            arrow.discard();
            cleanup(h, p);
            h.succeed();
        });
    }

    private static void constellationShift(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(7.5, 1, 4.5));
        ServerPlayer p = player(h, new Vec3(3.5, 1, 4.5), 0f, 90f);
        KnowledgeService.learn(p, CONSTELLATION_SHIFT);
        double start = pig.position().distanceTo(p.position());
        CastService.request(p, CONSTELLATION_SHIFT);
        release(h, p, 52);
        succeedWhen(h, p, () -> {
            h.assertTrue(ShiftBehavior.active() > 0, "the zone is up");
            h.assertTrue(pig.position().distanceTo(p.position()) < start - 1.5, "and drags the pig in");
            h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "and wears it down");
        });
    }

    private static void constellation(GameTestHelper h) {
        ServerPlayer p = boundTo(h, Schools.ASTRAL, STAR_SPIKE, 1);
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(3.5, 1, 3.5));
        pig.setNoAi(true);
        Vec3 c = h.absoluteVec(new Vec3(3.5, 1.5, 3.5));
        Stars.place(p, c.add(1.5, 0, 0), 0, 0);
        Stars.place(p, c.add(-1.5, 0, 1), 0, 0);
        h.assertValueEqual(Stars.of(p).size(), 2, "two stars wait");
        h.assertValueEqual(pig.getHealth(), pig.getMaxHealth(), "no pulse yet");
        Stars.place(p, c.add(0, 0, -1.5), 0, 0);
        h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the third star sets off the pulse");
        h.assertValueEqual(Stars.of(p).size(), 0, "the figure is spent");
        cleanup(h, p);
        h.succeed();
    }

    private static void foci(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.APPRENTICE_STAFF.get()));
        Foci.update(p);
        h.assertValueEqual(p.getAttributeValue(ModAttributes.SPELL_POWER), 1.15, "the staff adds power");
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(ModItems.APPRENTICE_TOME.get()));
        Foci.update(p);
        h.assertValueEqual(p.getAttributeValue(ModAttributes.SPELL_POWER), 1.0, "a busy off hand switches the two-handed staff off");
        h.assertValueEqual(p.getAttributeValue(ModAttributes.SPELL_COST), 0.9, "the tome works from the off hand");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.NETHERITE_WAND.get()));
        Foci.update(p);
        h.assertTrue(Math.abs(p.getAttributeValue(ModAttributes.CAST_SPEED) - 1.2) < 1e-6, "the wand speeds casts up");
        double before = ManaService.mana(p);
        KnowledgeService.learn(p, FIREBALL);
        CastService.request(p, FIREBALL);
        h.assertTrue(CastService.isCasting(p), "cast starts");
        h.assertTrue(before - ManaService.mana(p) < 10.0 * 0.95, "the tome makes it cheaper, spent " + (before - ManaService.mana(p)));
        CastService.stop(p, CastService.activeCastId(p), true);
        cleanup(h, p);
        h.succeed();
    }

    private static void manaPotion(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        ManaService.setMana(p, 10);
        ItemStack potion = new ItemStack(ModItems.MANA_POTION.get());
        ItemStack left = potion.finishUsingItem(h.getLevel(), p);
        h.assertValueEqual(ManaService.mana(p), 50.0, "the potion restores 40 mana");
        h.assertTrue(left.is(net.minecraft.world.item.Items.GLASS_BOTTLE) || p.getInventory().contains(new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE)) || p.isCreative(),
                "a bottle is left");
        cleanup(h, p);
        h.succeed();
    }

    private static void dismantle(GameTestHelper h) {
        ServerPlayer p = player(h, new Vec3(4.5, 1, 4.5), 0f, 0f);
        ItemStack unknown = SpellScrollItem.of(DAWN_RAY);
        h.assertFalse(ScrollService.dismantle(p, unknown), "an unknown spell's scroll is kept");
        h.assertValueEqual(unknown.getCount(), 1, "and not used up");
        KnowledgeService.learn(p, DAWN_RAY);
        ItemStack duplicate = SpellScrollItem.of(DAWN_RAY);
        h.assertTrue(ScrollService.dismantle(p, duplicate), "a duplicate is taken apart");
        h.assertTrue(duplicate.isEmpty() || p.isCreative(), "the scroll is used up (mock players are creative)");
        h.assertValueEqual(p.getInventory().countItem(ModItems.AETHER_INK.get()), 2, "a rare scroll gives 2 ink");
        cleanup(h, p);
        h.succeed();
    }

    private static void aetherOre(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, ModBlocks.AETHER_ORE.get());
        ItemStack pickaxe = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
        var drops = net.minecraft.world.level.block.Block.getDrops(h.getBlockState(pos), h.getLevel(), h.absolutePos(pos), null, null, pickaxe);
        h.assertTrue(drops.stream().anyMatch(s -> s.is(ModItems.AETHER_SHARD.get())), "the ore drops aether shards, got " + drops);
        h.succeed();
    }

    /** Rolls a vanilla chest table (with loot modifiers) many times and returns the scrolls found. */
    private static List<ItemStack> rollChest(GameTestHelper h, String table, int times) {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, Identifier.withDefaultNamespace(table));
        var loot = h.getLevel().getServer().reloadableRegistries().getLootTable(key);
        List<ItemStack> scrolls = new java.util.ArrayList<>();
        for (int i = 0; i < times; i++) {
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, h.absoluteVec(new Vec3(4, 1, 4)))
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
            loot.getRandomItems(params).stream().filter(s -> s.is(ModItems.SPELL_SCROLL.get())).forEach(scrolls::add);
        }
        return scrolls;
    }

    private static void dungeonLoot(GameTestHelper h) {
        List<ItemStack> scrolls = rollChest(h, "chests/simple_dungeon", 60);
        h.assertTrue(scrolls.size() > 5, "dungeon chests hold scrolls, found " + scrolls.size());
        for (ItemStack s : scrolls) {
            var def = SpellScrollItem.spell(s).flatMap(id -> SpellRegistry.get(h.getLevel().registryAccess(), id));
            h.assertTrue(def.isPresent(), "each scroll carries a spell");
            h.assertFalse(Schools.isHigher(def.get().school()), "no higher schools in dungeons");
            h.assertTrue(def.get().rarity().ordinal() <= com.orbisarcana.core.Rarity.RARE.ordinal(), "common to rare only");
        }
        h.succeed();
    }

    private static void ancientCityLoot(GameTestHelper h) {
        List<ItemStack> scrolls = rollChest(h, "chests/ancient_city", 40);
        h.assertTrue(!scrolls.isEmpty(), "ancient cities hold scrolls");
        for (ItemStack s : scrolls) {
            var def = SpellScrollItem.spell(s).flatMap(id -> SpellRegistry.get(h.getLevel().registryAccess(), id));
            h.assertTrue(def.isPresent() && def.get().school().equals(Schools.DARK), "of the Dark school");
        }
        h.succeed();
    }

    private OrbisGameTests() {}
}
