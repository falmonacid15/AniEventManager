package org.falmdev.anieventmanager.minigames.pvpfinal.combat;

import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.minigames.pvpfinal.kit.PvpKit;
import org.falmdev.anieventmanager.minigames.pvpfinal.model.CombatMode;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.*;


public class Combat {

    private final CombatMode    mode;
    private final List<UUID>    participants;
    private final Set<UUID>     alive;
    private final Map<UUID, String> teamByPlayer;
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

    public int countAliveTeams() {
        Set<String> teams = new HashSet<>();
        for (UUID uuid : alive) {
            String teamId = teamByPlayer.get(uuid);
            if (teamId != null) teams.add(teamId);
            else teams.add(uuid.toString());
        }
        return teams.size();
    }

    private boolean groupsByTeam() {
        return mode == CombatMode.TEAM_VS_TEAM || mode == CombatMode.ALL_TEAMS;
    }

    public List<List<UUID>> getSidesOrdered() {
        Map<String, List<UUID>> grouped = new LinkedHashMap<>();
        boolean byTeam = groupsByTeam();
        for (UUID uuid : participants) {
            String key = byTeam ? teamByPlayer.getOrDefault(uuid, uuid.toString()) : uuid.toString();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(uuid);
        }
        return new ArrayList<>(grouped.values());
    }

    public Set<String> getAliveTeamIds() {
        Set<String> teams = new HashSet<>();
        for (UUID uuid : alive) {
            String teamId = teamByPlayer.get(uuid);
            if (teamId != null) teams.add(teamId);
        }
        return teams;
    }

    public int getElapsedSeconds() {
        return (int) ((System.currentTimeMillis() - startTime) / 1000);
    }

    public boolean canDamage(UUID attacker, UUID victim) {
        if (friendlyFire) return true;
        String teamA = teamByPlayer.get(attacker);
        String teamV = teamByPlayer.get(victim);
        if (teamA == null || teamV == null) return true;
        return !teamA.equals(teamV);
    }
}