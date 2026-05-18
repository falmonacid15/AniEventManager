package org.falmdev.anieventmanager.minigames.frozenheist;

import org.bukkit.Location;
import org.falmdev.anieventmanager.model.EventTeam;

/**
 * Datos de un equipo durante la partida de Frozen Heist.
 * Los puntos aquí son LOCALES al minijuego (no van al ScoreManager global).
 */
public class TeamHeistData {

    // ── Puntos del minijuego (independientes del sistema global) ──────────────
    // MODIFICAR AQUI para cambiar los puntos por acción
    public static final int POINTS_CAPTURE  = 5; // capturar bandera enemiga
    public static final int POINTS_RECOVER  = 2; // recuperar bandera propia
    // ─────────────────────────────────────────────────────────────────────────

    private final EventTeam team;
    private int localPoints = 0;

    // Configuración de base (cargada desde config)
    private Location baseSpawn;       // spawn de respawn dentro de la base
    private Location captureZone;     // zona donde se entrega la bandera enemiga
    private Location flagStand;       // posición original de su propia bandera
    private Location baseCorner1;     // esquina 1 de la zona segura
    private Location baseCorner2;     // esquina 2 de la zona segura

    public TeamHeistData(EventTeam team) {
        this.team = team;
    }

    // ── Puntos ────────────────────────────────────────────────────────────────

    public void addPoints(int points) { localPoints += points; }
    public int  getPoints()           { return localPoints; }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public EventTeam getTeam()       { return team; }

    public Location getBaseSpawn()   { return baseSpawn; }
    public void setBaseSpawn(Location l) { this.baseSpawn = l; }

    public Location getCaptureZone() { return captureZone; }
    public void setCaptureZone(Location l) { this.captureZone = l; }

    public Location getFlagStand()   { return flagStand; }
    public void setFlagStand(Location l) { this.flagStand = l; }

    public Location getBaseCorner1() { return baseCorner1; }
    public void setBaseCorner1(Location l) { this.baseCorner1 = l; }

    public Location getBaseCorner2() { return baseCorner2; }
    public void setBaseCorner2(Location l) { this.baseCorner2 = l; }

    /**
     * Verifica si una ubicación está dentro de la zona segura de esta base.
     * Usa un cubo definido por corner1 y corner2.
     */
    public boolean isInsideBase(Location loc) {
        if (baseCorner1 == null || baseCorner2 == null) return false;
        if (!loc.getWorld().getName().equals(baseCorner1.getWorld().getName())) return false;

        double minX = Math.min(baseCorner1.getX(), baseCorner2.getX());
        double maxX = Math.max(baseCorner1.getX(), baseCorner2.getX());
        double minY = Math.min(baseCorner1.getY(), baseCorner2.getY());
        double maxY = Math.max(baseCorner1.getY(), baseCorner2.getY());
        double minZ = Math.min(baseCorner1.getZ(), baseCorner2.getZ());
        double maxZ = Math.max(baseCorner1.getZ(), baseCorner2.getZ());

        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /**
     * Verifica si una ubicación está dentro del radio de la zona de captura (3 bloques).
     */
    public boolean isInsideCaptureZone(Location loc) {
        if (captureZone == null) return false;
        if (!loc.getWorld().getName().equals(captureZone.getWorld().getName())) return false;
        return loc.distanceSquared(captureZone) <= 9; // radio 3
    }

    /**
     * Verifica si una ubicación está cerca del stand de la bandera (radio 2).
     */
    public boolean isNearFlagStand(Location loc) {
        if (flagStand == null) return false;
        if (!loc.getWorld().getName().equals(flagStand.getWorld().getName())) return false;
        return loc.distanceSquared(flagStand) <= 4; // radio 2
    }

    public boolean hasFullConfig() {
        return baseSpawn != null && captureZone != null
                && flagStand != null && baseCorner1 != null && baseCorner2 != null;
    }
}