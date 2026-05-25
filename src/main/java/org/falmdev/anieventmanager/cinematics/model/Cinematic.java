package org.falmdev.anieventmanager.cinematics.model;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.falmdev.anieventmanager.utils.LocationUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Una cinematica completa.
 *
 * Persistida en {@code plugins/AniEventManager/cinematics/<id>.yml}.
 *
 * Estructura YAML:
 *
 *   displayName: "Intro del evento"
 *   waypoints:
 *     - world: world
 *       x: 0.0 ... yaw: 0.0 pitch: 0.0
 *       tickOffset: 0
 *       titleMain: "&6Bienvenidos"
 *       titleSub: "&7Al Ani Event"
 *       actionbar: ""
 *       fadeIn: 10
 *       stay: 60
 *       fadeOut: 10
 */
public class Cinematic {

    private final String id;
    private String displayName;
    private CinematicState state = CinematicState.IDLE;
    private final List<CinematicWaypoint> waypoints = new ArrayList<>();

    private final File file;

    public Cinematic(String id, String displayName, File file) {
        this.id          = id;
        this.displayName = displayName;
        this.file        = file;
    }

    // ── Waypoints ─────────────────────────────────────────────────────────────

    public void addWaypoint(CinematicWaypoint waypoint) {
        waypoints.add(waypoint);
        waypoints.sort(Comparator.comparingInt(CinematicWaypoint::getTickOffset));
        reindexWaypoints();
    }

    public boolean removeWaypoint(int index) {
        if (index < 0 || index >= waypoints.size()) return false;
        waypoints.remove(index);
        reindexWaypoints();
        return true;
    }

    private void reindexWaypoints() {
        for (int i = 0; i < waypoints.size(); i++) waypoints.get(i).setIndex(i);
    }

    /** Tick total de la cinematica (tick del último waypoint). */
    public int getTotalTicks() {
        if (waypoints.isEmpty()) return 0;
        return waypoints.get(waypoints.size() - 1).getTickOffset();
    }

    // ── Persistencia ──────────────────────────────────────────────────────────

    public void save() {
        FileConfiguration yaml = new YamlConfiguration();
        yaml.set("displayName", displayName);

        List<java.util.Map<String, Object>> waypointList = new ArrayList<>();
        for (CinematicWaypoint wp : waypoints) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("world",      wp.getLocation().getWorld().getName());
            map.put("x",         wp.getLocation().getX());
            map.put("y",         wp.getLocation().getY());
            map.put("z",         wp.getLocation().getZ());
            map.put("yaw",       (double) wp.getLocation().getYaw());
            map.put("pitch",     (double) wp.getLocation().getPitch());
            map.put("tickOffset", wp.getTickOffset());
            map.put("titleMain",  wp.getTitleMain() != null ? wp.getTitleMain() : "");
            map.put("titleSub",   wp.getTitleSub()  != null ? wp.getTitleSub()  : "");
            map.put("actionbar",  wp.getActionbar() != null ? wp.getActionbar() : "");
            map.put("fadeIn",     wp.getTitleFadeIn());
            map.put("stay",       wp.getTitleStay());
            map.put("fadeOut",    wp.getTitleFadeOut());
            waypointList.add(map);
        }
        yaml.set("waypoints", waypointList);

        try {
            yaml.save(file);
        } catch (IOException e) {
            System.err.println("[AniEventManager] No se pudo guardar cinematica: " + id);
        }
    }

    /**
     * Carga o recarga los waypoints desde el archivo YAML.
     * Llama esto al cargar el plugin o después de un /em reload.
     */
    public void load() {
        if (!file.exists()) return;
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        this.displayName = yaml.getString("displayName", id);
        waypoints.clear();

        List<?> rawList = yaml.getList("waypoints");
        if (rawList == null) return;

        for (Object raw : rawList) {
            if (!(raw instanceof java.util.Map<?, ?> map)) continue;

            try {
                org.bukkit.Location loc = LocationUtil.fromMap((java.util.Map<?, ?>) map);
                if (loc == null) continue;

                int tickOffset = toInt(map.get("tickOffset"), 0);
                CinematicWaypoint wp = new CinematicWaypoint(loc, tickOffset);

                String titleMain = (String) map.get("titleMain");
                String titleSub  = (String) map.get("titleSub");
                String actionbar = (String) map.get("actionbar");

                wp.setTitleMain(titleMain  != null && !titleMain.isBlank()  ? titleMain  : null);
                wp.setTitleSub( titleSub   != null && !titleSub.isBlank()   ? titleSub   : null);
                wp.setActionbar(actionbar  != null && !actionbar.isBlank()  ? actionbar  : null);
                wp.setTitleFadeIn( toInt(map.get("fadeIn"),  10));
                wp.setTitleStay(   toInt(map.get("stay"),    60));
                wp.setTitleFadeOut(toInt(map.get("fadeOut"), 10));

                waypoints.add(wp);
            } catch (Exception e) {
                System.err.println("[AniEventManager] Waypoint inválido en cinematica " + id + ": " + e.getMessage());
            }
        }

        waypoints.sort(Comparator.comparingInt(CinematicWaypoint::getTickOffset));
        reindexWaypoints();
    }

    private int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String           getId()          { return id; }
    public String           getDisplayName() { return displayName; }
    public void             setDisplayName(String name) { this.displayName = name; }
    public CinematicState   getState()       { return state; }
    public void             setState(CinematicState s) { this.state = s; }
    public List<CinematicWaypoint> getWaypoints() { return java.util.Collections.unmodifiableList(waypoints); }
    public File             getFile()        { return file; }
    public boolean          isIdle()         { return state == CinematicState.IDLE; }
    public boolean          isPlaying()      { return state == CinematicState.PLAYING; }
    public boolean          isRecording()    { return state == CinematicState.RECORDING; }
}