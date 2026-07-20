package org.falmdev.anieventmanager.minigames.bingo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.Material;
import org.falmdev.anieventmanager.Anieventmanager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BingoConfig {

    private final Anieventmanager plugin;
    private File file;
    private FileConfiguration yaml;

    public BingoConfig(Anieventmanager plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void load() {
        File dir = new File(plugin.getDataFolder(), "minigames");
        dir.mkdirs();
        file = new File(dir, "bingo.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
                yaml = new YamlConfiguration();
                writeDefaults();
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear bingo.yml: " + e.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try { yaml.save(file); } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar bingo.yml: " + e.getMessage());
        }
    }

    private void writeDefaults() {
        yaml.set("settings.duration-minutes", 30);
        yaml.set("settings.countdown-seconds", 5);
        yaml.set("settings.score-first",  10);
        yaml.set("settings.score-second",  6);
        yaml.set("settings.score-third",   3);
        yaml.set("settings.score-default", 1);
    }

    public Location getSpawn() {
        if (!yaml.isConfigurationSection("spawn")) return null;
        String worldName = yaml.getString("spawn.world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                yaml.getDouble("spawn.x"),
                yaml.getDouble("spawn.y"),
                yaml.getDouble("spawn.z"),
                (float) yaml.getDouble("spawn.yaw"),
                (float) yaml.getDouble("spawn.pitch"));
    }

    public void setSpawn(Location loc) {
        yaml.set("spawn.world", loc.getWorld().getName());
        yaml.set("spawn.x",     loc.getX());
        yaml.set("spawn.y",     loc.getY());
        yaml.set("spawn.z",     loc.getZ());
        yaml.set("spawn.yaw",   (double) loc.getYaw());
        yaml.set("spawn.pitch", (double) loc.getPitch());
        save();
    }

    public int getCountdownSeconds() { return yaml.getInt("settings.countdown-seconds", 5); }
    public void setCountdownSeconds(int seconds) { yaml.set("settings.countdown-seconds", seconds); save(); }

    public int getDurationMinutes() { return yaml.getInt("settings.duration-minutes", 30); }
    public void setDurationMinutes(int minutes) { yaml.set("settings.duration-minutes", minutes); save(); }

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

    public List<BingoTask> loadTasks() {
        List<BingoTask> list = new ArrayList<>();
        if (!yaml.isConfigurationSection("tasks")) return list;
        var section = yaml.getConfigurationSection("tasks");
        if (section == null) return list;

        for (String id : section.getKeys(false)) {
            String path    = "tasks." + id;
            String typeStr = yaml.getString(path + ".type", "OBTAIN_ITEM").toUpperCase();
            String name    = yaml.getString(path + ".display-name", id);

            try {
                BingoTask.Type type = BingoTask.Type.valueOf(typeStr);
                BingoTask task = new BingoTask(id, type, name);

                switch (type) {
                    case OBTAIN_ITEM, CRAFT_ITEM, EQUIP_ITEM, FISH_ITEM -> {
                        String matStr = yaml.getString(path + ".material", "STONE").toUpperCase();
                        task.setMaterial(Material.valueOf(matStr));
                        task.setAmount(yaml.getInt(path + ".amount", 1));
                    }
                    case KILL_MOB -> {
                        String mobStr = yaml.getString(path + ".mob", "ZOMBIE").toUpperCase();
                        task.setMobType(EntityType.valueOf(mobStr));
                        task.setMobCount(yaml.getInt(path + ".count", 1));
                    }
                    case REACH_LOCATION -> {
                        task.setLocation(
                                yaml.getString(path + ".world", "world"),
                                yaml.getDouble(path + ".x"),
                                yaml.getDouble(path + ".y"),
                                yaml.getDouble(path + ".z"),
                                yaml.getDouble(path + ".radius", 5.0)
                        );
                    }
                    case VISIT_STRUCTURE -> {
                        task.setStructureKey(yaml.getString(path + ".structure",
                                "minecraft:trial_chambers"));
                    }
                    case TRADE_ANY -> {
                        task.setAmount(yaml.getInt(path + ".amount", 1));
                    }
                    case TRADE_ITEM -> {
                        String matStr = yaml.getString(path + ".material", "EMERALD").toUpperCase();
                        task.setMaterial(Material.valueOf(matStr));
                        task.setAmount(yaml.getInt(path + ".amount", 1));
                    }
                }

                String desc = yaml.getString(path + ".description", null);
                if (desc != null) task.setDescription(desc);

                String iconStr = yaml.getString(path + ".icon", null);
                if (iconStr != null) {
                    try { task.setIcon(Material.valueOf(iconStr.toUpperCase())); }
                    catch (IllegalArgumentException ignored) {}
                }

                String iconTexture = yaml.getString(path + ".icon-texture", null);
                if (iconTexture != null) task.setIconTexture(iconTexture);

                list.add(task);

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Bingo] Tarea inválida '" + id + "': " + e.getMessage());
            }
        }
        return list;
    }

    public void saveTask(BingoTask task) {
        String path = "tasks." + task.getId();
        yaml.set(path + ".type",         task.getType().name());
        yaml.set(path + ".display-name", task.getDisplayName());

        switch (task.getType()) {
            case OBTAIN_ITEM, CRAFT_ITEM, EQUIP_ITEM, FISH_ITEM -> {
                yaml.set(path + ".material", task.getMaterial().name());
                yaml.set(path + ".amount",   task.getAmount());
            }
            case KILL_MOB -> {
                yaml.set(path + ".mob",   task.getMobType().name());
                yaml.set(path + ".count", task.getMobCount());
            }
            case REACH_LOCATION -> {
                yaml.set(path + ".world",  task.getLocationWorld());
                yaml.set(path + ".x",      task.getLocationX());
                yaml.set(path + ".y",      task.getLocationY());
                yaml.set(path + ".z",      task.getLocationZ());
                yaml.set(path + ".radius", task.getLocationRadius());
            }
            case VISIT_STRUCTURE -> {
                yaml.set(path + ".structure", task.getStructureKey());
            }
            case TRADE_ANY -> {
                yaml.set(path + ".amount", task.getAmount());
            }
            case TRADE_ITEM -> {
                yaml.set(path + ".material", task.getMaterial().name());
                yaml.set(path + ".amount",   task.getAmount());
            }
        }

        yaml.set(path + ".icon", task.hasCustomIcon() ? task.getIcon().name() : null);
        yaml.set(path + ".icon-texture", task.hasIconTexture() ? task.getIconTexture() : null);
        yaml.set(path + ".description", task.hasDescription() ? task.getDescription() : null);
        save();
    }

    public void removeTask(String id) { yaml.set("tasks." + id, null); save(); }
    public void clearTasks()          { yaml.set("tasks", null); save(); }

    public int getTaskCount() {
        if (!yaml.isConfigurationSection("tasks")) return 0;
        var section = yaml.getConfigurationSection("tasks");
        return section == null ? 0 : section.getKeys(false).size();
    }

    public boolean hasTask(String id) {
        return yaml.isConfigurationSection("tasks." + id);
    }

    public List<BingoWall> loadWalls() {
        List<BingoWall> list = new ArrayList<>();
        if (!yaml.isConfigurationSection("walls")) return list;
        var section = yaml.getConfigurationSection("walls");
        if (section == null) return list;

        for (String id : section.getKeys(false)) {
            String path = "walls." + id;
            list.add(new BingoWall(id,
                    yaml.getString(path + ".world", "world"),
                    yaml.getInt(path + ".x1"), yaml.getInt(path + ".y1"), yaml.getInt(path + ".z1"),
                    yaml.getInt(path + ".x2"), yaml.getInt(path + ".y2"), yaml.getInt(path + ".z2")));
        }
        return list;
    }

    public void saveWall(BingoWall wall) {
        String path = "walls." + wall.getId();
        yaml.set(path + ".world", wall.getWorld());
        yaml.set(path + ".x1", wall.getX1()); yaml.set(path + ".y1", wall.getY1()); yaml.set(path + ".z1", wall.getZ1());
        yaml.set(path + ".x2", wall.getX2()); yaml.set(path + ".y2", wall.getY2()); yaml.set(path + ".z2", wall.getZ2());
        save();
    }

    public void removeWall(String id)  { yaml.set("walls." + id, null); save(); }
    public boolean hasWall(String id)  { return yaml.isConfigurationSection("walls." + id); }

    public int getWallCount() {
        if (!yaml.isConfigurationSection("walls")) return 0;
        var section = yaml.getConfigurationSection("walls");
        return section == null ? 0 : section.getKeys(false).size();
    }

    public String validate() {
        if (getTaskCount() == 0)
            return "No hay tareas configuradas. Usa /em bingo task add para agregar tareas.";
        return null;
    }
}