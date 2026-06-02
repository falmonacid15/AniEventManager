package org.falmdev.anieventmanager.minigames.tntrun;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.utils.LocationUtil;

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
 * lobby-spawn:      { world, x, y, z, yaw, pitch }
 * spectator-spawn:  { world, x, y, z, yaw, pitch }
 * arena-center:     { world, x, y, z }
 *
 * player-spawns:
 *   - { world, x, y, z, yaw, pitch }
 *   ...
 *
 * arena:
 *   size:        60         ← lado del cuadrado / diámetro del círculo
 *   shape:       SQUARE     ← SQUARE | CIRCLE
 *   layer-count: 3          ← número de capas de TNT+SAND
 *   layer-gap:   3          ← bloques de AIR entre capas
 *   dome-height: 30         ← altura de la cúpula sobre el jugador
 *
 * settings:
 *   block-remove-delay:  10   ← ticks antes de que caiga el bloque
 *   countdown-seconds:   5
 *   end-delay-seconds:   30   ← segundos que el ganador permanece en la arena
 *   double-jump-enabled: true
 *   double-jump-cooldown: 5   ← segundos de cooldown entre dobles saltos
 *   score-first:   10
 *   score-second:   6
 *   score-third:    3
 *   score-default:  1
 */
public class TNTRunConfig {

    private final Anieventmanager plugin;
    private File              file;
    private FileConfiguration yaml;

    public TNTRunConfig(Anieventmanager plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
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

    public String getWorldName() { return yaml.getString("world", ""); }

    public void setWorldName(String name) {
        yaml.set("world", name);
        save();
    }

    public World getWorld() {
        String name = getWorldName();
        return name.isEmpty() ? null : org.bukkit.Bukkit.getWorld(name);
    }

    // ── Spawns ────────────────────────────────────────────────────────────────

    public Location getLobbySpawn()     { return LocationUtil.read(yaml, "lobby-spawn"); }
    public Location getSpectatorSpawn() { return LocationUtil.read(yaml, "spectator-spawn"); }
    public Location getArenaCenter()    { return LocationUtil.read(yaml, "arena-center"); }

    public void setLobbySpawn(Location loc)     { LocationUtil.write(yaml, "lobby-spawn",     loc); save(); }
    public void setSpectatorSpawn(Location loc) { LocationUtil.write(yaml, "spectator-spawn", loc); save(); }
    public void setArenaCenter(Location loc)    { LocationUtil.write(yaml, "arena-center",    loc); save(); }

    // ── Player spawns ─────────────────────────────────────────────────────────

    public List<Location> getPlayerSpawns() {
        List<Location> list = new ArrayList<>();
        if (!yaml.isList("player-spawns")) return list;
        for (Object o : yaml.getList("player-spawns")) {
            if (o instanceof java.util.Map<?, ?> m) {
                Location l = LocationUtil.fromMap(m);
                if (l != null) list.add(l);
            }
        }
        return list;
    }

    public void addPlayerSpawn(Location loc) {
        List<java.util.Map<String, Object>> current = new ArrayList<>();
        if (yaml.isList("player-spawns")) {
            for (Object o : yaml.getList("player-spawns")) {
                if (o instanceof java.util.Map<?, ?> m)
                    current.add(castMap(m));
            }
        }
        current.add(LocationUtil.toMap(loc));
        yaml.set("player-spawns", current);
        save();
    }

    public void clearPlayerSpawns() {
        yaml.set("player-spawns", new ArrayList<>());
        save();
    }

    // ── Configuración de arena ────────────────────────────────────────────────

    public int getArenaSize() {
        return yaml.getInt("arena.size", TNTRunArena.ArenaConfig.defaults().arenaSize());
    }

    public void setArenaSize(int size) {
        yaml.set("arena.size", Math.max(10, size));
        save();
    }

    public TNTRunArena.Shape getArenaShape() {
        String raw = yaml.getString("arena.shape", "SQUARE").toUpperCase();
        try {
            return TNTRunArena.Shape.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return TNTRunArena.Shape.SQUARE;
        }
    }

    public void setArenaShape(TNTRunArena.Shape shape) {
        yaml.set("arena.shape", shape.name());
        save();
    }

    public int getLayerCount() {
        return yaml.getInt("arena.layer-count", TNTRunArena.ArenaConfig.defaults().layerCount());
    }

    public void setLayerCount(int count) {
        yaml.set("arena.layer-count", Math.max(1, count));
        save();
    }

    public int getLayerGap() {
        return yaml.getInt("arena.layer-gap", TNTRunArena.ArenaConfig.defaults().layerGap());
    }

    public void setLayerGap(int gap) {
        yaml.set("arena.layer-gap", Math.max(1, gap));
        save();
    }

    public int getDomeHeight() {
        return yaml.getInt("arena.dome-height", TNTRunArena.ArenaConfig.defaults().domeHeight());
    }

    public void setDomeHeight(int height) {
        yaml.set("arena.dome-height", Math.max(5, height));
        save();
    }

    public TNTRunArena.ArenaConfig buildArenaConfig() {
        return new TNTRunArena.ArenaConfig(
                getArenaSize(),
                getArenaShape(),
                getLayerCount(),
                getLayerGap(),
                getDomeHeight()
        );
    }

    // ── Settings de juego ─────────────────────────────────────────────────────

    /**
     * Ticks antes de que el bloque desaparezca tras pisarlo.
     * 20 ticks = 1 segundo. Default: 10.
     */
    public int getBlockRemoveDelay() { return yaml.getInt("settings.block-remove-delay", 10); }

    public void setBlockRemoveDelay(int ticks) {
        yaml.set("settings.block-remove-delay", ticks);
        save();
    }

    /** Segundos de cuenta regresiva antes de iniciar. Default: 5. */
    public int getCountdownSeconds() { return yaml.getInt("settings.countdown-seconds", 5); }

    public void setCountdownSeconds(int seconds) {
        yaml.set("settings.countdown-seconds", seconds);
        save();
    }

    /**
     * Segundos que el ganador (y los espectadores) permanecen en la arena
     * antes de que todos sean devueltos al lobby. Default: 30.
     * MODIFICAR con /em tntrun setenddelay <segundos>.
     */
    public int getEndDelaySeconds() {
        return yaml.getInt("settings.end-delay-seconds", 30);
    }

    public void setEndDelaySeconds(int seconds) {
        yaml.set("settings.end-delay-seconds", Math.max(5, seconds));
        save();
    }

    /** Convierte el end-delay de segundos a ticks de Bukkit (×20). */
    public long getEndDelayTicks() {
        return getEndDelaySeconds() * 20L;
    }

    // ── Doble salto ───────────────────────────────────────────────────────────

    public boolean isDoubleJumpEnabled() {
        return yaml.getBoolean("settings.double-jump-enabled", true);
    }

    public void setDoubleJumpEnabled(boolean enabled) {
        yaml.set("settings.double-jump-enabled", enabled);
        save();
    }

    public int getDoubleJumpCooldown() {
        return yaml.getInt("settings.double-jump-cooldown", 5);
    }

    public void setDoubleJumpCooldown(int seconds) {
        yaml.set("settings.double-jump-cooldown", Math.max(0, seconds));
        save();
    }

    // ── Puntajes por posición ─────────────────────────────────────────────────

    public int getScoreForPlace(int place) {
        return switch (place) {
            case 1  -> yaml.getInt("settings.score-first",    10);
            case 2  -> yaml.getInt("settings.score-second",    6);
            case 3  -> yaml.getInt("settings.score-third",     3);
            default -> yaml.getInt("settings.score-default",   1);
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

    public String validate() {
        if (getWorld() == null)
            return "El mundo no está configurado. Usa /em tntrun setworld.";
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

    // ── Helper privado ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> castMap(java.util.Map<?, ?> m) {
        return (java.util.Map<String, Object>) m;
    }
}