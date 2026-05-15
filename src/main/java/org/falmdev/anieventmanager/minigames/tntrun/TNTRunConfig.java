package org.falmdev.anieventmanager.minigames.tntrun;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.falmdev.anieventmanager.Anieventmanager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Maneja la configuración persistente del TNT Run en:
 * plugins/AniEventManager/minigames/tntrun.yml
 *
 * Estructura del archivo:
 *
 * world: "tntrun_world"
 *
 * lobby-spawn:
 *   world: tntrun_world
 *   x: 0.5  y: 100.0  z: 0.5  yaw: 0.0  pitch: 0.0
 *
 * spectator-spawn:
 *   world: tntrun_world
 *   x: 0.5  y: 120.0  z: 0.5  yaw: 0.0  pitch: 0.0
 *
 * arena-center:
 *   world: tntrun_world
 *   x: 0.0  y: 80.0  z: 0.0
 *
 * player-spawns:
 *   - { world: tntrun_world, x: 5.5, y: 83.0, z: 5.5, yaw: 0.0, pitch: 0.0 }
 *   - { world: tntrun_world, x: -4.5, y: 83.0, z: 5.5, yaw: 180.0, pitch: 0.0 }
 *   ...
 *
 * settings:
 *   block-remove-delay: 10     <- ticks antes de que caiga el bloque (default: 10)
 *   countdown-seconds: 5       <- cuenta regresiva al inicio
 *   score-first: 10
 *   score-second: 6
 *   score-third: 3
 *   score-default: 1
 */
public class TNTRunConfig {

    private final Anieventmanager plugin;
    private File file;
    private FileConfiguration yaml;

    public TNTRunConfig(Anieventmanager plugin) {
        this.plugin = plugin;
        load();
    }

    // ── Carga y guardado ──────────────────────────────────────────────────────

    public void load() {
        File dir = new File(plugin.getDataFolder(), "minigames");
        dir.mkdirs();
        file = new File(dir, "tntrun.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear tntrun.yml: " + e.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { yaml.save(file); } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar tntrun.yml: " + e.getMessage());
        }
    }

    // ── World ─────────────────────────────────────────────────────────────────

    public String getWorldName() {
        return yaml.getString("world", "");
    }

    public void setWorldName(String name) {
        yaml.set("world", name);
        save();
    }

    public World getWorld() {
        String name = getWorldName();
        if (name.isEmpty()) return null;
        return Bukkit.getWorld(name);
    }

    // ── Lobby spawn ───────────────────────────────────────────────────────────

    public Location getLobbySpawn() {
        return readLocation("lobby-spawn");
    }

    public void setLobbySpawn(Location loc) {
        writeLocation("lobby-spawn", loc);
        save();
    }

    // ── Spectator spawn ───────────────────────────────────────────────────────

    public Location getSpectatorSpawn() {
        return readLocation("spectator-spawn");
    }

    public void setSpectatorSpawn(Location loc) {
        writeLocation("spectator-spawn", loc);
        save();
    }

    // ── Arena center ──────────────────────────────────────────────────────────

    public Location getArenaCenter() {
        return readLocation("arena-center");
    }

    public void setArenaCenter(Location loc) {
        writeLocation("arena-center", loc);
        save();
    }

    // ── Player spawns ─────────────────────────────────────────────────────────

    public List<Location> getPlayerSpawns() {
        List<Location> list = new ArrayList<>();
        if (!yaml.isList("player-spawns")) return list;
        for (Object o : yaml.getList("player-spawns")) {
            if (o instanceof java.util.Map<?, ?> m) {
                Location l = mapToLocation(m);
                if (l != null) list.add(l);
            }
        }
        return list;
    }

    public void addPlayerSpawn(Location loc) {
        List<java.util.Map<String, Object>> current = new ArrayList<>();
        if (yaml.isList("player-spawns")) {
            for (Object o : yaml.getList("player-spawns")) {
                if (o instanceof java.util.Map<?, ?> m) current.add(castMap(m));
            }
        }
        current.add(locationToMap(loc));
        yaml.set("player-spawns", current);
        save();
    }

    public void clearPlayerSpawns() {
        yaml.set("player-spawns", new ArrayList<>());
        save();
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    /**
     * Ticks antes de que el bloque desaparezca tras pisarlo.
     * 20 ticks = 1 segundo. Default: 10 (medio segundo).
     * MODIFICAR AQUI para cambiar la velocidad de caída de bloques.
     */
    public int getBlockRemoveDelay() {
        return yaml.getInt("settings.block-remove-delay", 10);
    }

    public void setBlockRemoveDelay(int ticks) {
        yaml.set("settings.block-remove-delay", ticks);
        save();
    }

    /**
     * Segundos de cuenta regresiva antes de iniciar.
     * MODIFICAR AQUI para cambiar la duración de la cuenta regresiva.
     */
    public int getCountdownSeconds() {
        return yaml.getInt("settings.countdown-seconds", 5);
    }

    public void setCountdownSeconds(int seconds) {
        yaml.set("settings.countdown-seconds", seconds);
        save();
    }

    // ── Puntajes por posición ─────────────────────────────────────────────────

    public int getScoreForPlace(int place) {
        return switch (place) {
            case 1  -> yaml.getInt("settings.score-first",   10);
            case 2  -> yaml.getInt("settings.score-second",   6);
            case 3  -> yaml.getInt("settings.score-third",    3);
            default -> yaml.getInt("settings.score-default",  1);
        };
    }

    public void setScoreForPlace(int place, int score) {
        String key = switch (place) {
            case 1  -> "settings.score-first";
            case 2  -> "settings.score-second";
            case 3  -> "settings.score-third";
            default -> "settings.score-default";
        };
        yaml.set(key, score);
        save();
    }

    // ── Validación ────────────────────────────────────────────────────────────

    /**
     * Verifica que la configuración mínima esté completa para poder iniciar.
     * Devuelve null si todo está bien, o un mensaje de error si falta algo.
     */
    public String validate() {
        if (getWorld() == null)
            return "El mundo no está configurado. Usa /em tntrun setworld <mundo>.";
        if (getLobbySpawn() == null)
            return "El lobby spawn no está configurado. Usa /em tntrun setlobby.";
        if (getSpectatorSpawn() == null)
            return "El spawn de espectadores no está configurado. Usa /em tntrun setspectator.";
        if (getArenaCenter() == null)
            return "El centro de la arena no está configurado. Usa /em tntrun setcenter.";
        if (getPlayerSpawns().isEmpty())
            return "No hay spawns de jugadores. Usa /em tntrun addspawn.";
        return null;
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private Location readLocation(String path) {
        if (!yaml.isConfigurationSection(path)) return null;
        String worldName = yaml.getString(path + ".world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(
                world,
                yaml.getDouble(path + ".x"),
                yaml.getDouble(path + ".y"),
                yaml.getDouble(path + ".z"),
                (float) yaml.getDouble(path + ".yaw"),
                (float) yaml.getDouble(path + ".pitch")
        );
    }

    private void writeLocation(String path, Location loc) {
        yaml.set(path + ".world", loc.getWorld().getName());
        yaml.set(path + ".x",     loc.getX());
        yaml.set(path + ".y",     loc.getY());
        yaml.set(path + ".z",     loc.getZ());
        yaml.set(path + ".yaw",   (double) loc.getYaw());
        yaml.set(path + ".pitch", (double) loc.getPitch());
    }

    private Location mapToLocation(java.util.Map<?, ?> map) {
        try {
            World w = Bukkit.getWorld((String) map.get("world"));
            if (w == null) return null;
            double x     = toDouble(map.get("x"));
            double y     = toDouble(map.get("y"));
            double z     = toDouble(map.get("z"));
            Object rawYaw   = map.get("yaw");
            Object rawPitch = map.get("pitch");
            float  yaw   = rawYaw   != null ? (float) toDouble(rawYaw)   : 0f;
            float  pitch = rawPitch != null ? (float) toDouble(rawPitch) : 0f;
            return new Location(w, x, y, z, yaw, pitch);
        } catch (Exception e) { return null; }
    }

    private java.util.Map<String, Object> locationToMap(Location loc) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("world", loc.getWorld().getName());
        m.put("x",     loc.getX());
        m.put("y",     loc.getY());
        m.put("z",     loc.getZ());
        m.put("yaw",   (double) loc.getYaw());
        m.put("pitch", (double) loc.getPitch());
        return m;
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> castMap(java.util.Map<?, ?> m) {
        return (java.util.Map<String, Object>) m;
    }

    private double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }
}