package org.falmdev.anieventmanager.managers;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {

    private static final NamedTextColor[] COLORS = {
            NamedTextColor.RED,
            NamedTextColor.BLUE,
            NamedTextColor.GREEN,
            NamedTextColor.YELLOW,
            NamedTextColor.LIGHT_PURPLE,
            NamedTextColor.AQUA,
            NamedTextColor.GOLD,
            NamedTextColor.WHITE
    };

    private final Anieventmanager plugin;
    private final LinkedHashMap<String, EventTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, String> playerTeamMap = new HashMap<>();
    private int colorIndex = 0;

    // Friendly fire global — desactivado por defecto
    private boolean friendlyFire = false;

    private File dataFile;
    private FileConfiguration dataConfig;

    public TeamManager(Anieventmanager plugin) {
        this.plugin = plugin;
        setupFile();
        load();
    }

    // ── Archivo YAML ──────────────────────────────────────────────────────────

    private void setupFile() {
        dataFile = new File(plugin.getDataFolder(), "teams.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear teams.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        dataConfig = new YamlConfiguration();
        dataConfig.set("colorIndex", colorIndex);
        dataConfig.set("friendlyFire", friendlyFire);

        for (EventTeam team : teams.values()) {
            String path = "teams." + team.getId();
            dataConfig.set(path + ".displayName", team.getDisplayName());
            dataConfig.set(path + ".color", team.getColor().toString());

            List<String> memberUuids = team.getMembers()
                    .stream()
                    .map(UUID::toString)
                    .toList();
            dataConfig.set(path + ".members", memberUuids);
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar teams.yml: " + e.getMessage());
        }
    }

    private void load() {
        teams.clear();
        playerTeamMap.clear();

        colorIndex   = dataConfig.getInt("colorIndex", 0);
        friendlyFire = dataConfig.getBoolean("friendlyFire", false);

        if (!dataConfig.isConfigurationSection("teams")) return;

        var section = dataConfig.getConfigurationSection("teams");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String displayName = dataConfig.getString("teams." + id + ".displayName", id);
            String colorStr    = dataConfig.getString("teams." + id + ".color", "white");

            NamedTextColor color = parseColor(colorStr);
            EventTeam team = new EventTeam(id, displayName, color);

            List<String> memberUuids = dataConfig.getStringList("teams." + id + ".members");
            for (String uuidStr : memberUuids) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    team.addMember(uuid);
                    playerTeamMap.put(uuid, id);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("UUID invalido en teams.yml para equipo " + id + ": " + uuidStr);
                }
            }

            teams.put(id, team);
        }

        plugin.getLogger().info("Equipos cargados: " + teams.size()
                + " | Friendly fire: " + (friendlyFire ? "activado" : "desactivado"));
    }

    // ── Friendly fire ─────────────────────────────────────────────────────────

    public boolean isFriendlyFireEnabled() {
        return friendlyFire;
    }

    public void setFriendlyFire(boolean enabled) {
        this.friendlyFire = enabled;
        save();
    }

    // ── Creacion ──────────────────────────────────────────────────────────────

    public EventTeam createTeam(String id, String displayName) {
        String key = id.toLowerCase();
        if (teams.containsKey(key)) return null;

        NamedTextColor color = COLORS[colorIndex % COLORS.length];
        colorIndex++;

        EventTeam team = new EventTeam(key, displayName, color);
        teams.put(key, team);
        save();

        plugin.getScoreManager().initTeam(team);
        return team;
    }

    public boolean deleteTeam(String id) {
        EventTeam team = teams.remove(id.toLowerCase());
        if (team == null) return false;

        team.getMembers().forEach(playerTeamMap::remove);
        save();

        plugin.getScoreManager().removeTeam(id.toLowerCase());
        return true;
    }

    public void clearAll() {
        teams.clear();
        playerTeamMap.clear();
        colorIndex = 0;
        save();

        plugin.getScoreManager().resetAll();
    }

    // ── Asignacion de jugadores ───────────────────────────────────────────────

    public boolean addToTeam(String teamId, Player player) {
        EventTeam team = teams.get(teamId.toLowerCase());
        if (team == null) return false;
        if (team.isFull()) return false;

        removeFromCurrentTeam(player);

        team.addMember(player.getUniqueId());
        playerTeamMap.put(player.getUniqueId(), teamId.toLowerCase());
        save();
        return true;
    }

    public boolean removeFromCurrentTeam(Player player) {
        String teamId = playerTeamMap.remove(player.getUniqueId());
        if (teamId == null) return false;
        EventTeam team = teams.get(teamId);
        if (team != null) team.removeMember(player.getUniqueId());
        save();
        return true;
    }

    // ── Consultas ─────────────────────────────────────────────────────────────

    public Optional<EventTeam> getTeam(String id) {
        return Optional.ofNullable(teams.get(id.toLowerCase()));
    }

    public Optional<EventTeam> getTeamOf(Player player) {
        String teamId = playerTeamMap.get(player.getUniqueId());
        return Optional.ofNullable(teamId != null ? teams.get(teamId) : null);
    }

    public boolean isInTeam(Player player) {
        return playerTeamMap.containsKey(player.getUniqueId());
    }

    public Collection<EventTeam> getAllTeams() {
        return Collections.unmodifiableCollection(teams.values());
    }

    public Set<String> getTeamIds() {
        return Collections.unmodifiableSet(teams.keySet());
    }

    public int getTeamCount() {
        return teams.size();
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private NamedTextColor parseColor(String colorStr) {
        return switch (colorStr) {
            case "red"          -> NamedTextColor.RED;
            case "blue"         -> NamedTextColor.BLUE;
            case "green"        -> NamedTextColor.GREEN;
            case "yellow"       -> NamedTextColor.YELLOW;
            case "light_purple" -> NamedTextColor.LIGHT_PURPLE;
            case "aqua"         -> NamedTextColor.AQUA;
            case "gold"         -> NamedTextColor.GOLD;
            default             -> NamedTextColor.WHITE;
        };
    }
}