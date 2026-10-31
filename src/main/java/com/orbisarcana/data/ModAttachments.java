package com.orbisarcana.data;

import java.util.function.Supplier;

import com.orbisarcana.OrbisArcana;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Player data attachments (§15.3). */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, OrbisArcana.MODID);

    public static final Supplier<AttachmentType<PlayerResources>> RESOURCES = ATTACHMENTS.register("resources",
            () -> AttachmentType.builder(() -> new PlayerResources())
                    .serialize(PlayerResources.CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, PlayerResources.SYNC_CODEC)
                    .build());

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    private ModAttachments() {}
}
