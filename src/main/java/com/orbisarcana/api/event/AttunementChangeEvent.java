package com.orbisarcana.api.event;

import java.util.Optional;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** The player's dominant school changed: bound, switched or unbound (§10). Posted on {@code NeoForge.EVENT_BUS}. */
@ApiStatus.Experimental
public class AttunementChangeEvent extends Event {
    private final ServerPlayer player;
    private final Optional<Identifier> oldSchool;
    private final Optional<Identifier> newSchool;

    public AttunementChangeEvent(ServerPlayer player, Optional<Identifier> oldSchool, Optional<Identifier> newSchool) {
        this.player = player;
        this.oldSchool = oldSchool;
        this.newSchool = newSchool;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Optional<Identifier> getOldSchool() {
        return oldSchool;
    }

    public Optional<Identifier> getNewSchool() {
        return newSchool;
    }
}
