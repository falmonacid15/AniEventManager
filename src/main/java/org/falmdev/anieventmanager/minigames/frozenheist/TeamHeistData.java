package org.falmdev.anieventmanager.minigames.frozenheist;

import org.bukkit.Location;
import org.falmdev.anieventmanager.model.EventTeam;

public class TeamHeistData {

    public static final int POINTS_CAPTURE = 5;
    public static final int POINTS_RECOVER = 2;

    private final EventTeam team;
    private int localPoints = 0;

    // Dos spawns de respawn dentro de la base (uno por jugador)
    private Location baseSpawn1;
    private Location baseSpawn2;

    // Índice rotativo: alterna qué spawn usar para el próximo respawn.
    // 0 = próximo usa spawn1, 1 = próximo usa spawn2.
    // Se avanza cada vez que se teleporta a un jugador en teleportToBase.
    private int spawnIndex = 0;

    private Location captureZone;
    private Location flagStand;
    private Location baseCorner1;
    private Location baseCorner2;

    public TeamHeistData(EventTeam team) {
        this.team = team;
    }

    // ── Puntos ────────────────────────────────────────────────────────────────

    public void addPoints(int points) { localPoints += points; }
    public int  getPoints()           { return localPoints; }

    // ── Spawns de base ────────────────────────────────────────────────────────

    public Location getBaseSpawn1() { return baseSpawn1; }
    public void setBaseSpawn1(Location l) { this.baseSpawn1 = l; }

    public Location getBaseSpawn2() { return baseSpawn2; }
    public void setBaseSpawn2(Location l) { this.baseSpawn2 = l; }

    /**
     * Devuelve el spawn para el índice dado (1 o 2).
     * Si el spawn pedido es null, devuelve el otro como fallback.
     */
    public Location getBaseSpawnFor(int index) {
        if (index == 2) {
            return baseSpawn2 != null ? baseSpawn2 : baseSpawn1;
        }
        return baseSpawn1 != null ? baseSpawn1 : baseSpawn2;
    }

    /**
     * Devuelve el siguiente spawn disponible para un respawn,
     * alternando entre spawn1 y spawn2.
     * Si solo hay uno configurado, siempre devuelve ese.
     */
    public Location getNextRespawnSpawn() {
        if (baseSpawn1 == null) return baseSpawn2;
        if (baseSpawn2 == null) return baseSpawn1;
        Location result = (spawnIndex % 2 == 0) ? baseSpawn1 : baseSpawn2;
        spawnIndex++;
        return result;
    }

    /**
     * Devuelve cualquier spawn disponible (para eventos como onRespawn
     * donde no queremos avanzar el índice).
     */
    public Location getAnyBaseSpawn() {
        return baseSpawn1 != null ? baseSpawn1 : baseSpawn2;
    }

    // ── Resto de getters/setters ──────────────────────────────────────────────

    public EventTeam getTeam()       { return team; }

    public Location getCaptureZone() { return captureZone; }
    public void setCaptureZone(Location l) { this.captureZone = l; }

    public Location getFlagStand()   { return flagStand; }
    public void setFlagStand(Location l) { this.flagStand = l; }

    public Location getBaseCorner1() { return baseCorner1; }
    public void setBaseCorner1(Location l) { this.baseCorner1 = l; }

    public Location getBaseCorner2() { return baseCorner2; }
    public void setBaseCorner2(Location l) { this.baseCorner2 = l; }

    // ── Verificaciones de zona ────────────────────────────────────────────────

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

    public boolean isInsideCaptureZone(Location loc) {
        if (captureZone == null) return false;
        if (!loc.getWorld().getName().equals(captureZone.getWorld().getName())) return false;
        return loc.distanceSquared(captureZone) <= 9;
    }

    public boolean isNearFlagStand(Location loc) {
        if (flagStand == null) return false;
        if (!loc.getWorld().getName().equals(flagStand.getWorld().getName())) return false;
        return loc.distanceSquared(flagStand) <= 4;
    }

    public boolean hasFullConfig() {
        return baseSpawn1 != null && baseSpawn2 != null
                && captureZone != null && flagStand != null
                && baseCorner1 != null && baseCorner2 != null;
    }
}