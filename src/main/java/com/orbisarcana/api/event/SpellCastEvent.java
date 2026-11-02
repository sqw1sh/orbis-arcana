package com.orbisarcana.api.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** A player casts a spell (§16). Posted on {@code NeoForge.EVENT_BUS}, server side. */
public abstract class SpellCastEvent extends Event {
    private final ServerPlayer player;
    private final Identifier spell;

    protected SpellCastEvent(ServerPlayer player, Identifier spell) {
        this.player = player;
        this.spell = spell;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Identifier getSpell() {
        return spell;
    }

    /** After every rule passed, before mana is taken and the cast starts. Cancel to stop it silently. */
    public static class Pre extends SpellCastEvent implements ICancellableEvent {
        public Pre(ServerPlayer player, Identifier spell) {
            super(player, spell);
        }
    }

    /** The spell was released: its behavior has run. */
    public static class Post extends SpellCastEvent {
        public Post(ServerPlayer player, Identifier spell) {
            super(player, spell);
        }
    }
}
