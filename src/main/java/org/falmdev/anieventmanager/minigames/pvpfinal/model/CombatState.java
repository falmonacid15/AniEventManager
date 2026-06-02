package org.falmdev.anieventmanager.minigames.pvpfinal.model;

/**
 * Estados del combate actual.
 *
 * IDLE       — no hay combate activo
 * PREPARING  — teleport a spawns, aplicar kit, congelar jugadores
 * COUNTDOWN  — cuenta regresiva 5 -> 0
 * FIGHTING   — combate activo, daño habilitado
 * ENDING     — combate terminó, mostrando resultado antes de limpiar
 */
public enum CombatState {
    IDLE,
    PREPARING,
    COUNTDOWN,
    FIGHTING,
    ENDING
}