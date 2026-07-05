package org.falmdev.anieventmanager.minigames.parkourduos;

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

public class ParkourDuosConfig {

    private final Anieventmanager plugin;
    private File file;
    private FileConfiguration yaml;

    public ParkourDuosConfig(Anieventmanager plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File dir = new File(plugin.getDataFolder(), "minigames");
        dir.mkdirs();
        file = new File(dir, "parkourduos.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
                yaml = new YamlConfiguration();
                writeDefaults();
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("[ParkourDuos] No se pudo crear parkourduos.yml: " + e.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try { yaml.save(file); } catch (IOException e) {
            plugin.getLogger().severe("[ParkourDuos] No se pudo guardar: " + e.getMessage());
        }
    }

    private void writeDefaults() {
        yaml.set("settings.duration-minutes",    10);
        yaml.set("settings.countdown-seconds",   5);
        yaml.set("settings.chain-max-distance",  8.0);
        yaml.set("settings.score-first",         20);
        yaml.set("settings.score-second",        15);
        yaml.set("settings.score-third",         10);
        yaml.set("settings.score-default",        5);
        yaml.set("settings.score-per-checkpoint", 1);
    }

    public int    getDurationMinutes()    { return yaml.getInt("settings.duration-minutes", 10); }
    public int    getCountdownSeconds()   { return yaml.getInt("settings.countdown-seconds", 5); }
    public double getChainMaxDistance()   { return yaml.getDouble("settings.chain-max-distance", 8.0); }
    public int    getScoreFirst()         { return yaml.getInt("settings.score-first", 20); }
    public int    getScoreSecond()        { return yaml.getInt("settings.score-second", 15); }
    public int    getScoreThird()         { return yaml.getInt("settings.score-third", 10); }
    public int    getScoreDefault()       { return yaml.getInt("settings.score-default", 5); }
    public int    getScorePerCheckpoint() { return yaml.getInt("settings.score-per-checkpoint", 1); }

    public void setDurationMinutes(int v)    { yaml.set("settings.duration-minutes", v);    save(); }
    public void setCountdownSeconds(int v)   { yaml.set("settings.countdown-seconds", v);   save(); }
    public void setChainMaxDistance(double v){ yaml.set("settings.chain-max-distance", v);  save(); }
    public void setScoreForPlace(int place, int score) {
        String key = switch (place) {
            case 1 -> "settings.score-first";
            case 2 -> "settings.score-second";
            case 3 -> "settings.score-third";
            default -> "settings.score-default";
        };
        yaml.set(key, score);
        save();
    }
    public int getScoreForPlace(int place) {
        return switch (place) {
            case 1 -> getScoreFirst();
            case 2 -> getScoreSecond();
            case 3 -> getScoreThird();
            default -> getScoreDefault();
        };
    }

    public void setScorePerCheckpoint(int v) {
        yaml.set("settings.score-per-checkpoint", v);
        save();
    }

    public Location getLobby() {
        return loadLocation("lobby");
    }

    public void setLobby(Location loc) {
        saveLocation("lobby", loc);
    }

    public Location getTeamSpawn1(String teamId) {
        return loadLocation("teams." + teamId + ".spawn1");
    }

    public Location getTeamSpawn2(String teamId) {
        return loadLocation("teams." + teamId + ".spawn2");
    }

    public Location getTeamStart(String teamId) {
        return loadLocation("teams." + teamId + ".start");
    }

    public Location getTeamFinish(String teamId) {
        return loadLocation("teams." + teamId + ".finish");
    }

    public void setTeamSpawn1(String teamId, Location loc) {
        saveLocation("teams." + teamId + ".spawn1", loc);
    }

    public void setTeamSpawn2(String teamId, Location loc) {
        saveLocation("teams." + teamId + ".spawn2", loc);
    }

    public void setTeamStart(String teamId, Location loc) {
        saveLocation("teams." + teamId + ".start", loc);
    }

    public void setTeamFinish(String teamId, Location loc) {
        saveLocation("teams." + teamId + ".finish", loc);
    }

    public List<ParkourCheckpoint> getCheckpoints(String teamId) {
        List<ParkourCheckpoint> list = new ArrayList<>();
        String base = "teams." + teamId + ".checkpoints";
        if (!yaml.isList(base)) return list;

        var rawList = yaml.getMapList(base);
        for (int i = 0; i < rawList.size(); i++) {
            var map = rawList.get(i);
            try {
                String worldName = (String) map.get("world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                double x      = ((Number) map.get("x")).doubleValue();
                double y      = ((Number) map.get("y")).doubleValue();
                double z      = ((Number) map.get("z")).doubleValue();
                Object radiusObj = map.get("radius");
                double radius = radiusObj instanceof Number ? ((Number) radiusObj).doubleValue() : 3.0;
                list.add(new ParkourCheckpoint(i, new Location(world, x, y, z), radius));
            } catch (Exception e) {
                plugin.getLogger().warning("[ParkourDuos] Checkpoint " + i + " inválido para equipo " + teamId);
            }
        }
        return list;
    }

    public void addCheckpoint(String teamId, Location loc, double radius) {
        String base = "teams." + teamId + ".checkpoints";
        var list = yaml.getMapList(base);
        java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("world",  loc.getWorld().getName());
        entry.put("x",      loc.getX());
        entry.put("y",      loc.getY());
        entry.put("z",      loc.getZ());
        entry.put("radius", radius);
        list.add(entry);
        yaml.set(base, list);
        save();
    }

    public void removeCheckpoint(String teamId, int index) {
        String base = "teams." + teamId + ".checkpoints";
        var list = yaml.getMapList(base);
        if (index < 0 || index >= list.size()) return;
        list.remove(index);
        yaml.set(base, list);
        save();
    }

    public void clearCheckpoints(String teamId) {
        yaml.set("teams." + teamId + ".checkpoints", null);
        save();
    }

    public int getCheckpointCount(String teamId) {
        String base = "teams." + teamId + ".checkpoints";
        if (!yaml.isList(base)) return 0;
        return yaml.getMapList(base).size();
    }

    public String validateTeam(String teamId) {
        if (getTeamSpawn1(teamId) == null) return "Falta spawn1 del equipo " + teamId;
        if (getTeamSpawn2(teamId) == null) return "Falta spawn2 del equipo " + teamId;
        if (getTeamStart(teamId)  == null) return "Falta punto de inicio del equipo " + teamId;
        if (getTeamFinish(teamId) == null) return "Falta punto de finalización del equipo " + teamId;
        if (getCheckpointCount(teamId) == 0) return "El equipo " + teamId + " no tiene checkpoints";
        return null;
    }

    public String validateGlobal() {
        if (getLobby() == null) return "El lobby no está configurado. Usa /em pd setlobby.";
        return null;
    }

    private Location loadLocation(String path) {
        if (!yaml.isConfigurationSection(path)) return null;
        String worldName = yaml.getString(path + ".world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                yaml.getDouble(path + ".x"),
                yaml.getDouble(path + ".y"),
                yaml.getDouble(path + ".z"),
                (float) yaml.getDouble(path + ".yaw"),
                (float) yaml.getDouble(path + ".pitch"));
    }

    private void saveLocation(String path, Location loc) {
        yaml.set(path + ".world", loc.getWorld().getName());
        yaml.set(path + ".x",     loc.getX());
        yaml.set(path + ".y",     loc.getY());
        yaml.set(path + ".z",     loc.getZ());
        yaml.set(path + ".yaw",   (double) loc.getYaw());
        yaml.set(path + ".pitch", (double) loc.getPitch());
        save();
    }
}