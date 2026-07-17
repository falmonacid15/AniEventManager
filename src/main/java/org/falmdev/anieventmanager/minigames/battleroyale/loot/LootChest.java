package org.falmdev.anieventmanager.minigames.battleroyale.loot;

import org.bukkit.Location;
import org.bukkit.World;

public class LootChest {

    private final String worldName;
    private final int    x, y, z;
    private final String tierId;

    public LootChest(String worldName, int x, int y, int z, String tierId) {
        this.worldName = worldName;
        this.x = x; this.y = y; this.z = z;
        this.tierId = tierId;
    }

    public String getWorldName() { return worldName; }
    public int    getX()         { return x; }
    public int    getY()         { return y; }
    public int    getZ()         { return z; }
    public String getTierId()    { return tierId; }

    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }

    public Location centerLocation(World world) {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }
}