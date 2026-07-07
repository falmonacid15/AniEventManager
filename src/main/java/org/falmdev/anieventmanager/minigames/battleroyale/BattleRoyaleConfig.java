package org.falmdev.anieventmanager.minigames.battleroyale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.falmdev.anieventmanager.Anieventmanager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class BattleRoyaleConfig {

    public static final double[] ZONE_PHASE_PERCENTAGES = { 1.00, 0.60, 0.30, 0.10, 0.02 };

    public static final double ZONE_MIN_RADIUS = 2.0;

    private final Anieventmanager plugin;
    private File              file;
    private FileConfiguration yaml;

    public BattleRoyaleConfig(Anieventmanager plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File dir = new File(plugin.getDataFolder(), "minigames");
        dir.mkdirs();
        file = new File(dir, "battleroyale.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
                yaml = new YamlConfiguration();
                writeDefaults();
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("[BR] No se pudo crear battleroyale.yml: " + e.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() { yaml = YamlConfiguration.loadConfiguration(file); }

    public void save() {
        try { yaml.save(file); }
        catch (IOException e) { plugin.getLogger().severe("[BR] No se pudo guardar battleroyale.yml"); }
    }

    private void writeDefaults() {
        yaml.set("settings.countdown-seconds", 10);
        yaml.set("settings.min-players",       2);
        yaml.set("settings.starting-coins",    0);

        yaml.set("drop.height",       200);
        yaml.set("drop.speed",        0.6);
        yaml.set("drop.direction.x",  1.0);
        yaml.set("drop.direction.z",  0.0);
        yaml.set("drop.parachute-slow-falling-amplifier", 3);
        yaml.set("drop.parachute-ticks", 400);

        yaml.set("arena.y-min", 0);
        yaml.set("arena.y-max", 256);

        List<java.util.Map<String, Object>> phases = new ArrayList<>();
        int[][] phaseData = {
                {120, 60, 1},
                { 90, 45, 2},
                { 60, 30, 4},
                { 30, 15, 8},
                { 30,  0, 16}
        };
        for (int[] d : phaseData) {
            java.util.Map<String, Object> m = new LinkedHashMap<>();
            m.put("wait-seconds",      d[0]);
            m.put("shrink-seconds",    d[1]);
            m.put("damage-per-second", d[2]);
            phases.add(m);
        }
        yaml.set("zone.phases", phases);

        yaml.set("zone.particles.height-below",   10);
        yaml.set("zone.particles.height-above",   30);
        yaml.set("zone.particles.step",            1.0);
        yaml.set("zone.particles.max-render-dist", 150);
        yaml.set("zone.particles.size",            3.0);
        yaml.set("zone.particles.interval-ticks",  5);

        yaml.set("points.first",  100);
        yaml.set("points.second",  60);
        yaml.set("points.third",   30);
        yaml.set("points.other",   10);
    }

    public int  getCountdownSeconds() { return yaml.getInt("settings.countdown-seconds", 10); }
    public int  getMinPlayers()       { return yaml.getInt("settings.min-players", 2); }
    public int  getStartingCoins()    { return yaml.getInt("settings.starting-coins", 0); }

    public void setCountdownSeconds(int v) { yaml.set("settings.countdown-seconds", v); save(); }
    public void setMinPlayers(int v)       { yaml.set("settings.min-players", v);       save(); }
    public void setStartingCoins(int v)    { yaml.set("settings.starting-coins", v);    save(); }

    public int  getCoinsPerKill()       { return yaml.getInt("coins.per-kill", 50); }
    public void setCoinsPerKill(int v)  { yaml.set("coins.per-kill", v); save(); }

    public int getPointsForPlacement(int placement) {
        return switch (placement) {
            case 1  -> yaml.getInt("points.first", 100);
            case 2  -> yaml.getInt("points.second", 60);
            case 3  -> yaml.getInt("points.third", 30);
            default -> yaml.getInt("points.other", 10);
        };
    }

    public void setPointsForPlacement(int placement, int points) {
        String key = switch (placement) {
            case 1  -> "points.first";
            case 2  -> "points.second";
            case 3  -> "points.third";
            default -> "points.other";
        };
        yaml.set(key, points);
        save();
    }

    public Location getLobbySpawn()           { return readLocation("lobby-spawn"); }
    public void     setLobbySpawn(Location l) { writeLocation("lobby-spawn", l); save(); }

    public String getArenaWorld() {
        Location center = getArenaCenter();
        if (center != null) return center.getWorld().getName();
        return yaml.getString("arena.world", "");
    }

    public Location getArenaCenter()          { return readLocationRaw("arena.center"); }
    public void     setArenaCenter(Location l){ writeLocationRaw("arena.center", l); save(); }

    public Location getArenaPos1() {
        if (!yaml.isConfigurationSection("arena.pos1")) return null;
        World w = Bukkit.getWorld(yaml.getString("arena.pos1.world",
                yaml.getString("arena.world", "")));
        if (w == null) return null;
        return new Location(w,
                yaml.getDouble("arena.pos1.x"), getYMin(), yaml.getDouble("arena.pos1.z"));
    }

    public Location getArenaPos2() {
        if (!yaml.isConfigurationSection("arena.pos2")) return null;
        World w = Bukkit.getWorld(yaml.getString("arena.pos2.world",
                yaml.getString("arena.world", "")));
        if (w == null) return null;
        return new Location(w,
                yaml.getDouble("arena.pos2.x"), getYMax(), yaml.getDouble("arena.pos2.z"));
    }

    public void setArenaPos1(Location l) {
        yaml.set("arena.pos1.world", l.getWorld().getName());
        yaml.set("arena.pos1.x", l.getX());
        yaml.set("arena.pos1.z", l.getZ());
        save();
    }

    public void setArenaPos2(Location l) {
        yaml.set("arena.pos2.world", l.getWorld().getName());
        yaml.set("arena.pos2.x", l.getX());
        yaml.set("arena.pos2.z", l.getZ());
        save();
    }

    public int  getYMin()       { return yaml.getInt("arena.y-min", 0); }
    public int  getYMax()       { return yaml.getInt("arena.y-max", 256); }
    public void setYMin(int v)  { yaml.set("arena.y-min", v); save(); }
    public void setYMax(int v)  { yaml.set("arena.y-max", v); save(); }

    public boolean isInsideArena(Location loc) {
        Location p1 = getArenaPos1();
        Location p2 = getArenaPos2();
        if (p1 == null || p2 == null) return true;
        if (loc.getWorld() != p1.getWorld()) return false;
        double minX = Math.min(p1.getX(), p2.getX());
        double maxX = Math.max(p1.getX(), p2.getX());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxZ = Math.max(p1.getZ(), p2.getZ());
        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getZ() >= minZ && loc.getZ() <= maxZ
                && loc.getY() >= getYMin() && loc.getY() <= getYMax();
    }

    public java.util.List<Location> getRespawnPoints() {
        java.util.List<Location> list = new java.util.ArrayList<>();
        if (!yaml.isList("respawn-points")) return list;
        for (Object o : yaml.getList("respawn-points")) {
            if (!(o instanceof java.util.Map<?, ?> m)) continue;
            World w = Bukkit.getWorld(String.valueOf(m.get("world")));
            if (w == null) continue;
            list.add(new Location(w,
                    toD(m.get("x")), toD(m.get("y")), toD(m.get("z")),
                    (float) toD(m.get("yaw")), (float) toD(m.get("pitch"))));
        }
        return list;
    }

    public void addRespawnPoint(Location loc) {
        java.util.List<java.util.Map<String, Object>> raw = new java.util.ArrayList<>();
        if (yaml.isList("respawn-points")) {
            for (Object o : yaml.getList("respawn-points")) {
                if (o instanceof java.util.Map<?, ?> m) {
                    java.util.Map<String, Object> copy = new java.util.LinkedHashMap<>();
                    for (var e : m.entrySet()) copy.put(String.valueOf(e.getKey()), e.getValue());
                    raw.add(copy);
                }
            }
        }
        java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("world", loc.getWorld().getName());
        entry.put("x", loc.getX());
        entry.put("y", loc.getY());
        entry.put("z", loc.getZ());
        entry.put("yaw", (double) loc.getYaw());
        entry.put("pitch", (double) loc.getPitch());
        raw.add(entry);
        yaml.set("respawn-points", raw);
        save();
    }

    public boolean removeRespawnPoint(int index) {
        if (!yaml.isList("respawn-points")) return false;
        java.util.List<?> raw = yaml.getList("respawn-points");
        if (raw == null || index < 0 || index >= raw.size()) return false;
        java.util.List<Object> copy = new java.util.ArrayList<>(raw);
        copy.remove(index);
        yaml.set("respawn-points", copy);
        save();
        return true;
    }

    public void clearRespawnPoints() {
        yaml.set("respawn-points", null);
        save();
    }

    public double getDropHeight()        { return yaml.getDouble("drop.height", 200); }
    public void   setDropHeight(double v){ yaml.set("drop.height", v); save(); }

    public double getDropSpeed()         { return yaml.getDouble("drop.speed", 0.6); }
    public void   setDropSpeed(double v) { yaml.set("drop.speed", v); save(); }

    public org.bukkit.util.Vector getDropDirection() {
        double x = yaml.getDouble("drop.direction.x", 1.0);
        double z = yaml.getDouble("drop.direction.z", 0.0);
        return new org.bukkit.util.Vector(x, 0, z).normalize();
    }
    public void setDropDirection(double x, double z) {
        yaml.set("drop.direction.x", x);
        yaml.set("drop.direction.z", z);
        save();
    }

    public Location getDropStart()           { return readLocationRaw("drop.start"); }
    public void     setDropStart(Location l) { writeLocationRaw("drop.start", l); save(); }

    public Location getDropEnd()             { return readLocationRaw("drop.end"); }
    public void     setDropEnd(Location l)   { writeLocationRaw("drop.end", l); save(); }

    public int getParachuteSFAmplifier()     { return yaml.getInt("drop.parachute-slow-falling-amplifier", 3); }
    public int getParachuteTicks()           { return yaml.getInt("drop.parachute-ticks", 400); }
    public void setParachuteSFAmplifier(int v){ yaml.set("drop.parachute-slow-falling-amplifier", v); save(); }
    public void setParachuteTicks(int v)      { yaml.set("drop.parachute-ticks", v); save(); }

    public record ZonePhaseData(double radius, int waitSeconds, int shrinkSeconds, double damagePerSecond) {}

    public Location getZoneCenter() {
        Location p1 = getArenaPos1();
        Location p2 = getArenaPos2();
        if (p1 == null || p2 == null) return null;
        return new Location(p1.getWorld(),
                (p1.getX() + p2.getX()) / 2.0,
                (getYMin() + getYMax()) / 2.0,
                (p1.getZ() + p2.getZ()) / 2.0);
    }

    public double getZoneMaxRadius() {
        Location p1 = getArenaPos1();
        Location p2 = getArenaPos2();
        if (p1 == null || p2 == null) return 0;
        double dx = Math.abs(p1.getX() - p2.getX());
        double dz = Math.abs(p1.getZ() - p2.getZ());
        return Math.min(dx, dz) / 2.0;
    }

    public List<ZonePhaseData> getZonePhases() {
        List<ZonePhaseData> list = new ArrayList<>();
        if (!yaml.isList("zone.phases")) return list;

        double maxRadius = getZoneMaxRadius();
        if (maxRadius <= 0) return list;

        List<?> rawPhases = yaml.getList("zone.phases");
        int phaseCount = Math.min(rawPhases.size(), ZONE_PHASE_PERCENTAGES.length);

        for (int i = 0; i < phaseCount; i++) {
            Object o = rawPhases.get(i);
            if (!(o instanceof java.util.Map<?,?> m)) continue;

            double radius = Math.max(ZONE_MIN_RADIUS, maxRadius * ZONE_PHASE_PERCENTAGES[i]);

            int    wait   = (int) toD(m.get("wait-seconds"));
            Object shrVal = m.get("shrink-seconds");
            int    shrink = shrVal != null ? (int) toD(shrVal) : 30;
            double dmg    = toD(m.get("damage-per-second"));

            list.add(new ZonePhaseData(radius, wait, shrink, dmg));
        }
        return list;
    }

    public int    getZoneParticleHeightBelow()  { return yaml.getInt("zone.particles.height-below", 10); }
    public int    getZoneParticleHeightAbove()  { return yaml.getInt("zone.particles.height-above", 30); }
    public double getZoneParticleStep()         { return yaml.getDouble("zone.particles.step", 1.0); }
    public double getZoneParticleMaxRenderDist(){ return yaml.getDouble("zone.particles.max-render-dist", 150); }
    public float  getZoneParticleSize()         { return (float) yaml.getDouble("zone.particles.size", 3.0); }
    public int    getZoneParticleIntervalTicks(){ return yaml.getInt("zone.particles.interval-ticks", 5); }

    public String validate() {
        if (getLobbySpawn()  == null) return "Falta lobby spawn. /em battleroyale setlobby";
        if (getArenaCenter() == null) return "Falta centro de arena. /em battleroyale setcenter";
        if (getArenaPos1()   == null) return "Falta pos1 de la arena. /em battleroyale arena pos1";
        if (getArenaPos2()   == null) return "Falta pos2 de la arena. /em battleroyale arena pos2";
        if (getDropStart()   == null) return "Falta inicio del drop. /em battleroyale drop setstart";
        if (getDropEnd()     == null) return "Falta final del drop. /em battleroyale drop setend";
        if (getZonePhases().isEmpty()) return "Sin fases de zona configuradas.";
        return null;
    }

    private Location readLocation(String path) {
        if (!yaml.isConfigurationSection(path)) return null;
        World w = Bukkit.getWorld(yaml.getString(path + ".world", ""));
        if (w == null) return null;
        return new Location(w,
                yaml.getDouble(path + ".x"), yaml.getDouble(path + ".y"), yaml.getDouble(path + ".z"),
                (float) yaml.getDouble(path + ".yaw"), (float) yaml.getDouble(path + ".pitch"));
    }

    private Location readLocationRaw(String path) {
        if (!yaml.isConfigurationSection(path)) return null;
        World w = Bukkit.getWorld(yaml.getString(path + ".world", ""));
        if (w == null) return null;
        return new Location(w,
                yaml.getDouble(path + ".x"), yaml.getDouble(path + ".y"), yaml.getDouble(path + ".z"));
    }

    private void writeLocation(String path, Location loc) {
        yaml.set(path + ".world", loc.getWorld().getName());
        yaml.set(path + ".x",     loc.getX());
        yaml.set(path + ".y",     loc.getY());
        yaml.set(path + ".z",     loc.getZ());
        yaml.set(path + ".yaw",   (double) loc.getYaw());
        yaml.set(path + ".pitch", (double) loc.getPitch());
    }

    private void writeLocationRaw(String path, Location loc) {
        yaml.set(path + ".world", loc.getWorld().getName());
        yaml.set(path + ".x", loc.getX());
        yaml.set(path + ".y", loc.getY());
        yaml.set(path + ".z", loc.getZ());
    }

    private double toD(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}