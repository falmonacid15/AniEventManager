package org.falmdev.anieventmanager.minigames.frozenheist;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.falmdev.anieventmanager.utils.TeamColorUtil;

import java.util.List;

public class BaseColorizer {

    private BaseColorizer() {}

    public static void colorize(Location corner1, Location corner2, NamedTextColor teamColor) {
        if (corner1 == null || corner2 == null) return;
        World world = corner1.getWorld();
        if (world == null) return;

        DyeColor dye = TeamColorUtil.toDyeColor(teamColor);

        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    processBlock(block, dye);
                }
            }
        }
    }

    private static void processBlock(Block block, DyeColor dye) {
        Material mat = block.getType();
        Category cat = getCategory(mat);
        if (cat == null) return;

        switch (cat) {
            case STAINED_GLASS      -> replaceSimple(block, getColoredGlass(dye));
            case GLASS              -> replaceSimple(block, getColoredGlass(dye));
            case STAINED_GLASS_PANE -> replaceWithDirectional(block, getColoredGlassPane(dye));
            case GLASS_PANE         -> replaceWithDirectional(block, getColoredGlassPane(dye));
            case TERRACOTTA         -> replaceSimple(block, getColoredTerracotta(dye));
            case WALL_BANNER        -> replaceWallBanner(block, dye);
            case STANDING_BANNER    -> replaceStandingBanner(block, dye);
        }
    }

    // ── Reemplazos ────────────────────────────────────────────────────────────

    /** Reemplazo simple: solo cambia el tipo, sin orientación ni datos extra. */
    private static void replaceSimple(Block block, Material target) {
        block.setType(target, false);
    }

    /**
     * Reemplazo para paneles de cristal (Directional con conexiones N/S/E/W/UP/DOWN).
     * Bukkit calcula las conexiones al hacer setType, así que alcanza con no pasar physic=false
     * en la llamada final para que actualice los vecinos.
     */
    private static void replaceWithDirectional(Block block, Material target) {
        // Conservar el BlockData viejo para copiar propiedades si el nuevo tipo también las tiene
        BlockData oldData = block.getBlockData();
        block.setType(target, false);

        // Para GlassPane: copiar las conexiones del panel viejo si el nuevo tipo las soporta
        BlockData newData = block.getBlockData();
        if (oldData instanceof org.bukkit.block.data.type.GlassPane oldPane
                && newData instanceof org.bukkit.block.data.type.GlassPane newPane) {
            for (org.bukkit.block.BlockFace face : List.of(
                    org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                    org.bukkit.block.BlockFace.EAST,  org.bukkit.block.BlockFace.WEST,
                    org.bukkit.block.BlockFace.UP)) {
                try { newPane.setFace(face, oldPane.hasFace(face)); } catch (Exception ignored) {}
            }
            newPane.setWaterlogged(oldPane.isWaterlogged());
            block.setBlockData(newPane, false);
        }
    }

    /**
     * Reemplaza un WallBanner conservando:
     *  - La dirección (facing) hacia la que mira
     *  - Los patrones del banner
     */
    private static void replaceWallBanner(Block block, DyeColor dye) {
        // 1. Leer facing y patrones ANTES de cambiar el tipo
        BlockData oldData = block.getBlockData();
        org.bukkit.block.BlockFace facing = null;
        if (oldData instanceof Directional dir) {
            facing = dir.getFacing();
        }

        List<org.bukkit.block.banner.Pattern> patterns = List.of();
        BlockState oldState = block.getState();
        if (oldState instanceof org.bukkit.block.Banner banner) {
            patterns = List.copyOf(banner.getPatterns());
        }

        // 2. Cambiar al nuevo material
        block.setType(getColoredWallBanner(dye), false);

        // 3. Restaurar facing
        if (facing != null) {
            BlockData newData = block.getBlockData();
            if (newData instanceof Directional dir) {
                dir.setFacing(facing);
                block.setBlockData(newData, false);
            }
        }

        // 4. Restaurar patrones
        if (!patterns.isEmpty()) {
            BlockState newState = block.getState();
            if (newState instanceof org.bukkit.block.Banner newBanner) {
                newBanner.setPatterns(patterns);
                newBanner.update(true, false);
            }
        }
    }

    /**
     * Reemplaza un StandingBanner conservando:
     *  - La rotación (BlockFace de 16 direcciones)
     *  - Los patrones
     */
    private static void replaceStandingBanner(Block block, DyeColor dye) {
        // 1. Leer rotación y patrones
        BlockData oldData = block.getBlockData();
        org.bukkit.block.BlockFace rotation = null;
        if (oldData instanceof Rotatable rot) {
            rotation = rot.getRotation();
        }

        List<org.bukkit.block.banner.Pattern> patterns = List.of();
        BlockState oldState = block.getState();
        if (oldState instanceof org.bukkit.block.Banner banner) {
            patterns = List.copyOf(banner.getPatterns());
        }

        // 2. Cambiar tipo
        block.setType(getColoredBanner(dye), false);

        // 3. Restaurar rotación
        if (rotation != null) {
            BlockData newData = block.getBlockData();
            if (newData instanceof Rotatable rot) {
                rot.setRotation(rotation);
                block.setBlockData(newData, false);
            }
        }

        // 4. Restaurar patrones
        if (!patterns.isEmpty()) {
            BlockState newState = block.getState();
            if (newState instanceof org.bukkit.block.Banner newBanner) {
                newBanner.setPatterns(patterns);
                newBanner.update(true, false);
            }
        }
    }

    // ── Clasificador ──────────────────────────────────────────────────────────

    private enum Category {
        GLASS, STAINED_GLASS,
        GLASS_PANE, STAINED_GLASS_PANE,
        TERRACOTTA,
        WALL_BANNER, STANDING_BANNER
    }

    private static Category getCategory(Material mat) {
        return switch (mat) {
            // Cristal sin teñir
            case GLASS              -> Category.GLASS;
            case GLASS_PANE         -> Category.GLASS_PANE;

            // Cristal teñido — bloque
            case WHITE_STAINED_GLASS, ORANGE_STAINED_GLASS, MAGENTA_STAINED_GLASS,
                 LIGHT_BLUE_STAINED_GLASS, YELLOW_STAINED_GLASS, LIME_STAINED_GLASS,
                 PINK_STAINED_GLASS, GRAY_STAINED_GLASS, LIGHT_GRAY_STAINED_GLASS,
                 CYAN_STAINED_GLASS, PURPLE_STAINED_GLASS, BLUE_STAINED_GLASS,
                 BROWN_STAINED_GLASS, GREEN_STAINED_GLASS, RED_STAINED_GLASS,
                 BLACK_STAINED_GLASS -> Category.STAINED_GLASS;

            // Cristal teñido — panel
            case WHITE_STAINED_GLASS_PANE, ORANGE_STAINED_GLASS_PANE, MAGENTA_STAINED_GLASS_PANE,
                 LIGHT_BLUE_STAINED_GLASS_PANE, YELLOW_STAINED_GLASS_PANE, LIME_STAINED_GLASS_PANE,
                 PINK_STAINED_GLASS_PANE, GRAY_STAINED_GLASS_PANE, LIGHT_GRAY_STAINED_GLASS_PANE,
                 CYAN_STAINED_GLASS_PANE, PURPLE_STAINED_GLASS_PANE, BLUE_STAINED_GLASS_PANE,
                 BROWN_STAINED_GLASS_PANE, GREEN_STAINED_GLASS_PANE, RED_STAINED_GLASS_PANE,
                 BLACK_STAINED_GLASS_PANE -> Category.STAINED_GLASS_PANE;

            // Terracota sin teñir + teñida
            case TERRACOTTA,
                 WHITE_TERRACOTTA, ORANGE_TERRACOTTA, MAGENTA_TERRACOTTA,
                 LIGHT_BLUE_TERRACOTTA, YELLOW_TERRACOTTA, LIME_TERRACOTTA,
                 PINK_TERRACOTTA, GRAY_TERRACOTTA, LIGHT_GRAY_TERRACOTTA,
                 CYAN_TERRACOTTA, PURPLE_TERRACOTTA, BLUE_TERRACOTTA,
                 BROWN_TERRACOTTA, GREEN_TERRACOTTA, RED_TERRACOTTA,
                 BLACK_TERRACOTTA -> Category.TERRACOTTA;

            // Banners de pared (todos los colores)
            case WHITE_WALL_BANNER, ORANGE_WALL_BANNER, MAGENTA_WALL_BANNER,
                 LIGHT_BLUE_WALL_BANNER, YELLOW_WALL_BANNER, LIME_WALL_BANNER,
                 PINK_WALL_BANNER, GRAY_WALL_BANNER, LIGHT_GRAY_WALL_BANNER,
                 CYAN_WALL_BANNER, PURPLE_WALL_BANNER, BLUE_WALL_BANNER,
                 BROWN_WALL_BANNER, GREEN_WALL_BANNER, RED_WALL_BANNER,
                 BLACK_WALL_BANNER -> Category.WALL_BANNER;

            // Banners de piso (todos los colores)
            case WHITE_BANNER, ORANGE_BANNER, MAGENTA_BANNER,
                 LIGHT_BLUE_BANNER, YELLOW_BANNER, LIME_BANNER,
                 PINK_BANNER, GRAY_BANNER, LIGHT_GRAY_BANNER,
                 CYAN_BANNER, PURPLE_BANNER, BLUE_BANNER,
                 BROWN_BANNER, GREEN_BANNER, RED_BANNER,
                 BLACK_BANNER -> Category.STANDING_BANNER;

            default -> null;
        };
    }

    // ── Tablas DyeColor → Material ────────────────────────────────────────────

    private static Material getColoredGlass(DyeColor dye) {
        return switch (dye) {
            case WHITE      -> Material.WHITE_STAINED_GLASS;
            case ORANGE     -> Material.ORANGE_STAINED_GLASS;
            case MAGENTA    -> Material.MAGENTA_STAINED_GLASS;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_STAINED_GLASS;
            case YELLOW     -> Material.YELLOW_STAINED_GLASS;
            case LIME       -> Material.LIME_STAINED_GLASS;
            case PINK       -> Material.PINK_STAINED_GLASS;
            case GRAY       -> Material.GRAY_STAINED_GLASS;
            case LIGHT_GRAY -> Material.LIGHT_GRAY_STAINED_GLASS;
            case CYAN       -> Material.CYAN_STAINED_GLASS;
            case PURPLE     -> Material.PURPLE_STAINED_GLASS;
            case BLUE       -> Material.BLUE_STAINED_GLASS;
            case BROWN      -> Material.BROWN_STAINED_GLASS;
            case GREEN      -> Material.GREEN_STAINED_GLASS;
            case RED        -> Material.RED_STAINED_GLASS;
            case BLACK      -> Material.BLACK_STAINED_GLASS;
        };
    }

    private static Material getColoredGlassPane(DyeColor dye) {
        return switch (dye) {
            case WHITE      -> Material.WHITE_STAINED_GLASS_PANE;
            case ORANGE     -> Material.ORANGE_STAINED_GLASS_PANE;
            case MAGENTA    -> Material.MAGENTA_STAINED_GLASS_PANE;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case YELLOW     -> Material.YELLOW_STAINED_GLASS_PANE;
            case LIME       -> Material.LIME_STAINED_GLASS_PANE;
            case PINK       -> Material.PINK_STAINED_GLASS_PANE;
            case GRAY       -> Material.GRAY_STAINED_GLASS_PANE;
            case LIGHT_GRAY -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            case CYAN       -> Material.CYAN_STAINED_GLASS_PANE;
            case PURPLE     -> Material.PURPLE_STAINED_GLASS_PANE;
            case BLUE       -> Material.BLUE_STAINED_GLASS_PANE;
            case BROWN      -> Material.BROWN_STAINED_GLASS_PANE;
            case GREEN      -> Material.GREEN_STAINED_GLASS_PANE;
            case RED        -> Material.RED_STAINED_GLASS_PANE;
            case BLACK      -> Material.BLACK_STAINED_GLASS_PANE;
        };
    }

    private static Material getColoredTerracotta(DyeColor dye) {
        return switch (dye) {
            case WHITE      -> Material.WHITE_TERRACOTTA;
            case ORANGE     -> Material.ORANGE_TERRACOTTA;
            case MAGENTA    -> Material.MAGENTA_TERRACOTTA;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_TERRACOTTA;
            case YELLOW     -> Material.YELLOW_TERRACOTTA;
            case LIME       -> Material.LIME_TERRACOTTA;
            case PINK       -> Material.PINK_TERRACOTTA;
            case GRAY       -> Material.GRAY_TERRACOTTA;
            case LIGHT_GRAY -> Material.LIGHT_GRAY_TERRACOTTA;
            case CYAN       -> Material.CYAN_TERRACOTTA;
            case PURPLE     -> Material.PURPLE_TERRACOTTA;
            case BLUE       -> Material.BLUE_TERRACOTTA;
            case BROWN      -> Material.BROWN_TERRACOTTA;
            case GREEN      -> Material.GREEN_TERRACOTTA;
            case RED        -> Material.RED_TERRACOTTA;
            case BLACK      -> Material.BLACK_TERRACOTTA;
        };
    }

    private static Material getColoredBanner(DyeColor dye) {
        return switch (dye) {
            case WHITE      -> Material.WHITE_BANNER;
            case ORANGE     -> Material.ORANGE_BANNER;
            case MAGENTA    -> Material.MAGENTA_BANNER;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_BANNER;
            case YELLOW     -> Material.YELLOW_BANNER;
            case LIME       -> Material.LIME_BANNER;
            case PINK       -> Material.PINK_BANNER;
            case GRAY       -> Material.GRAY_BANNER;
            case LIGHT_GRAY -> Material.LIGHT_GRAY_BANNER;
            case CYAN       -> Material.CYAN_BANNER;
            case PURPLE     -> Material.PURPLE_BANNER;
            case BLUE       -> Material.BLUE_BANNER;
            case BROWN      -> Material.BROWN_BANNER;
            case GREEN      -> Material.GREEN_BANNER;
            case RED        -> Material.RED_BANNER;
            case BLACK      -> Material.BLACK_BANNER;
        };
    }

    private static Material getColoredWallBanner(DyeColor dye) {
        return switch (dye) {
            case WHITE      -> Material.WHITE_WALL_BANNER;
            case ORANGE     -> Material.ORANGE_WALL_BANNER;
            case MAGENTA    -> Material.MAGENTA_WALL_BANNER;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_WALL_BANNER;
            case YELLOW     -> Material.YELLOW_WALL_BANNER;
            case LIME       -> Material.LIME_WALL_BANNER;
            case PINK       -> Material.PINK_WALL_BANNER;
            case GRAY       -> Material.GRAY_WALL_BANNER;
            case LIGHT_GRAY -> Material.LIGHT_GRAY_WALL_BANNER;
            case CYAN       -> Material.CYAN_WALL_BANNER;
            case PURPLE     -> Material.PURPLE_WALL_BANNER;
            case BLUE       -> Material.BLUE_WALL_BANNER;
            case BROWN      -> Material.BROWN_WALL_BANNER;
            case GREEN      -> Material.GREEN_WALL_BANNER;
            case RED        -> Material.RED_WALL_BANNER;
            case BLACK      -> Material.BLACK_WALL_BANNER;
        };
    }
}