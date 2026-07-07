package org.falmdev.anieventmanager.minigames.tntrun;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class TNTRunArena {

    public enum Shape { SQUARE, CIRCLE }

    public record ArenaConfig(
            int arenaSize,
            Shape shape,
            int layerCount,
            int layerGap,
            int domeHeight
    ) {
        public ArenaConfig {
            arenaSize  = Math.max(10,  arenaSize);
            layerCount = Math.max(1,   layerCount);
            layerGap   = Math.max(1,   layerGap);
            domeHeight = Math.max(5,   domeHeight);
        }

        public static ArenaConfig defaults() {
            return new ArenaConfig(60, Shape.SQUARE, 3, 3, 30);
        }
    }

    private final Location    center;
    private final World       world;
    private final int         baseY;
    private final ArenaConfig cfg;

    private final List<Location> sandBlocks = new ArrayList<>();

    private final List<Location> tntBlocks  = new ArrayList<>();

    private final int playerOffset;

    private final int roofOffset;

    public TNTRunArena(Location center, ArenaConfig cfg) {
        this.center = center;
        this.world  = center.getWorld();
        this.baseY  = center.getBlockY();
        this.cfg    = cfg;

        int lastLayerBase  = 10 + (cfg.layerCount() - 1) * (2 + cfg.layerGap());
        this.playerOffset  = lastLayerBase + 2;
        this.roofOffset    = playerOffset + cfg.domeHeight();
    }

    public void generate() {
        sandBlocks.clear();
        tntBlocks.clear();

        int half = cfg.arenaSize() / 2;
        int cx   = center.getBlockX();
        int cz   = center.getBlockZ();

        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                if (!isInside(x, z, half)) continue;

                int bx = cx + x;
                int bz = cz + z;

                placeColumn(bx, bz);
            }
        }

        generateDome(half);
    }

    public void restore() {
        for (Location loc : tntBlocks) {
            if (loc.getBlock().getType() != Material.TNT)
                loc.getBlock().setType(Material.TNT, false);
        }
        for (Location loc : sandBlocks) {
            if (loc.getBlock().getType() != Material.SAND)
                loc.getBlock().setType(Material.SAND, false);
        }
    }

    public void clear() {
        int half = cfg.arenaSize() / 2;
        int cx   = center.getBlockX();
        int cz   = center.getBlockZ();

        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                if (!isInside(x, z, half)) continue;
                int bx = cx + x;
                int bz = cz + z;
                for (int y = 0; y <= roofOffset; y++) {
                    setBlock(bx, baseY + y, bz, Material.AIR);
                }
            }
        }
        clearDome(half);

        sandBlocks.clear();
        tntBlocks.clear();
    }


    public Location   getCenter()       { return center; }
    public World      getWorld()        { return world; }
    public ArenaConfig getArenaConfig() { return cfg; }

    public double getPlayerSpawnY()     { return baseY + playerOffset; }

    public List<Location> getSandBlocks() { return sandBlocks; }

    private void placeColumn(int bx, int bz) {
        setBlock(bx, baseY,     bz, Material.BEDROCK);
        setBlock(bx, baseY + 1, bz, Material.WATER);

        for (int y = 2; y < 10; y++) {
            setBlock(bx, baseY + y, bz, Material.AIR);
        }

        for (int i = 0; i < cfg.layerCount(); i++) {
            int base = 10 + i * (2 + cfg.layerGap());

            tntBlocks.add(setBlock(bx, baseY + base,      bz, Material.TNT));
            sandBlocks.add(setBlock(bx, baseY + base + 1, bz, Material.SAND));

            for (int g = 2; g < 2 + cfg.layerGap(); g++) {
                setBlock(bx, baseY + base + g, bz, Material.AIR);
            }
        }

        for (int y = playerOffset; y < roofOffset; y++) {
            setBlock(bx, baseY + y, bz, Material.AIR);
        }
    }

    private void generateDome(int half) {
        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        if (cfg.shape() == Shape.SQUARE) {
            for (int i = -half - 1; i <= half; i++) {
                for (int h = 0; h <= roofOffset; h++) {
                    setBlock(cx + i,        baseY + h, cz - half - 1, Material.GLASS);
                    setBlock(cx + i,        baseY + h, cz + half,     Material.GLASS);
                    setBlock(cx - half - 1, baseY + h, cz + i,        Material.GLASS);
                    setBlock(cx + half,     baseY + h, cz + i,        Material.GLASS);
                }
            }
            for (int x = -half; x < half; x++) {
                for (int z = -half; z < half; z++) {
                    setBlock(cx + x, baseY + roofOffset, cz + z, Material.GLASS);
                }
            }
        } else {
            double r     = half;
            double rOuter = half + 1.5;

            for (int x = -half - 2; x <= half + 2; x++) {
                for (int z = -half - 2; z <= half + 2; z++) {
                    double dx = x + 0.5;
                    double dz = z + 0.5;
                    double dist2 = dx * dx + dz * dz;

                    if (dist2 > r * r && dist2 <= rOuter * rOuter) {
                        for (int h = 0; h <= roofOffset; h++) {
                            setBlock(cx + x, baseY + h, cz + z, Material.GLASS);
                        }
                    }
                }
            }
            for (int x = -half; x < half; x++) {
                for (int z = -half; z < half; z++) {
                    double dx = x + 0.5;
                    double dz = z + 0.5;
                    if (dx * dx + dz * dz <= r * r) {
                        setBlock(cx + x, baseY + roofOffset, cz + z, Material.GLASS);
                    }
                }
            }
        }
    }

    private void clearDome(int half) {
        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        if (cfg.shape() == Shape.SQUARE) {
            for (int i = -half - 1; i <= half; i++) {
                for (int h = 0; h <= roofOffset; h++) {
                    setBlock(cx + i,        baseY + h, cz - half - 1, Material.AIR);
                    setBlock(cx + i,        baseY + h, cz + half,     Material.AIR);
                    setBlock(cx - half - 1, baseY + h, cz + i,        Material.AIR);
                    setBlock(cx + half,     baseY + h, cz + i,        Material.AIR);
                }
            }
            for (int x = -half; x < half; x++) {
                for (int z = -half; z < half; z++) {
                    setBlock(cx + x, baseY + roofOffset, cz + z, Material.AIR);
                }
            }
        } else {
            double r      = half;
            double rOuter = half + 1.5;
            for (int x = -half - 2; x <= half + 2; x++) {
                for (int z = -half - 2; z <= half + 2; z++) {
                    double dx = x + 0.5;
                    double dz = z + 0.5;
                    double dist2 = dx * dx + dz * dz;
                    if (dist2 > r * r && dist2 <= rOuter * rOuter) {
                        for (int h = 0; h <= roofOffset; h++) {
                            setBlock(cx + x, baseY + h, cz + z, Material.AIR);
                        }
                    }
                }
            }
            for (int x = -half; x < half; x++) {
                for (int z = -half; z < half; z++) {
                    double dx = x + 0.5;
                    double dz = z + 0.5;
                    if (dx * dx + dz * dz <= r * r) {
                        setBlock(cx + x, baseY + roofOffset, cz + z, Material.AIR);
                    }
                }
            }
        }
    }

    private boolean isInside(int x, int z, int half) {
        if (cfg.shape() == Shape.SQUARE) return true;

        double dx = x + 0.5;
        double dz = z + 0.5;
        return (dx * dx + dz * dz) <= (double) half * half;
    }

    private Location setBlock(int x, int y, int z, Material material) {
        Location loc = new Location(world, x, y, z);
        loc.getBlock().setType(material, false);
        return loc;
    }
}