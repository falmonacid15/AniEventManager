package org.falmdev.anieventmanager.minigames.battleroyale.model;

import org.falmdev.anieventmanager.model.EventTeam;

import java.util.List;

/**
 * Wrapper de EventTeam con estado específico del Battle Royale.
 */
public class BRTeam {

    private final EventTeam team;
    private boolean eliminated = false;
    private int     placement  = 0;   // posición final (1 = ganador)

    public BRTeam(EventTeam team) {
        this.team = team;
    }

    public EventTeam getTeam()          { return team; }
    public String    getId()            { return team.getId(); }
    public String    getDisplayName()   { return team.getDisplayName(); }

    public boolean   isEliminated()     { return eliminated; }
    public void      setEliminated(boolean v) { this.eliminated = v; }

    public int       getPlacement()     { return placement; }
    public void      setPlacement(int p){ this.placement = p; }

    /**
     * El equipo está vivo si tiene al menos un jugador en estado ALIVE,
     * PARACHUTING u ON_DRAGON.
     */
    public boolean isAlive(List<BRPlayer> players) {
        return players.stream()
                .filter(p -> team.getMembers().contains(p.getUuid()))
                .anyMatch(BRPlayer::isAlive);
    }
}