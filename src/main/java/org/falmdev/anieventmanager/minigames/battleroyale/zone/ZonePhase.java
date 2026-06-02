package org.falmdev.anieventmanager.minigames.battleroyale.zone;

/**
 * Una fase de la zona — radio (mitad del lado del cuadrado), tiempos y daño.
 *
 * Flujo de cada fase:
 *  1. Estado WAITING — dura {@code waitSeconds}. La zona queda en el radio
 *     de la fase anterior (o el inicial si es la primera).
 *  2. Estado SHRINKING — dura {@code shrinkSeconds}. La zona se reduce
 *     linealmente del radio anterior al {@code radius} de esta fase.
 *  3. Cuando termina, pasa a la siguiente fase (vuelve a WAITING).
 *
 * El daño {@code damagePerSecond} se aplica a quien esté fuera del cuadrado
 * actual durante TODA la fase (waiting y shrinking).
 */
public record ZonePhase(
        double radius,          // half-side del cuadrado (radio "cuadrado")
        int    waitSeconds,     // tiempo de espera antes de empezar a comprimir
        int    shrinkSeconds,   // tiempo que tarda la compresión
        double damagePerSecond  // daño/s a quien quede fuera
) {
    public int totalSeconds() { return waitSeconds + shrinkSeconds; }
}