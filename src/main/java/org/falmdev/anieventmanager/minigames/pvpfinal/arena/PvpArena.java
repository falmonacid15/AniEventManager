package org.falmdev.anieventmanager.minigames.pvpfinal.arena;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Arena de PvP — múltiples spawns + un lobby común post-muerte.
 *
 * Solo existe una arena activa en el sistema. Los spawns se asignan según
 * el modo de combate:
 *  - 1v1: spawn 1 y 2
 *  - TeamVsTeam: spawn 1 y 2 para los 2 equipos
 *  - FFA / AllTeams: spawns 1, 2, 3, ... repartidos round-robin
 */
public class PvpArena {

    private String        name;
    private List<Location> spawns;
    private Location      lobby;

    public PvpArena(String name) {
        this.name   = name;
        this.spawns = new ArrayList<>();
        this.lobby  = null;
    }

    public PvpArena(String name, List<Location> spawns, Location lobby) {
        this.name   = name;
        this.spawns = spawns != null ? spawns : new ArrayList<>();
        this.lobby  = lobby;
    }

    public String         getName()   { return name; }
    public List<Location> getSpawns() { return spawns; }
    public Location       getLobby()  { return lobby; }
    public int            getSpawnCount() { return spawns.size(); }

    public void setLobby(Location loc) { this.lobby = loc; }

    public void setSpawn(int index, Location loc) {
        // index es 1-based
        int idx = index - 1;
        while (spawns.size() <= idx) spawns.add(null);
        spawns.set(idx, loc);
    }

    public Location getSpawn(int index) {
        int idx = index - 1;
        if (idx < 0 || idx >= spawns.size()) return null;
        return spawns.get(idx);
    }

    public boolean isReady() {
        if (lobby == null) return false;
        if (spawns.size() < 2) return false;
        for (Location l : spawns) if (l == null) return false;
        return true;
    }
}