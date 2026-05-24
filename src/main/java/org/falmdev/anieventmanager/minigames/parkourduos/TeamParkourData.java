package org.falmdev.anieventmanager.minigames.parkourduos;

import org.falmdev.anieventmanager.model.EventTeam;

import java.util.List;

/**
 * Estado en tiempo real de un equipo durante la partida de Parkour Duos.
 */
public class TeamParkourData {

    private final EventTeam team;
    private final List<ParkourCheckpoint> checkpoints;

    // Índice del siguiente checkpoint que debe completar (0-based)
    private int nextCheckpointIndex = 0;

    // true si completó el recorrido completo (llegó al finish)
    private boolean finished = false;

    // Tiempo en ms en que terminó el recorrido (para ranking)
    private long finishTimeMs = 0;

    // Posición final en el ranking (1° que termina = 1, etc.)
    private int finishRank = 0;

    // Cuántos jugadores del equipo están dentro del checkpoint actual
    // Se actualiza cada tick
    private int playersInCurrentCheckpoint = 0;

    // Score interno acumulado durante la partida
    private int internalScore = 0;

    public TeamParkourData(EventTeam team, List<ParkourCheckpoint> checkpoints) {
        this.team        = team;
        this.checkpoints = checkpoints;
    }

    // ── Progreso ──────────────────────────────────────────────────────────────

    /**
     * Devuelve el checkpoint activo (siguiente a completar), o null si ya terminó.
     */
    public ParkourCheckpoint getActiveCheckpoint() {
        if (finished || nextCheckpointIndex >= checkpoints.size()) return null;
        return checkpoints.get(nextCheckpointIndex);
    }

    /**
     * Avanza al siguiente checkpoint. Devuelve true si con este avance se completan
     * todos los checkpoints (debe ir al finish).
     */
    public boolean advanceCheckpoint() {
        nextCheckpointIndex++;
        return nextCheckpointIndex >= checkpoints.size();
    }

    /**
     * Marca el equipo como finalizado.
     */
    public void markFinished(int rank) {
        this.finished     = true;
        this.finishTimeMs = System.currentTimeMillis();
        this.finishRank   = rank;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public EventTeam              getTeam()                  { return team; }
    public List<ParkourCheckpoint> getCheckpoints()          { return checkpoints; }
    public int                    getNextCheckpointIndex()   { return nextCheckpointIndex; }
    public int                    getTotalCheckpoints()      { return checkpoints.size(); }
    public boolean                isFinished()               { return finished; }
    public long                   getFinishTimeMs()          { return finishTimeMs; }
    public int                    getFinishRank()            { return finishRank; }
    public int                    getPlayersInCurrentCheckpoint() { return playersInCurrentCheckpoint; }
    public void                   setPlayersInCurrentCheckpoint(int n) { this.playersInCurrentCheckpoint = n; }
    public int                    getInternalScore()         { return internalScore; }
    public void                   addInternalScore(int pts)  { this.internalScore += pts; }
    public void                   setInternalScore(int pts)  { this.internalScore = pts; }

    /**
     * Progreso como fracción de checkpoints completados (0.0 a 1.0).
     */
    public double getProgressFraction() {
        if (checkpoints.isEmpty()) return 0.0;
        if (finished) return 1.0;
        return (double) nextCheckpointIndex / checkpoints.size();
    }

    /**
     * Checkpoints completados (los que ya pasó).
     */
    public int getCompletedCheckpoints() {
        return nextCheckpointIndex;
    }
}