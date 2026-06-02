package org.falmdev.anieventmanager.minigames.pvpfinal.model;

/**
 * Modos de combate del minijuego PvP Final.
 *
 *  ONE_VS_ONE:   2 jugadores elegidos manualmente. Si son del mismo equipo,
 *                se permite friendly fire. Modo "oficial" del evento.
 *  TEAM_VS_TEAM: 2 equipos completos enfrentados. Friendly fire OFF
 *                (no se dañan compañeros del mismo equipo).
 *  ALL_TEAMS:    Todos los equipos con miembros vivos contra todos.
 *                Respeta equipos (no friendly fire).
 *  FFA:          Free-for-all entre todos los jugadores listados.
 *                Sin teams, todos vs todos.
 */
public enum CombatMode {
    ONE_VS_ONE("1v1"),
    TEAM_VS_TEAM("Team vs Team"),
    ALL_TEAMS("Todos los equipos"),
    FFA("FFA");

    private final String label;
    CombatMode(String label) { this.label = label; }
    public String getLabel() { return label; }
}