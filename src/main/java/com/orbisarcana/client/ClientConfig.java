package com.orbisarcana.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client options (§9.3, §9.7, §13.5). Editable in Mods → Orbis Arcana → Config. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static {
        B.push("circles");
    }

    public static final ModConfigSpec.BooleanValue CIRCLES_ENABLED = B
            .comment("Draw magic circles at all. Gameplay does not depend on them.")
            .define("enabled", true);

    /** How circles are drawn. */
    public enum RenderMode {
        /** Mesh; particles under an Iris shader pack only if the pipeline could not be registered with Iris. */
        AUTO,
        /** Always the mesh pipeline. */
        MESH,
        /** Rim traced with particles. */
        PARTICLES
    }

    public static final ModConfigSpec.EnumValue<RenderMode> RENDER_MODE = B
            .comment("AUTO = mesh (registered with Iris shader packs), particles only if Iris could not take the circle pipeline")
            .defineEnum("renderMode", RenderMode.AUTO);

    public static final ModConfigSpec.BooleanValue RIM_ONLY = B
            .comment("Draw only the outer rim of every circle")
            .define("rimOnly", false);

    public static final ModConfigSpec.DoubleValue OPACITY = B
            .comment("Opacity multiplier for all circles")
            .defineInRange("opacity", 1.0, 0.1, 1.0);

    public static final ModConfigSpec.DoubleValue OWN_OPACITY = B
            .comment("Extra opacity multiplier for your own first-person circle")
            .defineInRange("ownOpacity", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue OWN_SCALE = B
            .comment("Size multiplier for your own first-person circle (still capped at 25% of the screen height)")
            .defineInRange("ownScale", 1.0, 0.25, 1.0);

    public static final ModConfigSpec.IntValue RIM_ONLY_DISTANCE = B
            .comment("Beyond this distance (blocks) only the rim is drawn")
            .defineInRange("rimOnlyDistance", 32, 4, 256);

    public static final ModConfigSpec.IntValue HIDE_DISTANCE = B
            .comment("Beyond this distance (blocks) circles are not drawn")
            .defineInRange("hideDistance", 64, 8, 512);

    public static final ModConfigSpec.IntValue MAX_CIRCLES = B
            .comment("Maximum circles drawn at once; nearest win")
            .defineInRange("maxCircles", 48, 1, 512);

    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private ClientConfig() {}
}
