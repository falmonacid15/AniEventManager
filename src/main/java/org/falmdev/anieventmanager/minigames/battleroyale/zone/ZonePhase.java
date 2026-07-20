package org.falmdev.anieventmanager.minigames.battleroyale.zone;

public record ZonePhase(
        double radius,
        int    waitSeconds,
        int    shrinkSeconds,
        double damagePerSecond
) {
    public int totalSeconds() { return waitSeconds + shrinkSeconds; }
}