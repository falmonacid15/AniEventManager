package org.falmdev.anieventmanager.minigames.boatracing;

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
import java.util.UUID;

/**
 * Configuración del Boat Racing en:
 * plugins/AniEventManager/minigames/boatracing.yml
 *
 * Estructura:
 *
 * settings:
 *   total-laps: 3
 *   default-boat: OAK
 *   qualy-duration-seconds: 180
 *   score-per-position:
 *     1: 25  2: 18  3: 15  4: 12  5: 10
 *     6: 8   7: 6   8: 4   default: 2
 *
 * paddock-spawn:
 *   world: ...  x: y: z: yaw: pitch:
 *
 * track:
 *   point-a: { world: x: y: z: }
 *   point-b: { world: x: y: z: }
 *   finish-a: { world: x: y: z: }   ← extremo A de la meta (dentro de la región)
 *   finish-b: { world: x: y: z: }   ← extremo B de la meta
 *
 * player-spawns:
 *   - { world: x: y: z: yaw: pitch: }
 *
 * checkpoints:
 *   - { world: x: y: z: radius: 4.0 }
 *
 * lights:
 *   - { world: x: y: z: }
 *
 * player-boats:
 *   <uuid>: OAK
 */
public class BoatRacingConfig {

    private final Anieventmanager plugin;
    private File file;
    private FileConfiguration yaml;

    public BoatRacingConfig(Anieventmanager plugin) {
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
        file = new File(dir, "boatracing.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
                yaml = new YamlConfiguration();
                writeDefaults();
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear boatracing.yml: " + e.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { yaml.save(file); }
        catch (IOException e) { plugin.getLogger().severe("No se pudo guardar boatracing.yml: " + e.getMessage()); }
    }

    private void writeDefaults() {
        yaml.set("settings.total-laps",             3);
        yaml.set("settings.default-boat",           "OAK");
        yaml.set("settings.qualy-duration-seconds", 180);
        yaml.set("settings.score-per-position.1",   25);
        yaml.set("settings.score-per-position.2",   18);
        yaml.set("settings.score-per-position.3",   15);
        yaml.set("settings.score-per-position.4",   12);
        yaml.set("settings.score-per-position.5",   10);
        yaml.set("settings.score-per-position.6",    8);
        yaml.set("settings.score-per-position.7",    6);
        yaml.set("settings.score-per-position.8",    4);
        yaml.set("settings.score-per-position.default", 2);
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    public int      getTotalLaps()          { return yaml.getInt("settings.total-laps", 3); }
    public void     setTotalLaps(int n)     { yaml.set("settings.total-laps", n); save(); }

    public BoatType getDefaultBoat()        { return BoatType.fromString(yaml.getString("settings.default-boat", "OAK")); }
    public void     setDefaultBoat(BoatType t) { yaml.set("settings.default-boat", t.name()); save(); }

    public int      getQualyDuration()      { return yaml.getInt("settings.qualy-duration-seconds", 180); }
    public void     setQualyDuration(int s) { yaml.set("settings.qualy-duration-seconds", s); save(); }

    public int getScoreForPosition(int pos) {
        int s = yaml.getInt("settings.score-per-position." + pos, -1);
        return s == -1 ? yaml.getInt("settings.score-per-position.default", 2) : s;
    }
    public void setScoreForPosition(int pos, int score) {
        yaml.set("settings.score-per-position." + pos, score); save();
    }

    // ── Paddock ───────────────────────────────────────────────────────────────

    public Location getPaddockSpawn()           { return readLocation("paddock-spawn"); }
    public void     setPaddockSpawn(Location l) { writeLocation("paddock-spawn", l); save(); }

    // ── Región de la pista ────────────────────────────────────────────────────

    public Location getTrackPointA()            { return readLocationRaw("track.point-a"); }
    public void     setTrackPointA(Location l)  { writeLocationRaw("track.point-a", l); save(); }

    public Location getTrackPointB()            { return readLocationRaw("track.point-b"); }
    public void     setTrackPointB(Location l)  { writeLocationRaw("track.point-b", l); save(); }

    public boolean hasTrackRegion() {
        return getTrackPointA() != null && getTrackPointB() != null;
    }

    /** Construye el TrackRegion con la región y la línea de meta */
    public TrackRegion buildTrackRegion() {
        Location a = getTrackPointA(), b = getTrackPointB();
        if (a == null || b == null) return null;
        TrackRegion region = new TrackRegion(a, b);
        region.setFinishA(readLocationRaw("track.finish-a"));
        region.setFinishB(readLocationRaw("track.finish-b"));
        return region;
    }

    // ── Meta ──────────────────────────────────────────────────────────────────

    public Location getFinishA()            { return readLocationRaw("track.finish-a"); }
    public void     setFinishA(Location l)  { writeLocationRaw("track.finish-a", l); save(); }

    public Location getFinishB()            { return readLocationRaw("track.finish-b"); }
    public void     setFinishB(Location l)  { writeLocationRaw("track.finish-b", l); save(); }

    public boolean hasFinishLine() {
        return getFinishA() != null && getFinishB() != null;
    }

    // ── Spawns de parrilla ────────────────────────────────────────────────────

    public List<Location> getPlayerSpawns() {
        List<Location> list = new ArrayList<>();
        if (!yaml.isList("player-spawns")) return list;
        for (Object o : yaml.getList("player-spawns"))
            if (o instanceof java.util.Map<?,?> m) { Location l = mapToLocation(m); if (l != null) list.add(l); }
        return list;
    }

    public void addPlayerSpawn(Location loc) {
        List<java.util.Map<String, Object>> cur = new ArrayList<>();
        if (yaml.isList("player-spawns"))
            for (Object o : yaml.getList("player-spawns"))
                if (o instanceof java.util.Map<?,?> m) cur.add(castMap(m));
        cur.add(locationToMap(loc));
        yaml.set("player-spawns", cur); save();
    }

    public void clearPlayerSpawns() { yaml.set("player-spawns", new ArrayList<>()); save(); }

    // ── Checkpoints ───────────────────────────────────────────────────────────

    public record CheckpointData(Location location, double radius) {}

    public List<CheckpointData> getCheckpoints() {
        List<CheckpointData> list = new ArrayList<>();
        if (!yaml.isList("checkpoints")) return list;
        for (Object o : yaml.getList("checkpoints")) {
            if (o instanceof java.util.Map<?,?> m) {
                Location loc = mapToLocation(m);
                double radius = m.get("radius") instanceof Number n ? n.doubleValue() : 4.0;
                if (loc != null) list.add(new CheckpointData(loc, radius));
            }
        }
        return list;
    }

    public void addCheckpoint(Location loc, double radius) {
        List<java.util.Map<String, Object>> cur = new ArrayList<>();
        if (yaml.isList("checkpoints"))
            for (Object o : yaml.getList("checkpoints"))
                if (o instanceof java.util.Map<?,?> m) cur.add(castMap(m));
        java.util.Map<String, Object> e = locationToMap(loc);
        e.put("radius", radius);
        cur.add(e);
        yaml.set("checkpoints", cur); save();
    }

    public void clearCheckpoints() { yaml.set("checkpoints", new ArrayList<>()); save(); }

    // ── Luces ─────────────────────────────────────────────────────────────────

    public List<Location> getLights() {
        List<Location> list = new ArrayList<>();
        if (!yaml.isList("lights")) return list;
        for (Object o : yaml.getList("lights"))
            if (o instanceof java.util.Map<?,?> m) { Location l = mapToLocation(m); if (l != null) list.add(l); }
        return list;
    }

    public void addLight(Location loc) {
        List<java.util.Map<String, Object>> cur = new ArrayList<>();
        if (yaml.isList("lights"))
            for (Object o : yaml.getList("lights"))
                if (o instanceof java.util.Map<?,?> m) cur.add(castMap(m));
        cur.add(locationToMap(loc));
        yaml.set("lights", cur); save();
    }

    public void clearLights() { yaml.set("lights", new ArrayList<>()); save(); }

    // ── Bote por jugador ──────────────────────────────────────────────────────

    public BoatType getPlayerBoat(UUID uuid) {
        String raw = yaml.getString("player-boats." + uuid, null);
        return raw != null ? BoatType.fromString(raw) : getDefaultBoat();
    }

    public void setPlayerBoat(UUID uuid, BoatType type) {
        yaml.set("player-boats." + uuid, type.name()); save();
    }

    // ── Validación ────────────────────────────────────────────────────────────

    public String validate() {
        if (getPaddockSpawn() == null)
            return "Falta el spawn del paddock. Usa /em boatracing setpaddock.";
        if (!hasTrackRegion())
            return "Falta definir la región de la pista. Usa /em boatracing track a y track b.";
        if (!hasFinishLine())
            return "Falta la línea de meta. Usa /em boatracing finish a y finish b.";
        if (getPlayerSpawns().isEmpty())
            return "No hay spawns de parrilla. Usa /em boatracing addspawn.";
        if (getLights().size() < 5)
            return "Se necesitan 5 luces. Actualmente hay " + getLights().size() + ". Usa /em boatracing addlight.";
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Location readLocation(String path) {
        if (!yaml.isConfigurationSection(path)) return null;
        World w = Bukkit.getWorld(yaml.getString(path + ".world", ""));
        if (w == null) return null;
        return new Location(w, yaml.getDouble(path + ".x"), yaml.getDouble(path + ".y"),
                yaml.getDouble(path + ".z"),
                (float) yaml.getDouble(path + ".yaw"), (float) yaml.getDouble(path + ".pitch"));
    }

    private Location readLocationRaw(String path) {
        if (!yaml.isConfigurationSection(path)) return null;
        World w = Bukkit.getWorld(yaml.getString(path + ".world", ""));
        if (w == null) return null;
        return new Location(w, yaml.getDouble(path + ".x"),
                yaml.getDouble(path + ".y"), yaml.getDouble(path + ".z"));
    }

    private void writeLocation(String path, Location loc) {
        yaml.set(path + ".world", loc.getWorld().getName());
        yaml.set(path + ".x",    loc.getX()); yaml.set(path + ".y", loc.getY()); yaml.set(path + ".z", loc.getZ());
        yaml.set(path + ".yaw",  (double) loc.getYaw()); yaml.set(path + ".pitch", (double) loc.getPitch());
    }

    private void writeLocationRaw(String path, Location loc) {
        yaml.set(path + ".world", loc.getWorld().getName());
        yaml.set(path + ".x", loc.getX()); yaml.set(path + ".y", loc.getY()); yaml.set(path + ".z", loc.getZ());
    }

    private Location mapToLocation(java.util.Map<?,?> map) {
        try {
            World w = Bukkit.getWorld((String) map.get("world"));
            if (w == null) return null;
            double x = toD(map.get("x")), y = toD(map.get("y")), z = toD(map.get("z"));
            Object ry = map.get("yaw"), rp = map.get("pitch");
            return new Location(w, x, y, z, ry!=null?(float)toD(ry):0f, rp!=null?(float)toD(rp):0f);
        } catch (Exception e) { return null; }
    }

    private java.util.Map<String, Object> locationToMap(Location loc) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("world", loc.getWorld().getName());
        m.put("x", loc.getX()); m.put("y", loc.getY()); m.put("z", loc.getZ());
        m.put("yaw", (double) loc.getYaw()); m.put("pitch", (double) loc.getPitch());
        return m;
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> castMap(java.util.Map<?,?> m) {
        return (java.util.Map<String, Object>) m;
    }

    private double toD(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }
}