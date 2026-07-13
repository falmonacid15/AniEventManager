package org.falmdev.anieventmanager.cinematics;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class CinematicSpectators {

    private final Set<UUID> extra = new LinkedHashSet<>();

    public boolean add(Player player) {
        return extra.add(player.getUniqueId());
    }

    public boolean remove(Player player) {
        return extra.remove(player.getUniqueId());
    }

    public boolean isSpectator(Player player) {
        return extra.contains(player.getUniqueId());
    }

    public boolean isSpectator(UUID uuid) {
        return extra.contains(uuid);
    }

    public Set<UUID> getAll() {
        return Collections.unmodifiableSet(extra);
    }

    public void clear() {
        extra.clear();
    }
}