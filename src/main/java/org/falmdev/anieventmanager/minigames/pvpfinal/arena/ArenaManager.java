package org.falmdev.anieventmanager.minigames.pvpfinal.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.falmdev.anieventmanager.Anieventmanager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ArenaManager — gestión de la única arena del minijuego.
 *
 * Persistencia: plugins/AniEventManager/minigames/pvpfinal-arena.yml
 *
 * Por diseño, solo existe UNA arena. Los comandos crean/modifican esa única
 * arena. Si no existe, los combates fallan.
 */
public class ArenaManager {

    private final Anieventmanager plugin;
    private PvpArena arena;
    private File              file;
    private FileConfiguration yaml;

    public ArenaManager(Anieventmanager plugin) {
        this.plugin = plugin;
        load();
    }

    // ── API ───────────────────────────────────────────────────────────────────

    public PvpArena getArena()       { return arena; }
    public boolean  hasArena()       { return arena != null; }

    public void createArena(String name) {
        arena = new PvpArena(name);
        save();
    }

    public void deleteArena() {
        arena = null;
        save();
    }

    public boolean setSpawn(int index, Location loc) {
        if (arena == null) return false;
        if (index < 1) return false;
        arena.setSpawn(index, loc);
        save();
        return true;
    }

    public boolean setLobby(Location loc) {
        if (arena == null) return false;
        arena.setLobby(loc);
        save();
        return true;
    }

    // ── Persistencia ──────────────────────────────────────────────────────────

    public void load() {
        File dir = new File(plugin.getDataFolder(), "minigames");
        dir.mkdirs();
        file = new File(dir, "pvpfinal-arena.yml");
        if (!file.exists()) {
            try { file.createNewFile(); }
            catch (IOException e) {
                plugin.getLogger().severe("[PvP] No se pudo crear pvpfinal-arena.yml");
                return;
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.isConfigurationSection("arena")) {
            arena = null;
            return;
        }
        ConfigurationSection sec = yaml.getConfigurationSection("arena");
        String name = sec.getString("name", "main");

        List<Location> spawns = new ArrayList<>();
        List<?> rawSpawns = sec.getList("spawns");
        if (rawSpawns != null) {
            for (Object o : rawSpawns) {
                if (!(o instanceof Map<?, ?> m)) { spawns.add(null); continue; }
                spawns.add(readLocation(m));
            }
        }

        Location lobby = null;
        if (sec.isConfigurationSection("lobby")) {
            Map<String, Object> m = sec.getConfigurationSection("lobby").getValues(false);
            lobby = readLocation(m);
        }

        arena = new PvpArena(name, spawns, lobby);
        plugin.getLogger().info("[PvP] Arena '" + name + "' cargada con "
                + spawns.size() + " spawns.");
    }

    public void save() {
        yaml.set("arena", null);
        if (arena == null) {
            try { yaml.save(file); } catch (IOException ignored) {}
            return;
        }
        yaml.set("arena.name", arena.getName());

        List<Map<String, Object>> spawnsList = new ArrayList<>();
        for (Location l : arena.getSpawns()) {
            spawnsList.add(locationToMap(l));
        }
        yaml.set("arena.spawns", spawnsList);

        if (arena.getLobby() != null) {
            yaml.set("arena.lobby", locationToMap(arena.getLobby()));
        }

        try { yaml.save(file); }
        catch (IOException e) {
            plugin.getLogger().severe("[PvP] No se pudo guardar arena: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Location readLocation(Map<?, ?> m) {
        if (m == null) return null;
        Object worldObj = m.get("world");
        if (worldObj == null) return null;
        World w = Bukkit.getWorld(String.valueOf(worldObj));
        if (w == null) return null;
        return new Location(w,
                toDouble(m.get("x")),
                toDouble(m.get("y")),
                toDouble(m.get("z")),
                (float) toDouble(m.get("yaw")),
                (float) toDouble(m.get("pitch")));
    }

    private Map<String, Object> locationToMap(Location l) {
        if (l == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("world", l.getWorld().getName());
        m.put("x", l.getX());
        m.put("y", l.getY());
        m.put("z", l.getZ());
        m.put("yaw", (double) l.getYaw());
        m.put("pitch", (double) l.getPitch());
        return m;
    }

    private double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}