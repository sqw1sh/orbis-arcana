package com.orbisarcana.data;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.orbisarcana.OrbisArcana;
import com.orbisarcana.progression.KnownSpells;
import com.orbisarcana.progression.Loadout;
import com.orbisarcana.school.Attunement;

import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Player data attachments (§15.3). All are saved, kept on death and synced to their owner only. */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, OrbisArcana.MODID);

    public static final Supplier<AttachmentType<PlayerResources>> RESOURCES = ATTACHMENTS.register("resources",
            () -> AttachmentType.builder(() -> new PlayerResources())
                    .serialize(PlayerResources.CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, PlayerResources.SYNC_CODEC)
                    .build());

    public static final Supplier<AttachmentType<KnownSpells>> KNOWLEDGE = ATTACHMENTS.register("knowledge",
            () -> ownerSynced(KnownSpells::new, KnownSpells.CODEC));

    public static final Supplier<AttachmentType<Attunement>> ATTUNEMENT = ATTACHMENTS.register("attunement",
            () -> ownerSynced(Attunement::new, Attunement.CODEC));

    public static final Supplier<AttachmentType<Loadout>> LOADOUT = ATTACHMENTS.register("loadout",
            () -> ownerSynced(Loadout::new, Loadout.CODEC));

    /** Whether the player already got the starter kit (§4). Server-only. */
    public static final Supplier<AttachmentType<Boolean>> ONBOARDED = ATTACHMENTS.register("onboarded",
            () -> AttachmentType.builder(() -> false).serialize(com.mojang.serialization.Codec.BOOL.fieldOf("done")).copyOnDeath().build());

    /** Full-state sync; switch to deltas if these grow large (§15.3). */
    private static <T> AttachmentType<T> ownerSynced(Supplier<T> factory, MapCodec<T> codec) {
        return AttachmentType.builder(factory)
                .serialize(codec)
                .copyOnDeath()
                .sync((holder, to) -> holder == to, ByteBufCodecs.fromCodecWithRegistries(codec.codec()))
                .build();
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    private ModAttachments() {}
}
