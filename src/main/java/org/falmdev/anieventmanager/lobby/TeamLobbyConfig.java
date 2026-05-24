package org.falmdev.anieventmanager.lobby;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.falmdev.anieventmanager.Anieventmanager;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Persistencia de la "team selection lobby":
 *  - Carteles registrados (cada uno asociado a un teamId)
 *  - Lámparas registradas (cada una asociada a un teamId)
 *
 * Estructura en plugins/AniEventManager/team-lobby.yml:
 *
 *   signs:
 *     "world,123,64,-50":
 *       team: red
 *     "world,128,64,-50":
 *       team: blue
 *
 *   lamps:
 *     "world,123,63,-49":
 *       team: red
 *     ...
 */
public class TeamLobbyConfig {

    private final Anieventmanager plugin;
    private File              file;
    private FileConfiguration yaml;

    // Mapas en memoria para acceso rápido
    // key = locationKey, value = teamId
    private final Map<String, String> signs = new HashMap<>();
    private final Map<String, String> lamps = new HashMap<>();

    public TeamLobbyConfig(Anieventmanager plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        load();
    }

    // ── Carga / guardado ──────────────────────────────────────────────────────

    private void load() {
        File dir = plugin.getDataFolder();
        dir.mkdirs();
        file = new File(dir, "team-lobby.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear team-lobby.yml: " + e.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);

        signs.clear();
        lamps.clear();

        if (yaml.isConfigurationSection("signs")) {
            var section = yaml.getConfigurationSection("signs");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    String teamId = yaml.getString("signs." + key + ".team");
                    if (teamId != null) signs.put(key, teamId);
                }
            }
        }

        if (yaml.isConfigurationSection("lamps")) {
            var section = yaml.getConfigurationSection("lamps");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    String teamId = yaml.getString("lamps." + key + ".team");
                    if (teamId != null) lamps.put(key, teamId);
                }
            }
        }
    }

    private void save() {
        // Reescribir desde cero para que reflejé el estado actual
        yaml = new YamlConfiguration();
        for (var e : signs.entrySet()) yaml.set("signs." + e.getKey() + ".team", e.getValue());
        for (var e : lamps.entrySet()) yaml.set("lamps." + e.getKey() + ".team", e.getValue());
        try { yaml.save(file); } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar team-lobby.yml: " + ex.getMessage());
        }
    }

    // ── Signs ─────────────────────────────────────────────────────────────────

    public boolean registerSign(Block block, String teamId) {
        if (block == null) return false;
        signs.put(locationKey(block.getLocation()), teamId.toLowerCase());
        save();
        return true;
    }

    public boolean unregisterSign(Block block) {
        if (block == null) return false;
        boolean removed = signs.remove(locationKey(block.getLocation())) != null;
        if (removed) save();
        return removed;
    }

    public String getSignTeam(Block block) {
        if (block == null) return null;
        return signs.get(locationKey(block.getLocation()));
    }

    public boolean isSignRegistered(Block block) {
        return block != null && signs.containsKey(locationKey(block.getLocation()));
    }

    public Map<String, String> getAllSigns() {
        return Collections.unmodifiableMap(signs);
    }

    /** Devuelve los carteles registrados asociados a un equipo (resueltos a Block). */
    public List<Block> getSignsForTeam(String teamId) {
        String id = teamId.toLowerCase();
        List<Block> out = new ArrayList<>();
        for (var e : signs.entrySet()) {
            if (!e.getValue().equals(id)) continue;
            Block b = blockFromKey(e.getKey());
            if (b != null) out.add(b);
        }
        return out;
    }

    /** Devuelve todos los bloques de cartel registrados, sin filtrar por equipo. */
    public List<Block> getAllSignBlocks() {
        List<Block> out = new ArrayList<>();
        for (String key : signs.keySet()) {
            Block b = blockFromKey(key);
            if (b != null) out.add(b);
        }
        return out;
    }

    // ── Lamps ─────────────────────────────────────────────────────────────────

    public boolean registerLamp(Block block, String teamId) {
        if (block == null) return false;
        lamps.put(locationKey(block.getLocation()), teamId.toLowerCase());
        save();
        return true;
    }

    public boolean unregisterLamp(Block block) {
        if (block == null) return false;
        boolean removed = lamps.remove(locationKey(block.getLocation())) != null;
        if (removed) save();
        return removed;
    }

    public String getLampTeam(Block block) {
        if (block == null) return null;
        return lamps.get(locationKey(block.getLocation()));
    }

    public boolean isLampRegistered(Block block) {
        return block != null && lamps.containsKey(locationKey(block.getLocation()));
    }

    public List<Block> getLampsForTeam(String teamId) {
        String id = teamId.toLowerCase();
        List<Block> out = new ArrayList<>();
        for (var e : lamps.entrySet()) {
            if (!e.getValue().equals(id)) continue;
            Block b = blockFromKey(e.getKey());
            if (b != null) out.add(b);
        }
        return out;
    }

    public List<Block> getAllLampBlocks() {
        List<Block> out = new ArrayList<>();
        for (String key : lamps.keySet()) {
            Block b = blockFromKey(key);
            if (b != null) out.add(b);
        }
        return out;
    }

    // ── Utilidades de location ────────────────────────────────────────────────

    /** Formato: "world,x,y,z" usando coordenadas de bloque enteras. */
    private String locationKey(Location loc) {
        return loc.getWorld().getName() + "," +
                loc.getBlockX() + "," +
                loc.getBlockY() + "," +
                loc.getBlockZ();
    }

    private Block blockFromKey(String key) {
        String[] parts = key.split(",");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return world.getBlockAt(
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }
}