package org.falmdev.anieventmanager.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utilidades para serializar/deserializar {@link Location} en archivos YAML.
 *
 * Reutilizable por cualquier minijuego del plugin — evita duplicar la
 * misma lógica de lectura/escritura en cada clase de configuración.
 *
 * Formato YAML generado:
 *
 *   some-key:
 *     world: world_name
 *     x: 0.5
 *     y: 64.0
 *     z: 0.5
 *     yaw: 0.0       ← opcional; omitido si es 0
 *     pitch: 0.0     ← opcional; omitido si es 0
 */
public final class LocationUtil {

    private LocationUtil() {}

    // ── Lectura ───────────────────────────────────────────────────────────────

    /**
     * Lee una {@link Location} almacenada en {@code path} dentro de {@code yaml}.
     *
     * @return la Location, o {@code null} si la ruta no existe, el mundo no está
     *         cargado o algún campo obligatorio falta.
     */
    public static Location read(FileConfiguration yaml, String path) {
        if (!yaml.isConfigurationSection(path)) return null;

        String worldName = yaml.getString(path + ".world", "");
        if (worldName.isEmpty()) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        return new Location(
                world,
                yaml.getDouble(path + ".x"),
                yaml.getDouble(path + ".y"),
                yaml.getDouble(path + ".z"),
                (float) yaml.getDouble(path + ".yaw",   0.0),
                (float) yaml.getDouble(path + ".pitch", 0.0)
        );
    }

    /**
     * Escribe {@code loc} en {@code path} dentro de {@code yaml}.
     * No llama a {@code yaml.save()} — el llamador es responsable de guardar.
     */
    public static void write(FileConfiguration yaml, String path, Location loc) {
        yaml.set(path + ".world", loc.getWorld().getName());
        yaml.set(path + ".x",     loc.getX());
        yaml.set(path + ".y",     loc.getY());
        yaml.set(path + ".z",     loc.getZ());
        yaml.set(path + ".yaw",   (double) loc.getYaw());
        yaml.set(path + ".pitch", (double) loc.getPitch());
    }

    // ── Conversión Map ↔ Location ─────────────────────────────────────────────

    /**
     * Convierte un {@code Map<?, ?>} (leído desde una lista YAML) a {@link Location}.
     *
     * @return la Location, o {@code null} si faltan campos o el mundo no existe.
     */
    public static Location fromMap(Map<?, ?> map) {
        try {
            String worldName = (String) map.get("world");
            if (worldName == null) return null;
            World w = Bukkit.getWorld(worldName);
            if (w == null) return null;

            double x     = toDouble(map.get("x"));
            double y     = toDouble(map.get("y"));
            double z     = toDouble(map.get("z"));
            float  yaw   = map.containsKey("yaw")   ? (float) toDouble(map.get("yaw"))   : 0f;
            float  pitch = map.containsKey("pitch") ? (float) toDouble(map.get("pitch")) : 0f;

            return new Location(w, x, y, z, yaw, pitch);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convierte una {@link Location} a un {@code Map<String, Object>} apto para
     * ser incluido en una lista YAML.
     */
    public static Map<String, Object> toMap(Location loc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("world", loc.getWorld().getName());
        m.put("x",     loc.getX());
        m.put("y",     loc.getY());
        m.put("z",     loc.getZ());
        m.put("yaw",   (double) loc.getYaw());
        m.put("pitch", (double) loc.getPitch());
        return m;
    }

    // ── Helper privado ────────────────────────────────────────────────────────

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }
}