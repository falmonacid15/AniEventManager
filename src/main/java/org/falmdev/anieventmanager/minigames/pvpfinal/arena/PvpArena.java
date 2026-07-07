package org.falmdev.anieventmanager.minigames.pvpfinal.arena;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;


public class PvpArena {

    private String        name;
    private List<Location> spawns;
    private Location      lobby;
    private Location      hologramLocation;

    public PvpArena(String name) {
        this.name   = name;
        this.spawns = new ArrayList<>();
        this.lobby  = null;
        this.hologramLocation = null;
    }

    public PvpArena(String name, List<Location> spawns, Location lobby, Location hologramLocation) {
        this.name   = name;
        this.spawns = spawns != null ? spawns : new ArrayList<>();
        this.lobby  = lobby;
        this.hologramLocation = hologramLocation;
    }

    public String         getName()   { return name; }
    public List<Location> getSpawns() { return spawns; }
    public Location       getLobby()  { return lobby; }
    public Location       getHologramLocation() { return hologramLocation; }
    public int            getSpawnCount() { return spawns.size(); }

    public void setLobby(Location loc) { this.lobby = loc; }
    public void setHologramLocation(Location loc) { this.hologramLocation = loc; }

    public void setSpawn(int index, Location loc) {
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