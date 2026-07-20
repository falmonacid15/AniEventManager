package org.falmdev.anieventmanager.cinematics;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.cinematics.model.Cinematic;
import org.falmdev.anieventmanager.cinematics.model.CinematicState;

import java.io.File;
import java.util.*;

public class CinematicManager {

    private final Anieventmanager plugin;
    private final File cinematicsDir;

    private final LinkedHashMap<String, Cinematic> cinematics = new LinkedHashMap<>();

    private final Map<String, String> cinematicWorlds = new HashMap<>();

    private final CinematicEffects  effects;
    private final CinematicPlayer   player;
    private final CinematicRecorder recorder;
    private final CinematicSpectators spectators;

    public CinematicManager(Anieventmanager plugin) {
        this.plugin        = plugin;
        this.cinematicsDir = new File(plugin.getDataFolder(), "cinematics");
        cinematicsDir.mkdirs();

        this.spectators = new CinematicSpectators();
        this.effects  = new CinematicEffects(plugin);
        this.player   = new CinematicPlayer(plugin, effects, spectators);
        this.recorder = new CinematicRecorder(plugin);
    }

    public void loadAll() {
        cinematics.clear();
        cinematicWorlds.clear();

        File[] files = cinematicsDir.listFiles(f ->
                f.getName().endsWith(".yml") && !f.getName().startsWith("_"));
        if (files == null) return;

        for (File f : files) {
            String id = f.getName().replace(".yml", "").toLowerCase();
            Cinematic c = new Cinematic(id, id, f);
            c.load();
            cinematics.put(id, c);

            // Cargar el mundo desde el archivo .world
            File worldFile = new File(cinematicsDir, id + ".world");
            if (worldFile.exists()) {
                try {
                    String worldName = new String(
                            java.nio.file.Files.readAllBytes(worldFile.toPath())).trim();
                    cinematicWorlds.put(id, worldName);
                } catch (Exception e) {
                    plugin.getLogger().warning("[CinematicManager] No se pudo leer el mundo de "
                            + id + ": " + e.getMessage());
                }
            }
        }

        plugin.getLogger().info("Cinematicas cargadas: " + cinematics.size());
    }

    public void reloadAll() {
        for (Cinematic c : cinematics.values()) {
            if (!c.isPlaying()) c.load();
        }
    }

    public World getCinematicWorld(String id) {
        String worldName = cinematicWorlds.get(id.toLowerCase());
        if (worldName == null) return null;
        return org.bukkit.Bukkit.getWorld(worldName);
    }

    public void setCinematicWorld(String id, World world) {
        cinematicWorlds.put(id.toLowerCase(), world.getName());
        File worldFile = new File(cinematicsDir, id.toLowerCase() + ".world");
        try {
            java.nio.file.Files.writeString(worldFile.toPath(), world.getName());
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicManager] No se pudo guardar el mundo de "
                    + id + ": " + e.getMessage());
        }
    }

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

        new File(cinematicsDir, id.toLowerCase() + ".world").delete();
        cinematics.remove(id.toLowerCase());
        cinematicWorlds.remove(id.toLowerCase());
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

    public boolean startRecording(org.bukkit.entity.Player admin,
                                  Cinematic cinematic, int durationTicks) {
        return recorder.startRecording(admin, cinematic, durationTicks);
    }

    public boolean play(String id) {
        Cinematic c = cinematics.get(id.toLowerCase());
        if (c == null) {
            plugin.getLogger().warning("[CinematicManager] play(): '" + id
                    + "' no encontrada.");
            return false;
        }
        if (c.getTotalFrames() < 1) {
            plugin.getLogger().warning("[CinematicManager] play(): '" + id
                    + "' no tiene frames grabados.");
            return false;
        }
        if (c.isPlaying() || player.isPlaying()) return false;

        c.setState(CinematicState.PLAYING);
        player.play(c, () -> c.setState(CinematicState.IDLE));
        return true;
    }

    public boolean playDebug(String id, Player admin) {
        Cinematic c = cinematics.get(id.toLowerCase());
        if (c == null || c.getTotalFrames() < 1) return false;
        if (c.isPlaying() || player.isPlaying()) return false;
        c.setState(CinematicState.PLAYING);
        player.playDebug(c, admin, () -> c.setState(CinematicState.IDLE));
        return true;
    }

    public boolean stop() {
        if (!player.isPlaying()) return false;
        player.stop();
        return true;
    }

    public boolean isAnyPlaying()                    { return player.isPlaying(); }
    public Optional<Cinematic> getCurrentlyPlaying() { return player.getCurrentCinematic(); }
    public CinematicEffects  getEffects()            { return effects; }
    public CinematicPlayer   getPlayer()             { return player; }
    public CinematicRecorder getRecorder()           { return recorder; }
    public CinematicSpectators getSpectators() { return spectators; }
}