package org.falmdev.anieventmanager.cinematics.model;

import org.jetbrains.annotations.Nullable;

/**
 * Un marker de texto en un tick específico de la cinematica.
 *
 * Reemplaza a CinematicWaypoint — ya no tiene posición propia
 * porque la posición la determina el frame grabado en ese tick.
 *
 * El marker solo almacena:
 *  - En qué tick se activa (0..totalFrames-1)
 *  - Qué texto mostrar (título, subtítulo, actionbar)
 *  - Tiempos del title (fadeIn, stay, fadeOut en ticks)
 */
public class CinematicMarker {

    private int tick;

    @Nullable private String titleMain;
    @Nullable private String titleSub;
    @Nullable private String actionbar;

    private int titleFadeIn  = 10;
    private int titleStay    = 60;
    private int titleFadeOut = 10;

    public CinematicMarker(int tick) {
        this.tick = tick;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public int  getTick()               { return tick; }
    public void setTick(int tick)       { this.tick = tick; }

    public @Nullable String getTitleMain()           { return titleMain; }
    public void             setTitleMain(@Nullable String t) { this.titleMain = t; }

    public @Nullable String getTitleSub()            { return titleSub; }
    public void             setTitleSub(@Nullable String t)  { this.titleSub = t; }

    public @Nullable String getActionbar()           { return actionbar; }
    public void             setActionbar(@Nullable String t) { this.actionbar = t; }

    public int  getTitleFadeIn()             { return titleFadeIn; }
    public void setTitleFadeIn(int ticks)    { this.titleFadeIn = ticks; }

    public int  getTitleStay()               { return titleStay; }
    public void setTitleStay(int ticks)      { this.titleStay = ticks; }

    public int  getTitleFadeOut()            { return titleFadeOut; }
    public void setTitleFadeOut(int ticks)   { this.titleFadeOut = ticks; }

    public boolean hasText() {
        return (titleMain != null && !titleMain.isBlank())
                || (titleSub != null && !titleSub.isBlank())
                || (actionbar != null && !actionbar.isBlank());
    }
}