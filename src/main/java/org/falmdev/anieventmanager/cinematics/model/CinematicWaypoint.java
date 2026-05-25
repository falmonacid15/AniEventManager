package org.falmdev.anieventmanager.cinematics.model;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * Un punto de la ruta de una cinematica.
 *
 * {@code tickOffset} define en qué tick absoluto (desde el inicio de la
 * reproducción) la cámara debe estar exactamente en esta posición.
 * El spline interpola entre waypoints consecutivos usando sus tickOffsets
 * para saber cuánto tiempo dedicar a cada segmento.
 *
 * Los textos soportan formato "&" de Minecraft.
 */
public class CinematicWaypoint {

    private Location location;

    /** Tick absoluto desde el inicio en que la cámara llega a este punto. */
    private int tickOffset;

    // ── Texto opcional en este waypoint ───────────────────────────────────────
    @Nullable private String titleMain;
    @Nullable private String titleSub;
    @Nullable private String actionbar;

    private int titleFadeIn  = 10;  // ticks
    private int titleStay    = 60;
    private int titleFadeOut = 10;

    public CinematicWaypoint(Location location, int tickOffset) {
        this.location   = location.clone();
        this.tickOffset = tickOffset;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public Location getLocation()                  { return location.clone(); }
    public void     setLocation(Location location) { this.location = location.clone(); }

    public int  getTickOffset()               { return tickOffset; }
    public void setTickOffset(int tickOffset) { this.tickOffset = tickOffset; }

    public @Nullable String getTitleMain()              { return titleMain; }
    public void             setTitleMain(@Nullable String t) { this.titleMain = t; }

    public @Nullable String getTitleSub()              { return titleSub; }
    public void             setTitleSub(@Nullable String t)  { this.titleSub = t; }

    public @Nullable String getActionbar()              { return actionbar; }
    public void             setActionbar(@Nullable String t) { this.actionbar = t; }

    public int  getTitleFadeIn()             { return titleFadeIn; }
    public void setTitleFadeIn(int ticks)    { this.titleFadeIn = ticks; }

    public int  getTitleStay()               { return titleStay; }
    public void setTitleStay(int ticks)      { this.titleStay = ticks; }

    public int  getTitleFadeOut()            { return titleFadeOut; }
    public void setTitleFadeOut(int ticks)   { this.titleFadeOut = ticks; }

    /** ¿Tiene algún texto configurado en este waypoint? */
    public boolean hasText() {
        return (titleMain != null && !titleMain.isBlank())
                || (titleSub != null && !titleSub.isBlank())
                || (actionbar != null && !actionbar.isBlank());
    }

    /** Índice de este waypoint dentro de su cinematica (seteado externamente para display). */
    private int index = -1;
    public int  getIndex()        { return index; }
    public void setIndex(int i)   { this.index = i; }
}