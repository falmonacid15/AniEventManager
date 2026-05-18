package org.falmdev.anieventmanager.minigames.frozenheist;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.falmdev.anieventmanager.Anieventmanager;

import java.io.File;
import java.io.IOException;

/**
 * Configuración del Frozen Heist en:
 * plugins/AniEventManager/minigames/frozenheist.yml
 *
 * Estructura por equipo:
 *
 * settings:
 *   duration-minutes: 10
 *   global-spawn:            ← spawn general antes de que empiece
 *     world: ...  x: y: z: yaw: pitch:
 *
 * teams:
 *   rojo:
 *     base-spawn:            ← respawn dentro de la base
 *     capture-zone:          ← zona donde se entrega la bandera enemiga
 *     flag-stand:            ← posición original de la bandera propia
 *     base-corner-1:         ← esquina 1 de la zona segura (no PvP)
 *     base-corner-2:         ← esquina 2 de la zona segura
 */
public class FrozenHeistConfig {

    private final Anieventmanager plugin;
    private File file;
    private FileConfiguration yaml;

    public FrozenHeistConfig(Anieventmanager plugin) {
        this.plugin = plugin;
        load();
    }

    // ── Carga y guardado ──────────────────────────────────────────────────────

    public void load() {
        File dir = new File(plugin.getDataFolder(), "minigames");
        dir.mkdirs();
        file = new File(dir, "frozenheist.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
                yaml = new YamlConfiguration();
                yaml.set("settings.duration-minutes", 10);
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear frozenheist.yml: " + e.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { yaml.save(file); }
        catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar frozenheist.yml: " + e.getMessage());
        }
    }

    // ── Settings globales ─────────────────────────────────────────────────────

    public int getDurationMinutes() {
        return yaml.getInt("settings.duration-minutes", 10);
    }

    public void setDurationMinutes(int minutes) {
        yaml.set("settings.duration-minutes", minutes);
        save();
    }

    public Location getGlobalSpawn() {
        return readLocation("settings.global-spawn");
    }

    public void setGlobalSpawn(Location loc) {
        writeLocation("settings.global-spawn", loc);
        save();
    }

    // ── Configuración por equipo ──────────────────────────────────────────────

    public Location getBaseSpawn(String teamId) {
        return readLocation("teams." + teamId + ".base-spawn");
    }

    public void setBaseSpawn(String teamId, Location loc) {
        writeLocation("teams." + teamId + ".base-spawn", loc);
        save();
    }

    public Location getCaptureZone(String teamId) {
        return readLocation("teams." + teamId + ".capture-zone");
    }

    public void setCaptureZone(String teamId, Location loc) {
        writeLocation("teams." + teamId + ".capture-zone", loc);
        save();
    }

    public Location getFlagStand(String teamId) {
        return readLocation("teams." + teamId + ".flag-stand");
    }

    public void setFlagStand(String teamId, Location loc) {
        writeLocation("teams." + teamId + ".flag-stand", loc);
        save();
    }

    public Location getBaseCorner1(String teamId) {
        return readLocation("teams." + teamId + ".base-corner-1");
    }

    public void setBaseCorner1(String teamId, Location loc) {
        writeLocation("teams." + teamId + ".base-corner-1", loc);
        save();
    }

    public Location getBaseCorner2(String teamId) {
        return readLocation("teams." + teamId + ".base-corner-2");
    }

    public void setBaseCorner2(String teamId, Location loc) {
        writeLocation("teams." + teamId + ".base-corner-2", loc);
        save();
    }

    /** Aplica la config guardada a un TeamHeistData */
    public void applyToTeamData(String teamId, TeamHeistData data) {
        data.setBaseSpawn(getBaseSpawn(teamId));
        data.setCaptureZone(getCaptureZone(teamId));
        data.setFlagStand(getFlagStand(teamId));
        data.setBaseCorner1(getBaseCorner1(teamId));
        data.setBaseCorner2(getBaseCorner2(teamId));
    }

    // ── Validación ────────────────────────────────────────────────────────────

    public String validate(java.util.Collection<org.falmdev.anieventmanager.model.EventTeam> teams) {
        if (getGlobalSpawn() == null)
            return "El spawn global no está configurado. Usa /em frozenheist setspawn.";
        for (var team : teams) {
            String id = team.getId();
            if (getBaseSpawn(id) == null)
                return "El equipo '" + id + "' no tiene base-spawn. Usa /em frozenheist setbasespawn " + id + ".";
            if (getCaptureZone(id) == null)
                return "El equipo '" + id + "' no tiene capture-zone. Usa /em frozenheist setcapture " + id + ".";
            if (getFlagStand(id) == null)
                return "El equipo '" + id + "' no tiene flag-stand. Usa /em frozenheist setflag " + id + ".";
            if (getBaseCorner1(id) == null || getBaseCorner2(id) == null)
                return "El equipo '" + id + "' no tiene base-corners. Usa /em frozenheist setbase " + id + " 1|2.";
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Location readLocation(String path) {
        if (!yaml.isConfigurationSection(path)) return null;
        World w = Bukkit.getWorld(yaml.getString(path + ".world", ""));
        if (w == null) return null;
        return new Location(w,
                yaml.getDouble(path + ".x"), yaml.getDouble(path + ".y"),
                yaml.getDouble(path + ".z"),
                (float) yaml.getDouble(path + ".yaw"),
                (float) yaml.getDouble(path + ".pitch"));
    }

    private void writeLocation(String path, Location loc) {
        yaml.set(path + ".world", loc.getWorld().getName());
        yaml.set(path + ".x",    loc.getX());
        yaml.set(path + ".y",    loc.getY());
        yaml.set(path + ".z",    loc.getZ());
        yaml.set(path + ".yaw",  (double) loc.getYaw());
        yaml.set(path + ".pitch",(double) loc.getPitch());
    }
}