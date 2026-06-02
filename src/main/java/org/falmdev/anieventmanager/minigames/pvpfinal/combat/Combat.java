package org.falmdev.anieventmanager.minigames.pvpfinal.combat;

import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.minigames.pvpfinal.kit.PvpKit;
import org.falmdev.anieventmanager.minigames.pvpfinal.model.CombatMode;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.*;

/**
 * Combat — representa un combate activo.
 *
 * Estructura interna:
 *  - participants:  todos los jugadores que entraron al combate
 *  - alive:         set de UUIDs de los que aún están vivos
 *  - teams:         map UUID → ID del equipo (puede ser null para FFA puro)
 *  - kit:           kit aplicado a todos
 *  - friendlyFire:  si los del mismo equipo se pueden dañar
 *
 * Para 1v1, si son del mismo equipo se activa friendly fire automáticamente.
 */
public class Combat {

    private final CombatMode    mode;
    private final List<UUID>    participants;
    private final Set<UUID>     alive;
    private final Map<UUID, String> teamByPlayer;  // UUID → teamId (null si FFA puro)
    private final PvpKit        kit;
    private final boolean       friendlyFire;
    private final long          startTime;

    public Combat(CombatMode mode, List<Player> participants,
                  Map<UUID, String> teamByPlayer, PvpKit kit, boolean friendlyFire) {
        this.mode         = mode;
        this.participants = new ArrayList<>();
        this.alive        = new HashSet<>();
        for (Player p : participants) {
            this.participants.add(p.getUniqueId());
            this.alive.add(p.getUniqueId());
        }
        this.teamByPlayer = teamByPlayer != null ? teamByPlayer : new HashMap<>();
        this.kit          = kit;
        this.friendlyFire = friendlyFire;
        this.startTime    = System.currentTimeMillis();
    }

    public CombatMode      getMode()         { return mode; }
    public List<UUID>      getParticipants() { return participants; }
    public Set<UUID>       getAlive()        { return alive; }
    public PvpKit          getKit()          { return kit; }
    public boolean         isFriendlyFire()  { return friendlyFire; }
    public long            getStartTime()    { return startTime; }
    public Map<UUID, String> getTeamByPlayer() { return teamByPlayer; }

    public boolean isAlive(UUID uuid)    { return alive.contains(uuid); }
    public void    markDead(UUID uuid)   { alive.remove(uuid); }
    public boolean isParticipant(UUID u) { return participants.contains(u); }

    public String getTeamId(UUID uuid)   { return teamByPlayer.get(uuid); }

    /**
     * Cuenta equipos distintos con al menos 1 jugador vivo.
     * Útil para detectar fin de combate en modos team-based.
     */
    public int countAliveTeams() {
        Set<String> teams = new HashSet<>();
        for (UUID uuid : alive) {
            String teamId = teamByPlayer.get(uuid);
            if (teamId != null) teams.add(teamId);
            else teams.add(uuid.toString());  // FFA → cada uno es su "equipo"
        }
        return teams.size();
    }

    /**
     * Devuelve los IDs de equipos que aún tienen al menos un jugador vivo.
     */
    public Set<String> getAliveTeamIds() {
        Set<String> teams = new HashSet<>();
        for (UUID uuid : alive) {
            String teamId = teamByPlayer.get(uuid);
            if (teamId != null) teams.add(teamId);
        }
        return teams;
    }

    /** Segundos transcurridos desde el inicio. */
    public int getElapsedSeconds() {
        return (int) ((System.currentTimeMillis() - startTime) / 1000);
    }

    /**
     * Devuelve true si dos jugadores se pueden dañar entre sí en este combate.
     * Reglas:
     *  - Si friendly fire está activo → siempre true
     *  - Si están en distintos equipos → true
     *  - Si están en el mismo equipo → false
     *  - Si alguno no tiene equipo asignado → true (FFA)
     */
    public boolean canDamage(UUID attacker, UUID victim) {
        if (friendlyFire) return true;
        String teamA = teamByPlayer.get(attacker);
        String teamV = teamByPlayer.get(victim);
        if (teamA == null || teamV == null) return true;
        return !teamA.equals(teamV);
    }
}