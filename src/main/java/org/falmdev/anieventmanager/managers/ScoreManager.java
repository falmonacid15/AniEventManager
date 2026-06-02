package org.falmdev.anieventmanager.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ScoreManager {

    private final Anieventmanager plugin;

    // teamId -> puntaje total
    private final Map<String, Integer> scores = new LinkedHashMap<>();

    private File dataFile;

    public ScoreManager(Anieventmanager plugin) {
        this.plugin = plugin;
        setupFile();
        load();
    }

    public void reload() {
        load();
    }

    // ── Archivo YAML ──────────────────────────────────────────────────────────

    private void setupFile() {
        dataFile = new File(plugin.getDataFolder(), "scores.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear scores.yml: " + e.getMessage());
            }
        }
    }

    private void save() {
        FileConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            yaml.set("scores." + entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar scores.yml: " + e.getMessage());
        }
    }

    private void load() {
        scores.clear();
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        if (!yaml.isConfigurationSection("scores")) return;

        var section = yaml.getConfigurationSection("scores");
        if (section == null) return;

        // Obtener los ids de equipos que actualmente existen
        Set<String> existingTeamIds = plugin.getTeamManager().getTeamIds();

        boolean hadOrphans = false;
        for (String teamId : section.getKeys(false)) {
            if (existingTeamIds.contains(teamId)) {
                scores.put(teamId, yaml.getInt("scores." + teamId, 0));
            } else {
                // Equipo eliminado — ignorar y marcar para limpiar
                hadOrphans = true;
                plugin.getLogger().info("Puntaje huerfano eliminado: " + teamId);
            }
        }

        // Si había entradas huérfanas, guardar el archivo limpio
        if (hadOrphans) save();

        plugin.getLogger().info("Puntajes cargados: " + scores.size() + " equipos.");
    }

    // ── Operaciones de puntaje ────────────────────────────────────────────────

    /**
     * Agrega puntos a un equipo. Si el equipo no tiene puntaje registrado aun, lo inicializa en 0.
     */
    public void addScore(EventTeam team, int points) {
        scores.merge(team.getId(), points, Integer::sum);
        save();
    }

    /**
     * Resta puntos a un equipo. El puntaje minimo es 0.
     */
    public void removeScore(EventTeam team, int points) {
        int current = scores.getOrDefault(team.getId(), 0);
        scores.put(team.getId(), Math.max(0, current - points));
        save();
    }

    /**
     * Setea el puntaje de un equipo a un valor exacto.
     */
    public void setScore(EventTeam team, int points) {
        scores.put(team.getId(), Math.max(0, points));
        save();
    }

    /**
     * Resetea el puntaje de un equipo a 0.
     */
    public void resetScore(EventTeam team) {
        scores.put(team.getId(), 0);
        save();
    }

    /**
     * Resetea los puntajes de todos los equipos a 0.
     */
    public void resetAll() {
        scores.replaceAll((id, v) -> 0);
        save();
    }

    /**
     * Elimina el registro de puntaje de un equipo.
     * Se usa cuando el equipo es eliminado.
     */
    public void removeTeam(String teamId) {
        scores.remove(teamId);
        save();
    }

    /**
     * Inicializa el puntaje de un equipo en 0 si no existe aun.
     * Se llama al crear un equipo nuevo.
     */
    public void initTeam(EventTeam team) {
        scores.putIfAbsent(team.getId(), 0);
        save();
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    public int getScore(EventTeam team) {
        return scores.getOrDefault(team.getId(), 0);
    }

    public int getScore(String teamId) {
        return scores.getOrDefault(teamId, 0);
    }

    /**
     * Devuelve todos los equipos ordenados por puntaje de mayor a menor.
     * Cada entrada es: teamId -> puntaje
     */
    public List<Map.Entry<String, Integer>> getLeaderboard() {
        return scores.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .toList();
    }

    /**
     * Devuelve la posicion de un equipo en el ranking (base 1).
     * Si hay empate, todos comparten la misma posicion.
     * Devuelve -1 si el equipo no tiene puntaje registrado.
     */
    public int getRank(EventTeam team) {
        int teamScore = scores.getOrDefault(team.getId(), -1);
        if (teamScore == -1) return -1;

        int rank = 1;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > teamScore) rank++;
        }
        return rank;
    }

    /**
     * Devuelve el teamId del equipo en la posicion indicada (base 1).
     * Devuelve null si no hay equipo en esa posicion.
     */
    public String getTeamIdAtRank(int rank) {
        List<Map.Entry<String, Integer>> lb = getLeaderboard();
        if (rank < 1 || rank > lb.size()) return null;
        return lb.get(rank - 1).getKey();
    }

    /**
     * Devuelve el puntaje mas alto registrado.
     */
    public int getTopScore() {
        return scores.values().stream().max(Integer::compare).orElse(0);
    }
}