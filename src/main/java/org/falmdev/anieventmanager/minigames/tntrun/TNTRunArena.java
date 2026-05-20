package org.falmdev.anieventmanager.minigames.tntrun;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Genera (y destruye) la arena de TNT Run a partir de un {@link ArenaConfig}.
 *
 * ── Estructura vertical desde el Y base ──────────────────────────────────────
 *
 *   Y+0  → BEDROCK  (suelo)
 *   Y+1  → WATER    (zona de eliminación)
 *   Y+2..Y+9 → AIR  (espacio fijo de caída — siempre 10 bloques hasta la capa 1)
 *
 *   Por cada capa i (0..layerCount-1):
 *     offset_base   = 10 + i * (2 + layerGap)
 *     offset_base+0 → TNT
 *     offset_base+1 → SAND
 *     offset_base+2..layerGap+1 → AIR  (espacio entre capas)
 *
 *   Sobre la última capa:
 *     PLAYER_OFFSET = offset_base_última + 2  (jugadores se paran aquí)
 *
 * ── Cúpula ───────────────────────────────────────────────────────────────────
 *   Paredes de GLASS desde Y+0 hasta Y+(PLAYER_OFFSET + domeHeight).
 *   Techo de GLASS en ese mismo Y máximo.
 *
 * ── Formas ───────────────────────────────────────────────────────────────────
 *   SQUARE  → cuadrado completo (comportamiento original).
 *   CIRCLE  → disco: solo se colocan bloques cuya distancia al centro ≤ radio.
 *             El radio = arenaSize / 2. Las esquinas quedan vacías (AIR).
 */
public class TNTRunArena {

    // ── Tipos de forma ────────────────────────────────────────────────────────

    public enum Shape { SQUARE, CIRCLE }

    // ── Configuración de arena (record inmutable) ─────────────────────────────

    /**
     * Todos los parámetros que definen el tamaño y estructura de la arena.
     * Se construye desde {@link TNTRunConfig} y se pasa al constructor.
     *
     * @param arenaSize   Lado del cuadrado (o diámetro del círculo) en bloques.
     * @param shape       {@link Shape#SQUARE} o {@link Shape#CIRCLE}.
     * @param layerCount  Número de capas de TNT+SAND (mínimo 1).
     * @param layerGap    Bloques de AIR entre capas (mínimo 1).
     * @param domeHeight  Altura de la cúpula sobre el suelo del jugador (mínimo 5).
     */
    public record ArenaConfig(
            int arenaSize,
            Shape shape,
            int layerCount,
            int layerGap,
            int domeHeight
    ) {
        /** Validación defensiva para evitar configuraciones absurdas. */
        public ArenaConfig {
            arenaSize  = Math.max(10,  arenaSize);
            layerCount = Math.max(1,   layerCount);
            layerGap   = Math.max(1,   layerGap);
            domeHeight = Math.max(5,   domeHeight);
        }

        /** Devuelve la configuración por defecto (equivalente al comportamiento original). */
        public static ArenaConfig defaults() {
            return new ArenaConfig(60, Shape.SQUARE, 3, 3, 30);
        }
    }

    // ── Estado interno ────────────────────────────────────────────────────────

    private final Location    center;
    private final World       world;
    private final int         baseY;
    private final ArenaConfig cfg;

    /** Ubicaciones de todos los bloques SAND generados (para restaurar). */
    private final List<Location> sandBlocks = new ArrayList<>();
    /** Ubicaciones de todos los bloques TNT generados (para restaurar). */
    private final List<Location> tntBlocks  = new ArrayList<>();

    // ── Offsets calculados (calculados en el constructor) ─────────────────────

    /** Offset del Y donde se paran los jugadores al inicio. */
    private final int playerOffset;
    /** Offset del techo de la cúpula. */
    private final int roofOffset;

    // ── Constructor ───────────────────────────────────────────────────────────

    public TNTRunArena(Location center, ArenaConfig cfg) {
        this.center = center;
        this.world  = center.getWorld();
        this.baseY  = center.getBlockY();
        this.cfg    = cfg;

        // Calcula los offsets según la configuración de capas
        // Cada capa ocupa: 1 TNT + 1 SAND + layerGap bloques de AIR = 2 + layerGap
        // La primera capa empieza siempre en Y+10 (espacio fijo de caída sobre el agua)
        int lastLayerBase  = 10 + (cfg.layerCount() - 1) * (2 + cfg.layerGap());
        this.playerOffset  = lastLayerBase + 2;              // encima del último SAND
        this.roofOffset    = playerOffset + cfg.domeHeight();
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /** Genera la arena completa en el mundo. */
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

    /**
     * Restaura todos los bloques TNT y SAND a su estado original.
     * Se usa cuando el juego termina y se quiere reiniciar la arena.
     */
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

    /**
     * Elimina completamente la arena — reemplaza todos los bloques generados
     * (incluyendo suelo, agua, cúpula) por AIR.
     *
     * Útil para el comando {@code /em tntrun clear}.
     */
    public void clear() {
        int half = cfg.arenaSize() / 2;
        int cx   = center.getBlockX();
        int cz   = center.getBlockZ();

        // Interior — desde suelo hasta techo
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

        // Cúpula — paredes y techo
        clearDome(half);

        sandBlocks.clear();
        tntBlocks.clear();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Location   getCenter()       { return center; }
    public World      getWorld()        { return world; }
    public ArenaConfig getArenaConfig() { return cfg; }

    /** Y donde deben teletransportarse los jugadores al inicio. */
    public double getPlayerSpawnY()     { return baseY + playerOffset; }

    /** Expone la lista de bloques SAND (para el listener). */
    public List<Location> getSandBlocks() { return sandBlocks; }

    // ── Generación interna ────────────────────────────────────────────────────

    /** Coloca la columna completa de bloques para una coordenada (bx, bz). */
    private void placeColumn(int bx, int bz) {
        // Suelo y agua
        setBlock(bx, baseY,     bz, Material.BEDROCK);
        setBlock(bx, baseY + 1, bz, Material.WATER);

        // AIR fijo desde Y+2 hasta Y+9 (espacio de caída al agua — siempre 10 bloques)
        for (int y = 2; y < 10; y++) {
            setBlock(bx, baseY + y, bz, Material.AIR);
        }

        // Capas de TNT + SAND + AIR
        for (int i = 0; i < cfg.layerCount(); i++) {
            int base = 10 + i * (2 + cfg.layerGap());

            tntBlocks.add(setBlock(bx, baseY + base,      bz, Material.TNT));
            sandBlocks.add(setBlock(bx, baseY + base + 1, bz, Material.SAND));

            // AIR de separación entre capas
            for (int g = 2; g < 2 + cfg.layerGap(); g++) {
                setBlock(bx, baseY + base + g, bz, Material.AIR);
            }
        }

        // Aire sobre la última capa hasta justo antes del techo
        for (int y = playerOffset; y < roofOffset; y++) {
            setBlock(bx, baseY + y, bz, Material.AIR);
        }
    }

    /**
     * Genera las paredes y el techo de cristal que encierran la arena.
     *
     * SQUARE: paredes rectas en los 4 lados del bounding-box.
     * CIRCLE: paredes siguiendo el perímetro circular — por cada Y se coloca
     *         vidrio en los bloques del borde exterior del disco (aquellos que
     *         están dentro del radio pero cuyo vecino inmediato está fuera).
     *         Además se añade un anillo exterior de 1 bloque de grosor para
     *         que no haya huecos entre bloques diagonales.
     */
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
            // Techo cuadrado interior
            for (int x = -half; x < half; x++) {
                for (int z = -half; z < half; z++) {
                    setBlock(cx + x, baseY + roofOffset, cz + z, Material.GLASS);
                }
            }
        } else {
            // Paredes circulares — recorrer un cuadrado ligeramente más grande que
            // el radio y colocar vidrio donde el bloque está en el anillo exterior
            double r     = half;           // radio interior (borde del disco)
            double rOuter = half + 1.5;   // anillo exterior de 1 bloque de grosor

            for (int x = -half - 2; x <= half + 2; x++) {
                for (int z = -half - 2; z <= half + 2; z++) {
                    double dx = x + 0.5;
                    double dz = z + 0.5;
                    double dist2 = dx * dx + dz * dz;

                    // Pertenece al anillo de pared si está entre radio interior y exterior
                    if (dist2 > r * r && dist2 <= rOuter * rOuter) {
                        for (int h = 0; h <= roofOffset; h++) {
                            setBlock(cx + x, baseY + h, cz + z, Material.GLASS);
                        }
                    }
                }
            }
            // Techo circular (cubre el interior completo del disco)
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

    /** Elimina la cúpula (paredes y techo) colocando AIR. */
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Determina si la coordenada relativa (x, z) pertenece al interior de la arena
     * según la forma configurada.
     *
     * @param x    Offset X relativo al centro.
     * @param z    Offset Z relativo al centro.
     * @param half Mitad del tamaño (radio para círculo, half-side para cuadrado).
     */
    private boolean isInside(int x, int z, int half) {
        if (cfg.shape() == Shape.SQUARE) return true;
        // Círculo: distancia euclidiana desde el centro ≤ radio
        // Usamos double para evitar problemas en los bordes
        double dx = x + 0.5;
        double dz = z + 0.5;
        return (dx * dx + dz * dz) <= (double) half * half;
    }

    /** Coloca un bloque y devuelve su Location (para registrar en las listas). */
    private Location setBlock(int x, int y, int z, Material material) {
        Location loc = new Location(world, x, y, z);
        loc.getBlock().setType(material, false);
        return loc;
    }
}