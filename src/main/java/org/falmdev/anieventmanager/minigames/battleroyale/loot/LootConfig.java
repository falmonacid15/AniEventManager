package org.falmdev.anieventmanager.minigames.battleroyale.loot;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.falmdev.anieventmanager.Anieventmanager;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Carga y guarda:
 *  - battleroyale-loot.yml   → tablas de loot por tier
 *  - battleroyale-chests.yml → coordenadas escaneadas con su tier
 */
public class LootConfig {

    private final Anieventmanager plugin;

    private File lootFile;
    private FileConfiguration lootYaml;

    private File chestsFile;
    private FileConfiguration chestsYaml;

    private final Map<String, LootTier> tiers = new LinkedHashMap<>();

    // Lista de fases (1-indexed) que disparan refill
    private List<Integer> refillOnShrinkPhases = new ArrayList<>();
    private boolean refillOnGameStart = true;

    public LootConfig(Anieventmanager plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File dir = new File(plugin.getDataFolder(), "minigames");
        dir.mkdirs();

        lootFile   = new File(dir, "battleroyale-loot.yml");
        chestsFile = new File(dir, "battleroyale-chests.yml");

        if (!lootFile.exists()) {
            try {
                lootFile.createNewFile();
                lootYaml = new YamlConfiguration();
                writeLootDefaults();
                lootYaml.save(lootFile);
            } catch (IOException e) {
                plugin.getLogger().severe("[BR-Loot] No se pudo crear " + lootFile.getName());
            }
        }
        if (!chestsFile.exists()) {
            try {
                chestsFile.createNewFile();
                chestsYaml = new YamlConfiguration();
                chestsYaml.save(chestsFile);
            } catch (IOException e) {
                plugin.getLogger().severe("[BR-Loot] No se pudo crear " + chestsFile.getName());
            }
        }

        lootYaml   = YamlConfiguration.loadConfiguration(lootFile);
        chestsYaml = YamlConfiguration.loadConfiguration(chestsFile);

        parseTiers();
        parseRefill();
    }

    public void reload() { load(); }

    private void writeLootDefaults() {
        // refill
        lootYaml.set("refill.on-game-start", true);
        lootYaml.set("refill.on-phase-shrink", List.of(1, 2, 3));

        // COMMON
        lootYaml.set("tiers.COMMON.weight", 60);
        lootYaml.set("tiers.COMMON.color",  "WHITE");
        lootYaml.set("tiers.COMMON.min-items", 2);
        lootYaml.set("tiers.COMMON.max-items", 4);
        lootYaml.set("tiers.COMMON.coins-chance", 100);   // % de chance de dar monedas
        lootYaml.set("tiers.COMMON.coins-min",    5);
        lootYaml.set("tiers.COMMON.coins-max",   15);
        List<Map<String, Object>> commonItems = new ArrayList<>();
        commonItems.add(itemMap("STONE_SWORD",    1, 30));
        commonItems.add(itemMap("LEATHER_HELMET", 1, 20));
        commonItems.add(itemMap("LEATHER_CHESTPLATE", 1, 20));
        commonItems.add(itemMap("BREAD",          3, 40));
        commonItems.add(itemMap("ARROW",          8, 25));
        commonItems.add(itemMap("BOW",            1, 15));
        commonItems.add(itemMap("APPLE",          2, 30));
        lootYaml.set("tiers.COMMON.items", commonItems);

        // RARE
        lootYaml.set("tiers.RARE.weight", 30);
        lootYaml.set("tiers.RARE.color",  "AQUA");
        lootYaml.set("tiers.RARE.min-items", 3);
        lootYaml.set("tiers.RARE.max-items", 5);
        lootYaml.set("tiers.RARE.coins-chance", 100);
        lootYaml.set("tiers.RARE.coins-min",   20);
        lootYaml.set("tiers.RARE.coins-max",   50);
        List<Map<String, Object>> rareItems = new ArrayList<>();
        rareItems.add(itemMap("IRON_SWORD",      1, 25));
        rareItems.add(itemMap("IRON_HELMET",     1, 15));
        rareItems.add(itemMap("IRON_CHESTPLATE", 1, 15));
        rareItems.add(itemMap("IRON_LEGGINGS",   1, 15));
        rareItems.add(itemMap("IRON_BOOTS",      1, 15));
        rareItems.add(itemMap("BOW",             1, 20));
        rareItems.add(itemMap("ARROW",          16, 30));
        rareItems.add(itemMap("GOLDEN_APPLE",    1, 10));
        rareItems.add(itemMap("COOKED_BEEF",     5, 25));
        lootYaml.set("tiers.RARE.items", rareItems);

        // LEGENDARY
        lootYaml.set("tiers.LEGENDARY.weight", 10);
        lootYaml.set("tiers.LEGENDARY.color",  "GOLD");
        lootYaml.set("tiers.LEGENDARY.min-items", 4);
        lootYaml.set("tiers.LEGENDARY.max-items", 6);
        lootYaml.set("tiers.LEGENDARY.coins-chance", 100);
        lootYaml.set("tiers.LEGENDARY.coins-min",    75);
        lootYaml.set("tiers.LEGENDARY.coins-max",   150);
        List<Map<String, Object>> legendaryItems = new ArrayList<>();
        legendaryItems.add(itemMap("DIAMOND_SWORD",       1, 20));
        legendaryItems.add(itemMap("DIAMOND_HELMET",      1, 10));
        legendaryItems.add(itemMap("DIAMOND_CHESTPLATE",  1, 10));
        legendaryItems.add(itemMap("DIAMOND_LEGGINGS",    1, 10));
        legendaryItems.add(itemMap("DIAMOND_BOOTS",       1, 10));
        legendaryItems.add(itemMap("CROSSBOW",            1, 15));
        legendaryItems.add(itemMap("ARROW",              32, 25));
        legendaryItems.add(itemMap("ENCHANTED_GOLDEN_APPLE", 1, 5));
        legendaryItems.add(itemMap("GOLDEN_APPLE",        2, 20));
        legendaryItems.add(itemMap("ENDER_PEARL",         2, 15));
        lootYaml.set("tiers.LEGENDARY.items", legendaryItems);
    }

    private Map<String, Object> itemMap(String material, int amount, int weight) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("material", material);
        m.put("amount",   amount);
        m.put("weight",   weight);
        return m;
    }

    // ── Parseo ────────────────────────────────────────────────────────────────

    public record TierCoinReward(int chance, int min, int max) {}

    private final Map<String, TierCoinReward> tierCoinRewards = new LinkedHashMap<>();

    public TierCoinReward getCoinReward(String tierId) {
        return tierCoinRewards.get(tierId);
    }

    private void parseTiers() {
        tiers.clear();
        tierCoinRewards.clear();
        ConfigurationSection tiersSec = lootYaml.getConfigurationSection("tiers");
        if (tiersSec == null) return;

        for (String tierId : tiersSec.getKeys(false)) {
            ConfigurationSection sec = tiersSec.getConfigurationSection(tierId);
            if (sec == null) continue;

            int    weight   = sec.getInt("weight", 10);
            String colorStr = sec.getString("color", "WHITE");
            Color  color    = colorFromName(colorStr);
            int    minItems = sec.getInt("min-items", 2);
            int    maxItems = sec.getInt("max-items", 4);

            // Coin reward para este tier
            int coinsChance = sec.getInt("coins-chance", 0);
            int coinsMin    = sec.getInt("coins-min", 0);
            int coinsMax    = sec.getInt("coins-max", 0);
            tierCoinRewards.put(tierId, new TierCoinReward(coinsChance, coinsMin, coinsMax));

            List<LootTier.LootEntry> entries = new ArrayList<>();
            List<?> itemsList = sec.getList("items");
            if (itemsList != null) {
                for (Object o : itemsList) {
                    if (!(o instanceof Map<?, ?> m)) continue;
                    String matName = String.valueOf(m.get("material"));
                    Material material;
                    try { material = Material.valueOf(matName.toUpperCase()); }
                    catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("[BR-Loot] Material inválido: " + matName);
                        continue;
                    }
                    int itemAmount = toInt(m.get("amount"), 1);
                    int itemWeight = toInt(m.get("weight"), 10);
                    entries.add(new LootTier.LootEntry(material, itemWeight, itemAmount));
                }
            }

            tiers.put(tierId, new LootTier(tierId, weight, color, minItems, maxItems, entries));
        }

        plugin.getLogger().info("[BR-Loot] Cargados " + tiers.size() + " tiers: " + tiers.keySet());
    }

    private void parseRefill() {
        refillOnGameStart = lootYaml.getBoolean("refill.on-game-start", true);
        refillOnShrinkPhases = new ArrayList<>();
        List<?> phases = lootYaml.getList("refill.on-phase-shrink");
        if (phases != null) {
            for (Object o : phases) {
                refillOnShrinkPhases.add(toInt(o, -1));
            }
            refillOnShrinkPhases.removeIf(i -> i < 1);
        }
    }

    // ── API tiers ─────────────────────────────────────────────────────────────

    public Collection<LootTier> getTiers()         { return tiers.values(); }
    public LootTier             getTier(String id) { return tiers.get(id); }

    public List<Integer> getRefillOnShrinkPhases() { return refillOnShrinkPhases; }
    public boolean       isRefillOnGameStart()     { return refillOnGameStart; }

    // ── Cofres ────────────────────────────────────────────────────────────────

    public List<LootChest> loadChests() {
        List<LootChest> list = new ArrayList<>();
        List<?> raw = chestsYaml.getList("chests");
        if (raw == null) return list;
        for (Object o : raw) {
            if (!(o instanceof Map<?,?> m)) continue;
            String world = String.valueOf(m.get("world"));
            int    x     = toInt(m.get("x"), 0);
            int    y     = toInt(m.get("y"), 0);
            int    z     = toInt(m.get("z"), 0);
            String tier  = String.valueOf(m.get("tier"));
            list.add(new LootChest(world, x, y, z, tier));
        }
        return list;
    }

    public void saveChests(List<LootChest> chests) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LootChest c : chests) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", c.getWorldName());
            m.put("x",     c.getX());
            m.put("y",     c.getY());
            m.put("z",     c.getZ());
            m.put("tier",  c.getTierId());
            list.add(m);
        }
        chestsYaml.set("chests", list);
        try { chestsYaml.save(chestsFile); }
        catch (IOException e) { plugin.getLogger().severe("[BR-Loot] No se pudo guardar chests.yml"); }
    }

    public void clearChests() {
        chestsYaml.set("chests", null);
        try { chestsYaml.save(chestsFile); }
        catch (IOException e) { plugin.getLogger().severe("[BR-Loot] No se pudo limpiar chests.yml"); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private Color colorFromName(String name) {
        return switch (name.toUpperCase()) {
            case "WHITE"    -> Color.WHITE;
            case "GRAY"     -> Color.GRAY;
            case "AQUA"     -> Color.AQUA;
            case "BLUE"     -> Color.BLUE;
            case "GREEN"    -> Color.GREEN;
            case "LIME"     -> Color.LIME;
            case "YELLOW"   -> Color.YELLOW;
            case "ORANGE"   -> Color.ORANGE;
            case "RED"      -> Color.RED;
            case "PURPLE"   -> Color.PURPLE;
            case "GOLD"     -> Color.fromRGB(255, 170, 0);
            case "PINK"     -> Color.FUCHSIA;
            default         -> Color.WHITE;
        };
    }
}