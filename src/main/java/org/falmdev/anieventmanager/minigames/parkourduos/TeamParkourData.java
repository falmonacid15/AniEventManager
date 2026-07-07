package org.falmdev.anieventmanager.minigames.parkourduos;

import org.falmdev.anieventmanager.model.EventTeam;

import java.util.List;

public class TeamParkourData {

    private final EventTeam team;
    private final List<ParkourCheckpoint> checkpoints;

    private int nextCheckpointIndex = 0;

    private boolean finished = false;

    private long finishTimeMs = 0;

    private int finishRank = 0;

    private int playersInCurrentCheckpoint = 0;

    private int internalScore = 0;

    public TeamParkourData(EventTeam team, List<ParkourCheckpoint> checkpoints) {
        this.team        = team;
        this.checkpoints = checkpoints;
    }

    public ParkourCheckpoint getActiveCheckpoint() {
        if (finished || nextCheckpointIndex >= checkpoints.size()) return null;
        return checkpoints.get(nextCheckpointIndex);
    }

    public boolean advanceCheckpoint() {
        nextCheckpointIndex++;
        return nextCheckpointIndex >= checkpoints.size();
    }

    public void markFinished(int rank) {
        this.finished     = true;
        this.finishTimeMs = System.currentTimeMillis();
        this.finishRank   = rank;
    }

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

    public double getProgressFraction() {
        if (checkpoints.isEmpty()) return 0.0;
        if (finished) return 1.0;
        return (double) nextCheckpointIndex / checkpoints.size();
    }

    public int getCompletedCheckpoints() {
        return nextCheckpointIndex;
    }
}