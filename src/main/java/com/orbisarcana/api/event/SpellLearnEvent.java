package com.orbisarcana.api.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** A player learned a spell for good (§6). Posted on {@code NeoForge.EVENT_BUS}, server side. */
public class SpellLearnEvent extends Event {
    private final ServerPlayer player;
    private final Identifier spell;

    public SpellLearnEvent(ServerPlayer player, Identifier spell) {
        this.player = player;
        this.spell = spell;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Identifier getSpell() {
        return spell;
    }
}
