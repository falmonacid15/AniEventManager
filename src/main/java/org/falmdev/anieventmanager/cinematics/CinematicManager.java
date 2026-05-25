package org.falmdev.anieventmanager.cinematics;

import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.cinematics.model.Cinematic;
import org.falmdev.anieventmanager.cinematics.model.CinematicState;

import java.io.File;
import java.util.*;

/**
 * Registro central de cinematicas.
 *
 * FIX Bug 3: loadAll() ya NO se llama en el constructor.
 * Se llama desde el ServerLoadEvent en Anieventmanager (cuando los mundos
 * ya están cargados). Esto resuelve el problema de que LocationUtil.fromMap()
 * llame Bukkit.getWorld() y reciba null porque el mundo todavía no está
 * disponible durante el onEnable().
 *
 * Cambio necesario en Anieventmanager.java:
 *   En el ServerLoadEvent, ANTES de refreshAll(), agregar:
 *     cinematicManager.loadAll();
 */
public class CinematicManager {

    private final Anieventmanager plugin;
    private final File cinematicsDir;

    private final LinkedHashMap<String, Cinematic> cinematics = new LinkedHashMap<>();

    private final CinematicEffects  effects;
    private final CinematicPlayer   player;
    private final CinematicRecorder recorder;

    public CinematicManager(Anieventmanager plugin) {
        this.plugin        = plugin;
        this.cinematicsDir = new File(plugin.getDataFolder(), "cinematics");
        cinematicsDir.mkdirs();

        this.effects  = new CinematicEffects(plugin);
        this.player   = new CinematicPlayer(plugin, effects);
        this.recorder = new CinematicRecorder(plugin);

        // NO llamamos loadAll() aquí — los mundos no están cargados todavía.
        // loadAll() se llama desde Anieventmanager.onServerLoad()
    }

    // ── Carga ─────────────────────────────────────────────────────────────────

    /**
     * Carga todas las cinematicas desde disco.
     * Llamar SOLO desde ServerLoadEvent, cuando los mundos ya están disponibles.
     */
    public void loadAll() {
        cinematics.clear();
        File[] files = cinematicsDir.listFiles(f -> f.getName().endsWith(".yml"));
        if (files == null) return;

        int loaded = 0;
        int failed = 0;
        for (File f : files) {
            String id = f.getName().replace(".yml", "").toLowerCase();
            Cinematic c = new Cinematic(id, id, f);
            c.load();
            // Verificar que tenga al menos el displayName y no esté corrupta
            cinematics.put(id, c);
            if (c.getWaypoints().isEmpty() && !isEmptyByDesign(f)) {
                plugin.getLogger().warning("[CinematicManager] '" + id
                        + "' cargó sin waypoints — puede que el mundo no esté disponible.");
                failed++;
            } else {
                loaded++;
            }
        }
        plugin.getLogger().info("Cinematicas: " + loaded + " cargadas"
                + (failed > 0 ? ", " + failed + " con problemas" : ""));
    }

    /** Detecta si el archivo YAML está vacío intencionalmente (cinematica recién creada). */
    private boolean isEmptyByDesign(File f) {
        try {
            return f.length() < 50; // archivo casi vacío = recién creada
        } catch (Exception e) {
            return false;
        }
    }

    public void reloadAll() {
        for (Cinematic c : cinematics.values()) {
            if (!c.isPlaying()) c.load();
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public Cinematic create(String id, String displayName) {
        String key = id.toLowerCase();
        if (cinematics.containsKey(key)) return null;
        File f = new File(cinematicsDir, key + ".yml");
        Cinematic c = new Cinematic(key, displayName, f);
        c.save();
        cinematics.put(key, c);
        return c;
    }

    public boolean delete(String id) {
        Cinematic c = cinematics.get(id.toLowerCase());
        if (c == null) return false;
        if (c.isPlaying()) return false;
        if (c.isRecording()) recorder.stopRecording();
        c.getFile().delete();
        cinematics.remove(id.toLowerCase());
        return true;
    }

    public Optional<Cinematic> get(String id) {
        return Optional.ofNullable(cinematics.get(id.toLowerCase()));
    }

    public Collection<Cinematic> getAllCinematics() {
        return Collections.unmodifiableCollection(cinematics.values());
    }

    public Set<String> getIds() {
        return Collections.unmodifiableSet(cinematics.keySet());
    }

    // ── Reproducción ─────────────────────────────────────────────────────────

    public boolean play(String id) {
        Cinematic c = cinematics.get(id.toLowerCase());
        if (c == null) {
            plugin.getLogger().warning("[CinematicManager] play(): cinematica '" + id
                    + "' no encontrada. IDs disponibles: " + cinematics.keySet());
            return false;
        }
        if (c.getWaypoints().size() < 2) {
            plugin.getLogger().warning("[CinematicManager] play(): '"
                    + id + "' tiene " + c.getWaypoints().size()
                    + " waypoints (mínimo 2).");
            return false;
        }
        if (c.isPlaying()) return false;
        if (player.isPlaying()) return false;

        c.setState(CinematicState.PLAYING);
        player.play(c, () -> c.setState(CinematicState.IDLE));
        return true;
    }

    public boolean stop() {
        if (!player.isPlaying()) return false;
        player.stop();
        return true;
    }

    public boolean isAnyPlaying() { return player.isPlaying(); }

    public Optional<Cinematic> getCurrentlyPlaying() {
        return player.getCurrentCinematic();
    }

    // ── Grabación ─────────────────────────────────────────────────────────────

    public CinematicEffects  getEffects()  { return effects; }
    public CinematicPlayer   getPlayer()   { return player; }
    public CinematicRecorder getRecorder() { return recorder; }
}