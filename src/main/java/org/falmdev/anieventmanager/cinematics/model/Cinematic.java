package org.falmdev.anieventmanager.cinematics.model;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Cinematic {

    private final String id;
    private String displayName;
    private CinematicState state = CinematicState.IDLE;

    private final List<CinematicFrame>  frames  = new ArrayList<>();
    private final List<CinematicMarker> markers = new ArrayList<>();

    private long timeStart   = -1;
    private long timeEnd     = -1;
    private int  fadeInTicks  = 0;   // 0 = sin fade
    private int  fadeOutTicks = 0;

    private final File file;

    public Cinematic(String id, String displayName, File file) {
        this.id          = id;
        this.displayName = displayName;
        this.file        = file;
    }

    // ── Frames ────────────────────────────────────────────────────────────────

    public void addFrame(CinematicFrame frame)  { frames.add(frame); }
    public void clearFrames()                   { frames.clear(); }
    public List<CinematicFrame> getFrames()     { return Collections.unmodifiableList(frames); }
    public CinematicFrame getFrame(int tick)    { return (tick < 0 || tick >= frames.size()) ? null : frames.get(tick); }
    public int getTotalFrames()                 { return frames.size(); }
    public int getTotalTicks()                  { return frames.size(); }
    public double getDurationSeconds()          { return frames.size() / 20.0; }

    // ── Markers ───────────────────────────────────────────────────────────────

    public void addMarker(CinematicMarker marker) {
        markers.removeIf(m -> m.getTick() == marker.getTick());
        markers.add(marker);
        markers.sort(Comparator.comparingInt(CinematicMarker::getTick));
    }

    public boolean removeMarker(int tick)              { return markers.removeIf(m -> m.getTick() == tick); }
    public Optional<CinematicMarker> getMarkerAt(int tick) { return markers.stream().filter(m -> m.getTick() == tick).findFirst(); }
    public List<CinematicMarker> getMarkers()          { return Collections.unmodifiableList(markers); }

    // ── Tiempo del mundo ──────────────────────────────────────────────────────

    public boolean hasTimeControl() { return timeStart >= 0 && timeEnd >= 0; }

    public long getWorldTimeAt(int currentTick) {
        if (!hasTimeControl() || getTotalTicks() <= 0) return -1;
        double progress = Math.max(0.0, Math.min(1.0, (double) currentTick / getTotalTicks()));
        long start = timeStart, end = timeEnd;
        long range = end >= start ? end - start : 24000L - start + end;
        long result = (start + (long)(range * progress)) % 24000L;
        return result < 0 ? result + 24000L : result;
    }

    public long getTimeStart()       { return timeStart; }
    public long getTimeEnd()         { return timeEnd; }
    public void setTimeStart(long t) { this.timeStart = t; }
    public void setTimeEnd(long t)   { this.timeEnd = t; }
    public void clearTimeControl()   { this.timeStart = -1; this.timeEnd = -1; }

    // ── Fade ──────────────────────────────────────────────────────────────────

    public int  getFadeInTicks()         { return fadeInTicks; }
    public void setFadeInTicks(int t)    { this.fadeInTicks  = Math.max(0, t); }
    public int  getFadeOutTicks()        { return fadeOutTicks; }
    public void setFadeOutTicks(int t)   { this.fadeOutTicks = Math.max(0, t); }

    // ── Persistencia ──────────────────────────────────────────────────────────

    public void save() {
        FileConfiguration yaml = new YamlConfiguration();
        yaml.set("displayName",  displayName);
        yaml.set("timeStart",    timeStart);
        yaml.set("timeEnd",      timeEnd);
        yaml.set("fadeInTicks",  fadeInTicks);
        yaml.set("fadeOutTicks", fadeOutTicks);

        List<String> frameList = new ArrayList<>(frames.size());
        for (CinematicFrame f : frames) frameList.add(f.serialize());
        yaml.set("frames", frameList);

        List<Map<String, Object>> markerList = new ArrayList<>();
        for (CinematicMarker m : markers) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tick",      m.getTick());
            map.put("titleMain", m.getTitleMain()  != null ? m.getTitleMain()  : "");
            map.put("titleSub",  m.getTitleSub()   != null ? m.getTitleSub()   : "");
            map.put("actionbar", m.getActionbar()  != null ? m.getActionbar()  : "");
            map.put("fadeIn",    m.getTitleFadeIn());
            map.put("stay",      m.getTitleStay());
            map.put("fadeOut",   m.getTitleFadeOut());
            markerList.add(map);
        }
        yaml.set("markers", markerList);

        try { yaml.save(file); }
        catch (IOException e) { System.err.println("[AniEventManager] No se pudo guardar cinematica: " + id); }
    }

    public void load() {
        if (!file.exists()) return;
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        this.displayName  = yaml.getString("displayName", id);
        this.timeStart    = yaml.getLong("timeStart",    -1);
        this.timeEnd      = yaml.getLong("timeEnd",      -1);
        this.fadeInTicks  = yaml.getInt("fadeInTicks",    0);
        this.fadeOutTicks = yaml.getInt("fadeOutTicks",   0);

        frames.clear();
        for (String s : yaml.getStringList("frames")) {
            try { frames.add(CinematicFrame.deserialize(s)); }
            catch (Exception e) { System.err.println("[AniEventManager] Frame inválido en " + id + ": " + s); }
        }

        markers.clear();
        List<?> rawMarkers = yaml.getList("markers");
        if (rawMarkers != null) {
            for (Object raw : rawMarkers) {
                if (!(raw instanceof Map<?, ?> map)) continue;
                try {
                    int tick = toInt(map.get("tick"), 0);
                    if (tick < 0 || tick >= frames.size()) continue;
                    CinematicMarker m = new CinematicMarker(tick);
                    String titleMain = str(map.get("titleMain"));
                    String titleSub  = str(map.get("titleSub"));
                    String actionbar = str(map.get("actionbar"));
                    m.setTitleMain(!titleMain.isBlank() ? titleMain : null);
                    m.setTitleSub( !titleSub.isBlank()  ? titleSub  : null);
                    m.setActionbar(!actionbar.isBlank() ? actionbar : null);
                    m.setTitleFadeIn( toInt(map.get("fadeIn"),  10));
                    m.setTitleStay(   toInt(map.get("stay"),    60));
                    m.setTitleFadeOut(toInt(map.get("fadeOut"), 10));
                    markers.add(m);
                } catch (Exception e) { System.err.println("[AniEventManager] Marker inválido en " + id); }
            }
        }
        markers.sort(Comparator.comparingInt(CinematicMarker::getTick));
    }

    private int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private String str(Object o) { return o != null ? o.toString() : ""; }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String         getId()          { return id; }
    public String         getDisplayName() { return displayName; }
    public void           setDisplayName(String name) { this.displayName = name; }
    public CinematicState getState()       { return state; }
    public void           setState(CinematicState s)  { this.state = s; }
    public File           getFile()        { return file; }
    public boolean        isIdle()         { return state == CinematicState.IDLE; }
    public boolean        isPlaying()      { return state == CinematicState.PLAYING; }
    public boolean        isRecording()    { return state == CinematicState.RECORDING; }
}