package org.falmdev.anieventmanager.minigames.battleroyale.model;

import org.bukkit.Location;
import java.util.UUID;

/**
 * Estado en tiempo real de un jugador durante el Battle Royale.
 */
public class BRPlayer {

    public enum State {
        WAITING,      // en lobby esperando
        ON_DRAGON,    // montado en el dragón, aún no saltó
        PARACHUTING,  // cayendo con elytra + slow falling
        ALIVE,        // en tierra, combate activo
        DEAD,         // eliminado
        SPECTATING    // espectador tras morir
    }

    private final UUID   uuid;
    private final String name;

    private State    state            = State.WAITING;
    private int      kills            = 0;
    private int      coins            = 0;
    private boolean  hasLanded        = false;
    private int      dragonSeatIndex  = 0;
    private Location lastGroundLocation = null;

    public BRPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID    getUuid()  { return uuid; }
    public String  getName()  { return name; }

    public State   getState()             { return state; }
    public void    setState(State s)      { this.state = s; }

    public boolean isAlive()       { return state == State.ALIVE || state == State.PARACHUTING || state == State.ON_DRAGON; }
    public boolean isOnDragon()    { return state == State.ON_DRAGON; }
    public boolean isParachuting() { return state == State.PARACHUTING; }
    public boolean isDead()        { return state == State.DEAD || state == State.SPECTATING; }

    public int  getKills()          { return kills; }
    public void addKill()           { kills++; }

    public int  getCoins()          { return coins; }
    public void addCoins(int n)     { coins += n; }
    public void removeCoins(int n)  { coins = Math.max(0, coins - n); }
    public void setCoins(int n)     { coins = Math.max(0, n); }

    public boolean hasLanded()              { return hasLanded; }
    public void    setHasLanded(boolean v)  { this.hasLanded = v; }

    public int  getDragonSeatIndex()        { return dragonSeatIndex; }
    public void setDragonSeatIndex(int i)   { this.dragonSeatIndex = i; }

    public Location getLastGroundLocation()           { return lastGroundLocation; }
    public void     setLastGroundLocation(Location l) { this.lastGroundLocation = l; }
}