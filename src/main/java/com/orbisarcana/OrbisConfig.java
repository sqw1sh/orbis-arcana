package com.orbisarcana;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative synced config (§15.8). Values are prototype defaults; filled in as phases land. */
public final class OrbisConfig {
    private static final ModConfigSpec.Builder SERVER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue MASTERY_XP_RATE = SERVER
            .comment("Global multiplier for mastery XP gain")
            .defineInRange("masteryXpRate", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.BooleanValue LEARN_REQUIREMENTS = SERVER
            .comment("Enforce rarity-based requirements when learning scrolls")
            .define("learnRequirements", true);

    public static final ModConfigSpec.BooleanValue ALLOW_FORGETTING = SERVER
            .comment("Allow players to forget learned spells")
            .define("allowForgetting", false);

    static final ModConfigSpec SERVER_SPEC = SERVER.build();

    private OrbisConfig() {}
}
