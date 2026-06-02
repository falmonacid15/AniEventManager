package org.falmdev.anieventmanager.minigames.pvpfinal.kit;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;

import java.io.*;
import java.util.*;

/**
 * KitManager — gestión de kits de PvP.
 *
 * Persistencia: plugins/AniEventManager/minigames/pvpfinal-kits.yml
 *
 * Almacena los ItemStack como Base64 usando ItemStack.serializeAsBytes()
 * (formato nativo Bukkit, preserva NBT, encantamientos, items custom).
 */
public class KitManager {

    private final Anieventmanager  plugin;
    private final Map<String, PvpKit> kits = new HashMap<>();
    private File              file;
    private FileConfiguration yaml;

    public KitManager(Anieventmanager plugin) {
        this.plugin = plugin;
        load();
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public Collection<PvpKit> getAll() { return kits.values(); }
    public PvpKit              get(String name) { return kits.get(name.toLowerCase()); }
    public boolean             exists(String name) { return kits.containsKey(name.toLowerCase()); }
    public int                 count() { return kits.size(); }
    public Set<String>         getNames() { return new TreeSet<>(kits.keySet()); }

    /**
     * Captura el inventario del jugador y lo guarda como kit.
     * Si ya existe, lo sobrescribe.
     */
    public PvpKit createFromPlayer(String name, Player player) {
        PvpKit kit = PvpKit.captureFrom(name, player);
        kits.put(name.toLowerCase(), kit);
        save();
        return kit;
    }

    public boolean delete(String name) {
        if (kits.remove(name.toLowerCase()) == null) return false;
        save();
        return true;
    }

    /**
     * Aplica el kit al jugador: limpia inventario y pone los items del kit.
     */
    public void apply(PvpKit kit, Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        kit.applyTo(player);
    }

    /**
     * Limpia el inventario completo del jugador.
     * Usado al terminar el combate para quitar el kit.
     */
    public void clearInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    // ── Persistencia ──────────────────────────────────────────────────────────

    public void load() {
        File dir = new File(plugin.getDataFolder(), "minigames");
        dir.mkdirs();
        file = new File(dir, "pvpfinal-kits.yml");
        if (!file.exists()) {
            try { file.createNewFile(); }
            catch (IOException e) {
                plugin.getLogger().severe("[PvP] No se pudo crear pvpfinal-kits.yml");
                return;
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        kits.clear();

        if (!yaml.isConfigurationSection("kits")) return;
        for (String key : yaml.getConfigurationSection("kits").getKeys(false)) {
            try {
                PvpKit kit = deserialize(key, yaml.getConfigurationSection("kits." + key));
                if (kit != null) kits.put(key.toLowerCase(), kit);
            } catch (Exception e) {
                plugin.getLogger().warning("[PvP] No se pudo cargar kit '" + key + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("[PvP] Cargados " + kits.size() + " kits.");
    }

    public void save() {
        yaml.set("kits", null);
        for (PvpKit kit : kits.values()) {
            serialize(kit, "kits." + kit.getName().toLowerCase());
        }
        try { yaml.save(file); }
        catch (IOException e) {
            plugin.getLogger().severe("[PvP] No se pudo guardar pvpfinal-kits.yml: " + e.getMessage());
        }
    }

    private void serialize(PvpKit kit, String path) {
        yaml.set(path + ".name", kit.getName());

        // Contents
        List<String> contentsB64 = new ArrayList<>();
        for (ItemStack item : kit.getContents()) {
            contentsB64.add(itemToBase64(item));
        }
        yaml.set(path + ".contents", contentsB64);

        // Armor
        List<String> armorB64 = new ArrayList<>();
        for (ItemStack item : kit.getArmor()) {
            armorB64.add(itemToBase64(item));
        }
        yaml.set(path + ".armor", armorB64);

        // Offhand
        yaml.set(path + ".offhand", itemToBase64(kit.getOffhand()));
    }

    private PvpKit deserialize(String key, org.bukkit.configuration.ConfigurationSection sec) {
        if (sec == null) return null;
        String name = sec.getString("name", key);

        List<String> contentsB64 = sec.getStringList("contents");
        ItemStack[] contents = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            contents[i] = (i < contentsB64.size()) ? itemFromBase64(contentsB64.get(i)) : null;
        }

        List<String> armorB64 = sec.getStringList("armor");
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armor[i] = (i < armorB64.size()) ? itemFromBase64(armorB64.get(i)) : null;
        }

        ItemStack offhand = itemFromBase64(sec.getString("offhand"));

        return new PvpKit(name, contents, armor, offhand);
    }

    private String itemToBase64(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return "";
        byte[] bytes = item.serializeAsBytes();
        return Base64.getEncoder().encodeToString(bytes);
    }

    private ItemStack itemFromBase64(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            plugin.getLogger().warning("[PvP] No se pudo deserializar item: " + e.getMessage());
            return null;
        }
    }
}