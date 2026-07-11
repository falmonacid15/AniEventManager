package org.falmdev.anieventmanager.minigames.boatracing;

import org.bukkit.entity.Boat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Datos de carrera de un jugador.
 * Maneja qualy y carrera de forma independiente.
 */
public class RacerData {

    private final UUID   playerUUID;
    private final String playerName;

    // ── Bote ──────────────────────────────────────────────────────────────────
    private Boat     boat;
    private BoatType boatType = BoatType.OAK;

    // ── Qualy ─────────────────────────────────────────────────────────────────
    private boolean qualyStarted   = false;
    private boolean qualyFinished  = false;
    private long    qualyStartMs   = 0;
    private long    qualyTimeMs    = 0;   // 0 = no terminó (DNF)
    private int     qualyPosition  = 0;   // posición en parrilla
    public enum QualyCrossResult { OUT_LAP_STARTED, TIMED_LAP_STARTED, FINISHED, IGNORED }
    private boolean qualyOutLapDone = false;



    // ── Carrera ───────────────────────────────────────────────────────────────
    private int     currentLap     = 0;
    private long    lapStartMs     = 0;
    private long    lastLapTimeMs  = 0;
    private long    bestLapTimeMs  = 0;
    private boolean raceFinished   = false;
    private int     racePosition   = 0;
    private final List<Long> lapTimes = new ArrayList<>();

    // Último checkpoint alcanzado en la vuelta actual (para posición en tiempo real)
    private int lastCheckpoint = -1;

    public RacerData(UUID playerUUID, String playerName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
    }

    // ── Qualy ─────────────────────────────────────────────────────────────────

    public QualyCrossResult crossQualyFinish() {
        if (qualyFinished) return QualyCrossResult.IGNORED;
        if (!qualyOutLapDone) {
            qualyOutLapDone = true;
            return QualyCrossResult.OUT_LAP_STARTED;
        }
        if (!qualyStarted) {
            qualyStarted = true;
            qualyStartMs = System.currentTimeMillis();
            return QualyCrossResult.TIMED_LAP_STARTED;
        }
        qualyFinished = true;
        qualyTimeMs   = System.currentTimeMillis() - qualyStartMs;
        return QualyCrossResult.FINISHED;
    }

    public boolean isQualyOutLapDone() { return qualyOutLapDone; }

    public String getCurrentQualyLapTimeFormatted() {
        if (!qualyStarted || qualyFinished) return "--:--.---";
        return formatTime(System.currentTimeMillis() - qualyStartMs);
    }

    // ── Carrera ───────────────────────────────────────────────────────────────

    // true cuando el jugador cruzó la meta por primera vez (inicio real de la vuelta 1)
    private boolean raceStarted = false;

    /**
     * Llamado cuando el piloto cruza la meta al INICIO de la carrera.
     * Inicia el cronómetro de la vuelta 1.
     */
    public void startRace() {
        currentLap     = 1;
        lapStartMs     = System.currentTimeMillis();
        lastCheckpoint = -1;
        raceStarted    = true;
    }

    public boolean isRaceStarted() { return raceStarted; }

    /**
     * Completa la vuelta actual. Devuelve true si terminó la carrera.
     * Solo se llama al cruzar la meta después de que la vuelta ya inició.
     */
    public boolean completeLap(int totalLaps) {
        if (currentLap == 0 || raceFinished) return false;
        long lapTime = System.currentTimeMillis() - lapStartMs;
        lapTimes.add(lapTime);
        lastLapTimeMs = lapTime;
        if (bestLapTimeMs == 0 || lapTime < bestLapTimeMs) bestLapTimeMs = lapTime;

        if (currentLap >= totalLaps) {
            raceFinished = true;
            return true;
        }
        currentLap++;
        lapStartMs     = System.currentTimeMillis();
        lastCheckpoint = -1;
        return false;
    }

    public void reachCheckpoint(int index) {
        if (index > lastCheckpoint) lastCheckpoint = index;
    }

    // ── Formato de tiempo ─────────────────────────────────────────────────────

    public static String formatTime(long ms) {
        if (ms <= 0) return "--:--.---";
        long mins   = ms / 60000;
        long secs   = (ms % 60000) / 1000;
        long millis = ms % 1000;
        return String.format("%d:%02d.%03d", mins, secs, millis);
    }

    public String getCurrentLapTimeFormatted() {
        if (lapStartMs == 0 || currentLap == 0) return "--:--.---";
        return formatTime(System.currentTimeMillis() - lapStartMs);
    }

    public String getQualyTimeFormatted() {
        return qualyTimeMs > 0 ? formatTime(qualyTimeMs) : "DNF";
    }

    public String getBestLapFormatted() {
        return bestLapTimeMs > 0 ? formatTime(bestLapTimeMs) : "--:--.---";
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID    getPlayerUUID()  { return playerUUID; }
    public String  getPlayerName()  { return playerName; }

    public Boat    getBoat()        { return boat; }
    public void    setBoat(Boat b)  { this.boat = b; }

    public BoatType getBoatType()            { return boatType; }
    public void     setBoatType(BoatType t)  { this.boatType = t; }

    public boolean isQualyStarted()   { return qualyStarted; }
    public boolean isQualyFinished()  { return qualyFinished; }
    public long    getQualyTimeMs()   { return qualyTimeMs; }
    public int     getQualyPosition() { return qualyPosition; }
    public void    setQualyPosition(int p) { this.qualyPosition = p; }

    public int     getCurrentLap()    { return currentLap; }
    public long    getLastLapTimeMs() { return lastLapTimeMs; }
    public long    getBestLapTimeMs() { return bestLapTimeMs; }
    public boolean isRaceFinished()   { return raceFinished; }
    public int     getRacePosition()  { return racePosition; }
    public void    setRacePosition(int p) { this.racePosition = p; }
    public int     getLastCheckpoint(){ return lastCheckpoint; }
    public List<Long> getLapTimes()   { return lapTimes; }
}