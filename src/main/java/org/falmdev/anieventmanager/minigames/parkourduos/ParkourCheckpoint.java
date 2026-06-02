package org.falmdev.anieventmanager.minigames.parkourduos;

import org.bukkit.Location;

/**
 * Representa un checkpoint del parkour.
 * Un checkpoint se valida cuando LOS DOS jugadores del equipo están dentro del radio.
 */
public class ParkourCheckpoint {

    private final int index;
    private final Location center;
    private final double radius;

    public ParkourCheckpoint(int index, Location center, double radius) {
        this.index  = index;
        this.center = center;
        this.radius = radius;
    }

    public boolean isInside(Location loc) {
        if (loc.getWorld() == null || center.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(center.getWorld().getName())) return false;
        return loc.distanceSquared(center) <= radius * radius;
    }

    public int      getIndex()  { return index; }
    public Location getCenter() { return center; }
    public double   getRadius() { return radius; }
}