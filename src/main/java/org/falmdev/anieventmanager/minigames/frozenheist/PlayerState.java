package org.falmdev.anieventmanager.minigames.frozenheist;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Estado de un jugador durante la partida de Frozen Heist.
 *
 * Hits:      acumulados desde el último freeze (reset al ser congelado)
 * Frozen:    estado de congelación (no puede moverse, atacar ni interactuar)
 * Carrying:  id del equipo cuya bandera lleva (null = no lleva ninguna)
 * Rescuing:  UUID del compañero que está intentando rescatar
 */
public class PlayerState {

    // ── Configuración de combat ────────────────────────────────────────────────
    // MODIFICAR AQUI para cambiar la mecánica de combate
    public static final int    HITS_TO_FREEZE       = 3;     // hits para congelar
    public static final long   FREEZE_DURATION_MS   = 8000;  // ms congelado
    public static final long   SNOWBALL_COOLDOWN_MS = 400;   // ms entre disparos
    public static final long   RESCUE_TIME_MS       = 1000;  // ms de click para rescatar
    // ─────────────────────────────────────────────────────────────────────────

    private final UUID playerUUID;

    // Hits recibidos en la vida actual (reset al congelarse)
    private int hits = 0;

    // Estado de congelación
    private boolean frozen = false;
    private long    frozenUntilMs = 0;
    private BukkitTask unfreezeTask;

    // Bandera que lleva (teamId del equipo dueño de la bandera, o null)
    private String carryingFlagOf = null;

    // Cooldown de bola de nieve
    private long lastSnowballMs = 0;

    // Rescate en progreso
    private UUID   rescuingTarget    = null; // a quién está rescatando
    private long   rescueStartMs     = 0;
    private BukkitTask rescueTask;

    // Quién lo está rescatando a él
    private UUID   beingRescuedBy    = null;

    public PlayerState(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    // ── Hits ──────────────────────────────────────────────────────────────────

    /**
     * Registra un hit. Devuelve true si se alcanzó el límite para congelar.
     */
    public boolean addHit() {
        if (frozen) return false;
        hits++;
        return hits >= HITS_TO_FREEZE;
    }

    public void resetHits() { hits = 0; }
    public int  getHits()   { return hits; }

    // ── Congelación ───────────────────────────────────────────────────────────

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

    /** Segundos restantes de congelación (para mostrar en bossbar/actionbar) */
    public int getFrozenSecondsLeft() {
        if (!frozen) return 0;
        return (int) Math.ceil((frozenUntilMs - System.currentTimeMillis()) / 1000.0);
    }

    // ── Snowball cooldown ─────────────────────────────────────────────────────

    public boolean canShoot() {
        return System.currentTimeMillis() - lastSnowballMs >= SNOWBALL_COOLDOWN_MS;
    }

    public void recordShot() { lastSnowballMs = System.currentTimeMillis(); }

    // ── Bandera ───────────────────────────────────────────────────────────────

    public boolean isCarryingFlag()   { return carryingFlagOf != null; }
    public String  getCarryingFlagOf(){ return carryingFlagOf; }
    public void    setCarryingFlag(String teamId) { this.carryingFlagOf = teamId; }
    public void    clearFlag()        { this.carryingFlagOf = null; }

    // ── Rescate ───────────────────────────────────────────────────────────────

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

    public void    setBeingRescuedBy(UUID uuid) { this.beingRescuedBy = uuid; }
    public UUID    getBeingRescuedBy()           { return beingRescuedBy; }
    public boolean isBeingRescued()              { return beingRescuedBy != null; }

    // ── Getter ────────────────────────────────────────────────────────────────

    public UUID getPlayerUUID() { return playerUUID; }
}