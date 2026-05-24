package org.falmdev.anieventmanager.minigames.bingo;

/**
 * Define una pared rectangular de barriers entre dos esquinas.
 */
public class BingoWall {

    private final String id;
    private String world;
    private int x1, y1, z1;
    private int x2, y2, z2;

    public BingoWall(String id, String world,
                     int x1, int y1, int z1,
                     int x2, int y2, int z2) {
        this.id    = id;
        this.world = world;
        this.x1 = x1; this.y1 = y1; this.z1 = z1;
        this.x2 = x2; this.y2 = y2; this.z2 = z2;
    }

    public String getId()     { return id; }
    public String getWorld()  { return world; }
    public int getX1() { return x1; } public int getY1() { return y1; } public int getZ1() { return z1; }
    public int getX2() { return x2; } public int getY2() { return y2; } public int getZ2() { return z2; }
}