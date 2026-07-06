package org.falmdev.anieventmanager.minigames.pvpfinal.model;


public enum CombatMode {
    ONE_VS_ONE("1v1"),
    TEAM_VS_TEAM("Team vs Team"),
    ALL_TEAMS("Todos los equipos"),
    FFA("FFA");

    private final String label;
    CombatMode(String label) { this.label = label; }
    public String getLabel() { return label; }
}