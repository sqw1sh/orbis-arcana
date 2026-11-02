package com.orbisarcana.example;

import java.util.function.Consumer;

import com.orbisarcana.api.OrbisArcanaAPI;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.mana.ManaService;
import com.orbisarcana.school.AttunementService;
import com.orbisarcana.school.Schools;
import com.orbisarcana.spell.CastService;
import com.orbisarcana.spell.SpellRegistry;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The addon's own GameTests: ./gradlew :example-addon:runGameTestServer. */
@SuppressWarnings("removal") // makeMockServerPlayerInLevel is the only helper that joins the player list
final class ExampleGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, ExampleAddon.MODID);
    private static final Identifier STRUCTURE = Identifier.fromNamespaceAndPath("orbisarcana", "test_platform");

    static {
        FUNCTIONS.register("blood_school_is_registered", () -> ExampleGameTests::school);
        FUNCTIONS.register("blood_bolt_drains_life", () -> ExampleGameTests::bloodBolt);
        FUNCTIONS.register("bloodlust_echo_heals", () -> ExampleGameTests::bloodlust);
    }

    static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener((RegisterGameTestsEvent event) -> {
            Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(ExampleAddon.id("default"));
            for (String name : new String[] {"blood_school_is_registered", "blood_bolt_drains_life", "bloodlust_echo_heals"}) {
                Identifier id = ExampleAddon.id(name);
                ResourceKey<Consumer<GameTestHelper>> fn = ResourceKey.create(Registries.TEST_FUNCTION, id);
                event.registerTest(id, data -> new FunctionGameTestInstance(fn, data), new TestData<>(env, STRUCTURE, 60, 1, true));
            }
        });
    }

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

    private static void done(GameTestHelper h, ServerPlayer p) {
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static void school(GameTestHelper h) {
        h.assertTrue(Schools.exists(ExampleAddon.BLOOD), "the Blood school is in the registry");
        h.assertValueEqual(Schools.color(ExampleAddon.BLOOD), 0xB01030, "with its colour");
        h.assertTrue(Schools.all().contains(ExampleAddon.BLOOD), "and in the school list, after the built-in ones");
        h.assertTrue(SpellRegistry.get(h.getLevel().registryAccess(), ExampleAddon.BLOOD_BOLT).isPresent(), "Blood Bolt loads from the datapack");
        h.succeed();
    }

    private static void bloodBolt(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 7.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(4.5, 1, 2.5), 0f, 15f);
        p.setHealth(10f);
        OrbisArcanaAPI api = OrbisArcanaAPI.get();
        api.learn(p, ExampleAddon.BLOOD_BOLT);
        api.cast(p, ExampleAddon.BLOOD_BOLT);
        h.assertTrue(CastService.isCasting(p), "a charged cast at the pig starts");
        h.runAfterDelay(18, () -> CastService.stop(p, CastService.activeCastId(p), false));
        h.succeedWhen(() -> {
            h.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the pig is drained");
            h.assertTrue(p.getHealth() > 10f, "the caster heals, health " + p.getHealth());
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    private static void bloodlust(GameTestHelper h) {
        Pig pig = h.spawn(EntityTypes.PIG, new Vec3(4.5, 1, 6.5));
        pig.setNoAi(true);
        ServerPlayer p = player(h, new Vec3(4.5, 1, 2.5), 0f, 0f);
        OrbisArcanaAPI api = OrbisArcanaAPI.get();
        api.learn(p, ExampleAddon.BLOOD_BOLT);
        AttunementService.bind(p, ExampleAddon.BLOOD);
        p.getData(ModAttachments.ATTUNEMENT).school(ExampleAddon.BLOOD).setResonance(1);
        p.setHealth(10f);
        api.reportHit(p, ExampleAddon.BLOOD_BOLT, 900, pig);
        h.assertValueEqual(p.getHealth(), 11f, "the Echo of the dominant Blood school heals 1 HP per hit");
        done(h, p);
    }

    private ExampleGameTests() {}
}
