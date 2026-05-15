package org.falmdev.anieventmanager.minigames.tntrun;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Genera la arena de TNT Run.
 *
 * Estructura vertical desde el Y base:
 *
 *   Y+0  → BEDROCK  (suelo)
 *   Y+1  → WATER    (zona de eliminación)
 *   Y+2  → AIR
 *
 *   Capa 1:
 *   Y+3  → TNT
 *   Y+4  → SAND
 *   Y+5  → AIR
 *
 *   Capa 2:
 *   Y+6  → TNT
 *   Y+7  → SAND
 *   Y+8  → AIR
 *
 *   Capa 3 (inicio):
 *   Y+9  → TNT
 *   Y+10 → SAND
 *   Y+11 → AIR  (jugadores se paran aquí)
 *
 * Cúpula:
 *   Paredes de GLASS desde Y+0 hasta Y+12 rodeando la arena.
 *   Techo de GLASS en Y+12 cerrando completamente el espacio.
 *   Los jugadores no pueden caerse por los bordes ni saltar afuera.
 *
 * ─────────────────────────────────────────────────────────────────
 * TAMAÑO DE ARENA: modificar ARENA_SIZE para cambiar las dimensiones.
 * Actualmente: 60x60 bloques.
 * ─────────────────────────────────────────────────────────────────
 */
public class TNTRunArena {

    // ── Configuración de tamaño ───────────────────────────────────────────────
    // MODIFICAR AQUI para cambiar el tamaño de la arena
    private static final int ARENA_SIZE = 60;
    // ─────────────────────────────────────────────────────────────────────────

    // Offsets verticales desde baseY
    public static final int FLOOR_OFFSET  = 0;
    public static final int WATER_OFFSET  = 1;
    public static final int LAYER_1_TNT   = 5;
    public static final int LAYER_1_SAND  = 6;
    public static final int LAYER_2_TNT   = 11;
    public static final int LAYER_2_SAND  = 12;
    public static final int LAYER_3_TNT   = 17;
    public static final int LAYER_3_SAND  = 18;
    public static final int PLAYER_OFFSET = 19;

    // Alto de las paredes de cristal (desde FLOOR_OFFSET)
    // MODIFICAR AQUI si quieres paredes más altas o más bajas
    private static final int DOME_HEIGHT = 30;

    private final Location center;
    private final World world;
    private final int baseY;

    private final List<Location> sandBlocks = new ArrayList<>();
    private final List<Location> tntBlocks  = new ArrayList<>();

    public TNTRunArena(Location center) {
        this.center = center;
        this.world  = center.getWorld();
        this.baseY  = center.getBlockY();
    }

    // ── Generación ────────────────────────────────────────────────────────────

    public void generate() {
        sandBlocks.clear();
        tntBlocks.clear();

        int half = ARENA_SIZE / 2;

        // ── Interior ──────────────────────────────────────────────────────────
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                int bx = center.getBlockX() + x;
                int bz = center.getBlockZ() + z;

                // Suelo y agua
                setBlock(bx, baseY + FLOOR_OFFSET,      bz, Material.BEDROCK);
                setBlock(bx, baseY + WATER_OFFSET,      bz, Material.WATER);
                setBlock(bx, baseY + WATER_OFFSET + 1,  bz, Material.AIR);

                // Capa 1
                tntBlocks.add(setBlock(bx, baseY + LAYER_1_TNT,       bz, Material.TNT));
                sandBlocks.add(setBlock(bx, baseY + LAYER_1_SAND,     bz, Material.SAND));
                setBlock(bx, baseY + LAYER_1_SAND + 1,  bz, Material.AIR);

                // Capa 2
                tntBlocks.add(setBlock(bx, baseY + LAYER_2_TNT,       bz, Material.TNT));
                sandBlocks.add(setBlock(bx, baseY + LAYER_2_SAND,     bz, Material.SAND));
                setBlock(bx, baseY + LAYER_2_SAND + 1,  bz, Material.AIR);

                // Capa 3
                tntBlocks.add(setBlock(bx, baseY + LAYER_3_TNT,       bz, Material.TNT));
                sandBlocks.add(setBlock(bx, baseY + LAYER_3_SAND,     bz, Material.SAND));
                setBlock(bx, baseY + LAYER_3_SAND + 1,  bz, Material.AIR);

                // Aire sobre la capa 3 hasta el techo
                for (int y = PLAYER_OFFSET + 1; y < DOME_HEIGHT; y++) {
                    setBlock(bx, baseY + y, bz, Material.AIR);
                }
            }
        }

        // ── Cúpula de cristal ─────────────────────────────────────────────────
        generateDome(half);
    }

    /**
     * Genera las paredes y el techo de cristal que encierran la arena.
     *
     * Paredes: rodean el perímetro desde Y+FLOOR_OFFSET hasta Y+DOME_HEIGHT.
     * Techo:   cubre todo el interior en Y+DOME_HEIGHT.
     */
    private void generateDome(int half) {
        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        // Paredes norte, sur, este, oeste
        for (int i = -half - 1; i <= half; i++) {
            for (int h = FLOOR_OFFSET; h <= DOME_HEIGHT; h++) {
                // Norte y sur
                setBlock(cx + i,        baseY + h, cz - half - 1, Material.GLASS);
                setBlock(cx + i,        baseY + h, cz + half,     Material.GLASS);
                // Este y oeste
                setBlock(cx - half - 1, baseY + h, cz + i,        Material.GLASS);
                setBlock(cx + half,     baseY + h, cz + i,        Material.GLASS);
            }
        }

        // Techo — cubre todo el interior
        for (int x = -half; x < half; x++) {
            for (int z = -half; z < half; z++) {
                setBlock(cx + x, baseY + DOME_HEIGHT, cz + z, Material.GLASS);
            }
        }
    }

    // ── Restauración ──────────────────────────────────────────────────────────

    public void restore() {
        for (Location loc : tntBlocks) {
            loc.getBlock().setType(Material.TNT, false);
        }
        for (Location loc : sandBlocks) {
            loc.getBlock().setType(Material.SAND, false);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public static int getArenaSize()    { return ARENA_SIZE; }
    public Location   getCenter()       { return center; }
    public World      getWorld()        { return world; }
    public double     getPlayerSpawnY() { return baseY + PLAYER_OFFSET; }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Location setBlock(int x, int y, int z, Material material) {
        Location loc = new Location(world, x, y, z);
        loc.getBlock().setType(material, false);
        return loc;
    }
}