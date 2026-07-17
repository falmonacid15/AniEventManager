package org.falmdev.anieventmanager.minigames.frozenheist;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class PlayerState {

    public static final int    HITS_TO_FREEZE       = 3;
    public static final long   FREEZE_DURATION_MS   = 8000;
    public static final long   SNOWBALL_COOLDOWN_MS = 400;
    public static final long   RESCUE_TIME_MS       = 2000;

    private final UUID playerUUID;

    private int hits = 0;

    private boolean frozen = false;
    private long    frozenUntilMs = 0;
    private BukkitTask unfreezeTask;

    private String carryingFlagOf = null;

    private long lastSnowballMs = 0;

    private UUID   rescuingTarget    = null;
    private long   rescueStartMs     = 0;
    private BukkitTask rescueTask;

    private UUID   beingRescuedBy    = null;

    public PlayerState(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }


    public boolean addHit() {
        if (frozen) return false;
        hits++;
        return hits >= HITS_TO_FREEZE;
    }

    public void resetHits() { hits = 0; }
    public int  getHits()   { return hits; }

    public void freeze(BukkitTask task) {
        this.frozen       = true;
        this.frozenUntilMs = System.currentTimeMillis() + FREEZE_DURATION_MS;
        this.unfreezeTask  = task;
        this.hits          = 0;
    }

    public void unfreeze() {
        this.frozen        = false;
        this.frozenUntilMs = 0;
        if (unfreezeTask != null && !unfreezeTask.isCancelled()) {
            unfreezeTask.cancel();
        }
        this.unfreezeTask  = null;
        this.beingRescuedBy = null;
    }

    public boolean isFrozen()      { return frozen; }
    public long getFrozenUntilMs() { return frozenUntilMs; }

    public int getFrozenSecondsLeft() {
        if (!frozen) return 0;
        return (int) Math.ceil((frozenUntilMs - System.currentTimeMillis()) / 1000.0);
    }

    public boolean canShoot() {
        return System.currentTimeMillis() - lastSnowballMs >= SNOWBALL_COOLDOWN_MS;
    }

    public void recordShot() { lastSnowballMs = System.currentTimeMillis(); }

    public boolean isCarryingFlag()   { return carryingFlagOf != null; }
    public String  getCarryingFlagOf(){ return carryingFlagOf; }
    public void    setCarryingFlag(String teamId) { this.carryingFlagOf = teamId; }
    public void    clearFlag()        { this.carryingFlagOf = null; }

    public void startRescuing(UUID target, BukkitTask task) {
        this.rescuingTarget = target;
        this.rescueStartMs  = System.currentTimeMillis();
        this.rescueTask     = task;
    }

    public void cancelRescue() {
        if (rescueTask != null && !rescueTask.isCancelled()) rescueTask.cancel();
        this.rescuingTarget = null;
        this.rescueStartMs  = 0;
        this.rescueTask     = null;
    }

    public boolean isRescuing()       { return rescuingTarget != null; }
    public UUID    getRescuingTarget(){ return rescuingTarget; }
    public long    getRescueStartMs() { return rescueStartMs; }

    public void    setBeingRescuedBy(UUID uuid) { this.beingRescuedBy = uuid; }
    public UUID    getBeingRescuedBy()           { return beingRescuedBy; }
    public boolean isBeingRescued()              { return beingRescuedBy != null; }

    public UUID getPlayerUUID() { return playerUUID; }
}