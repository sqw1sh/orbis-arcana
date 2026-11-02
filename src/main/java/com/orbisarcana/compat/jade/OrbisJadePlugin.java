package com.orbisarcana.compat.jade;

import com.orbisarcana.OrbisArcana;
import com.orbisarcana.block.ArcaneSealBlock;
import com.orbisarcana.block.GlyphRingBlock;
import com.orbisarcana.block.ResonanceAltarBlock;
import com.orbisarcana.data.ModAttachments;
import com.orbisarcana.school.Schools;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade integration (§17), loaded by Jade only. Everything shown is already on the client: the altar tells your own
 * school and resonance, glyph rings their glyph, the seal how to break it.
 */
@WailaPlugin
public final class OrbisJadePlugin implements IWailaPlugin {
    private static final String[] GLYPHS = {"star", "eye", "moon", "spiral"};

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Altar.INSTANCE, ResonanceAltarBlock.class);
        registration.registerBlockComponent(Glyph.INSTANCE, GlyphRingBlock.class);
        registration.registerBlockComponent(Seal.INSTANCE, ArcaneSealBlock.class);
    }

    /** Your dominant school and its resonance (the attunement is synced to its owner). */
    enum Altar implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            var attunement = player.getData(ModAttachments.ATTUNEMENT);
            attunement.dominant().ifPresentOrElse(school -> {
                int resonance = attunement.schools().containsKey(school) ? attunement.schools().get(school).resonance() : 0;
                tooltip.add(Component.translatable("jade.orbisarcana.altar.school",
                        Component.translatable(Schools.nameKey(school)).withColor(Schools.color(school)), resonance));
            }, () -> tooltip.add(Component.translatable("jade.orbisarcana.altar.none").withStyle(ChatFormatting.GRAY)));
            tooltip.add(Component.translatable("jade.orbisarcana.altar.hint").withStyle(ChatFormatting.DARK_GRAY));
        }

        @Override
        public Identifier getUid() {
            return OrbisArcana.id("altar");
        }
    }

    /** Which glyph a ring shows, and whether it is the key. */
    enum Glyph implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            var state = accessor.getBlockState();
            Component glyph = Component.translatable("jade.orbisarcana.glyph." + GLYPHS[state.getValue(GlyphRingBlock.GLYPH)]);
            tooltip.add(Component.translatable(state.getValue(GlyphRingBlock.KEY) ? "jade.orbisarcana.glyph.key" : "jade.orbisarcana.glyph.ring", glyph));
        }

        @Override
        public Identifier getUid() {
            return OrbisArcana.id("glyph_ring");
        }
    }

    /** How to open an arcane seal. */
    enum Seal implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            tooltip.add(Component.translatable("jade.orbisarcana.seal").withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        @Override
        public Identifier getUid() {
            return OrbisArcana.id("arcane_seal");
        }
    }
}
