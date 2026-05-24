package org.falmdev.anieventmanager.model;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EventTeam {

    private final String id;
    private String displayName;                       // ya no es final
    private final NamedTextColor color;
    private final List<UUID> members = new ArrayList<>();

    public EventTeam(String id, String displayName, NamedTextColor color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }

    public boolean addMember(UUID uuid) {
        if (members.size() >= 2) return false;
        if (members.contains(uuid)) return false;
        members.add(uuid);
        return true;
    }

    public boolean removeMember(UUID uuid) {
        return members.remove(uuid);
    }

    public boolean hasMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isFull() {
        return members.size() >= 2;
    }

    public List<Player> getOnlinePlayers() {
        List<Player> online = new ArrayList<>();
        for (UUID uuid : members) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) online.add(p);
        }
        return online;
    }

    public String getId()             { return id; }
    public String getDisplayName()    { return displayName; }
    public NamedTextColor getColor()  { return color; }
    public List<UUID> getMembers()    { return List.copyOf(members); }
    public int getMemberCount()       { return members.size(); }

    /**
     * Cambia el displayName del equipo. La persistencia debe hacerla quien lo llama
     * (típicamente {@code TeamManager.renameTeam(id, newName)}).
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() { return displayName; }
}