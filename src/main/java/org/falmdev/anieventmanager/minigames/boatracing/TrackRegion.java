package org.falmdev.anieventmanager.minigames.boatracing;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Región rectangular de la pista definida por dos puntos (A y B).
 * Dentro de esta región se configuran los checkpoints y la meta.
 *
 * También maneja la detección de cruce de la línea de meta,
 * que se define como un segmento entre dos puntos dentro de la región.
 */
public class TrackRegion {

    // Radio de detección de la meta (bloques)
    // MODIFICAR AQUI si la meta es difícil de cruzar
    private static final double FINISH_DETECTION_RADIUS = 3.5;

    private final Location pointA;
    private final Location pointB;
    private final String   worldName;

    // Línea de meta (dos puntos dentro de la región)
    private Location finishA;
    private Location finishB;

    public TrackRegion(Location pointA, Location pointB) {
        this.pointA    = pointA;
        this.pointB    = pointB;
        this.worldName = pointA.getWorld().getName();
    }

    // ── Región ────────────────────────────────────────────────────────────────

    public boolean contains(Location loc) {
        if (!loc.getWorld().getName().equals(worldName)) return false;
        double minX = Math.min(pointA.getX(), pointB.getX());
        double maxX = Math.max(pointA.getX(), pointB.getX());
        double minY = Math.min(pointA.getY(), pointB.getY());
        double maxY = Math.max(pointA.getY(), pointB.getY());
        double minZ = Math.min(pointA.getZ(), pointB.getZ());
        double maxZ = Math.max(pointA.getZ(), pointB.getZ());
        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    // ── Detección de cruce de meta ─────────────────────────────────────────────

    /**
     * Devuelve true si la ubicación está dentro del radio de la línea de meta.
     * La meta es el segmento entre finishA y finishB.
     */
    public boolean isCrossingFinish(Location loc) {
        if (finishA == null || finishB == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;

        double dist = distanceToSegment(
                loc.getX(), loc.getZ(),
                finishA.getX(), finishA.getZ(),
                finishB.getX(), finishB.getZ()
        );

        // Verificar también la altura
        double midY = (finishA.getY() + finishB.getY()) / 2.0;
        double dy   = Math.abs(loc.getY() - midY);

        return dist <= FINISH_DETECTION_RADIUS && dy <= 4.0;
    }

    private double distanceToSegment(double px, double pz,
                                     double ax, double az,
                                     double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double lenSq = dx * dx + dz * dz;
        if (lenSq == 0) return Math.sqrt((px-ax)*(px-ax) + (pz-az)*(pz-az));
        double t = Math.max(0, Math.min(1, ((px-ax)*dx + (pz-az)*dz) / lenSq));
        double nx = ax + t * dx, nz = az + t * dz;
        return Math.sqrt((px-nx)*(px-nx) + (pz-nz)*(pz-nz));
    }

    // ── Centro de la región ────────────────────────────────────────────────────

    public Location getCenter() {
        World w = pointA.getWorld();
        return new Location(w,
                (pointA.getX() + pointB.getX()) / 2.0,
                (pointA.getY() + pointB.getY()) / 2.0,
                (pointA.getZ() + pointB.getZ()) / 2.0);
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Location getPointA() { return pointA; }
    public Location getPointB() { return pointB; }
    public String   getWorldName() { return worldName; }

    public Location getFinishA() { return finishA; }
    public void     setFinishA(Location l) { this.finishA = l; }

    public Location getFinishB() { return finishB; }
    public void     setFinishB(Location l) { this.finishB = l; }

    public boolean hasFinishLine() { return finishA != null && finishB != null; }

    public Location getFinishCenter() {
        if (!hasFinishLine()) return null;
        return new Location(finishA.getWorld(),
                (finishA.getX() + finishB.getX()) / 2.0,
                (finishA.getY() + finishB.getY()) / 2.0,
                (finishA.getZ() + finishB.getZ()) / 2.0);
    }
}