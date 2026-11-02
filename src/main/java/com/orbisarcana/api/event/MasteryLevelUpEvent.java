package com.orbisarcana.api.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * A spell reached a new mastery level through XP (§7). {@link Milestone} follows for levels 5, 10 and 15. Posted on
 * {@code NeoForge.EVENT_BUS}, server side.
 */
public class MasteryLevelUpEvent extends Event {
    private final ServerPlayer player;
    private final Identifier spell;
    private final int level;

    public MasteryLevelUpEvent(ServerPlayer player, Identifier spell, int level) {
        this.player = player;
        this.spell = spell;
        this.level = level;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Identifier getSpell() {
        return spell;
    }

    public int getLevel() {
        return level;
    }

    /** The new level is a milestone (5, 10 or 15) that changes the spell. */
    public static class Milestone extends MasteryLevelUpEvent {
        public Milestone(ServerPlayer player, Identifier spell, int level) {
            super(player, spell, level);
        }
    }
}
