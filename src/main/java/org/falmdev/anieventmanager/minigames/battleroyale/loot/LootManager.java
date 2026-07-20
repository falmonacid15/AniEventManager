package org.falmdev.anieventmanager.minigames.battleroyale.loot;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleConfig;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleMiniGame;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class LootManager {

    private static final double PARTICLE_RADIUS    = 0.8;
    private static final double PARTICLE_VIEW_DIST = 32.0;
    private static final int    PARTICLE_INTERVAL  = 5;
    private static final int    PARTICLES_PER_RING = 8;
    private static final double ROTATION_PER_TICK  = Math.PI / 30.0;

    private final Anieventmanager      plugin;
    private final BattleRoyaleConfig   brConfig;
    private final BattleRoyaleMiniGame game;
    private final LootConfig           lootConfig;

    private final List<LootChest> chests = new ArrayList<>();
    private LootListener listener = null;
    private BukkitTask particleTask = null;
    private double particleAngle    = 0;

    public LootManager(Anieventmanager plugin, BattleRoyaleMiniGame game) {
        this.plugin     = plugin;
        this.brConfig   = game.getConfig();
        this.game       = game;
        this.lootConfig = new LootConfig(plugin);
        this.chests.addAll(lootConfig.loadChests());
        plugin.getLogger().info("[BR-Loot] Cargados " + chests.size() + " cofres registrados.");
    }

    public LootConfig getLootConfig()      { return lootConfig; }
    public void       setListener(LootListener l) { this.listener = l; }
    public List<LootChest> getChests()     { return Collections.unmodifiableList(chests); }
    public int getChestCount()             { return chests.size(); }

    public Map<String, Integer> getTierDistribution() {
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (LootChest c : chests) {
            dist.merge(c.getTierId(), 1, Integer::sum);
        }
        return dist;
    }

    public ScanResult scan() {
        Location p1 = brConfig.getArenaPos1();
        Location p2 = brConfig.getArenaPos2();
        if (p1 == null || p2 == null) {
            return new ScanResult(false, "Pos1/pos2 no configurados.", 0, Map.of());
        }
        if (lootConfig.getTiers().isEmpty()) {
            return new ScanResult(false, "No hay tiers cargados.", 0, Map.of());
        }

        World world = p1.getWorld();
        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());
        int minY = brConfig.getYMin();
        int maxY = brConfig.getYMax();

        plugin.getLogger().info(String.format(
                "[BR-Loot] Escaneando: x=[%d,%d] y=[%d,%d] z=[%d,%d] en mundo '%s'",
                minX, maxX, minY, maxY, minZ, maxZ, world.getName()));

        List<LootChest> found = new ArrayList<>();
        int scanned = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    scanned++;
                    Block block = world.getBlockAt(x, y, z);
                    if (isLootableContainer(block)) {
                        String tier = pickRandomTier();
                        found.add(new LootChest(world.getName(), x, y, z, tier));
                    }
                }
            }
        }

        chests.clear();
        chests.addAll(found);
        lootConfig.saveChests(chests);

        Map<String, Integer> dist = getTierDistribution();
        plugin.getLogger().info("[BR-Loot] Escaneo completo: " + scanned + " bloques revisados, "
                + found.size() + " cofres encontrados. Distribución: " + dist);

        return new ScanResult(true, "OK", found.size(), dist);
    }

    private boolean isLootableContainer(Block block) {
        Material mat = block.getType();
        if (mat != Material.CHEST && mat != Material.TRAPPED_CHEST) {
            return false;
        }

        BlockState state = block.getState(false);
        return state instanceof Container;
    }

    private String pickRandomTier() {
        Collection<LootTier> all = lootConfig.getTiers();
        int totalWeight = all.stream().mapToInt(LootTier::weight).sum();
        if (totalWeight <= 0) return all.iterator().next().id();
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int acc = 0;
        for (LootTier t : all) {
            acc += t.weight();
            if (roll < acc) return t.id();
        }
        return all.iterator().next().id();
    }

    public void clearChests() {
        chests.clear();
        lootConfig.clearChests();
        plugin.getLogger().info("[BR-Loot] Registro de cofres limpiado.");
    }

    public RefillResult refill() {
        if (listener != null) listener.clearOpenedChests();

        int filled = 0, skipped = 0;
        for (LootChest c : chests) {
            World world = Bukkit.getWorld(c.getWorldName());
            if (world == null) { skipped++; continue; }
            Block block = world.getBlockAt(c.getX(), c.getY(), c.getZ());
            BlockState bs = block.getState(false);
            if (!(bs instanceof Container container)) { skipped++; continue; }

            Inventory inv = container.getInventory();
            inv.clear();

            LootTier tier = lootConfig.getTier(c.getTierId());
            if (tier == null) { skipped++; continue; }
            fillInventory(inv, tier);
            filled++;
        }
        plugin.getLogger().info("[BR-Loot] Refill: " + filled + " cofres rellenados, " + skipped + " saltados.");
        return new RefillResult(filled, skipped);
    }

    public int emptyAll() {
        int emptied = 0;
        for (LootChest c : chests) {
            World world = Bukkit.getWorld(c.getWorldName());
            if (world == null) continue;
            Block block = world.getBlockAt(c.getX(), c.getY(), c.getZ());
            BlockState bs = block.getState(false);
            if (!(bs instanceof Container container)) continue;
            container.getInventory().clear();
            emptied++;
        }
        plugin.getLogger().info("[BR-Loot] " + emptied + " cofres vaciados.");
        return emptied;
    }

    private void fillInventory(Inventory inv, LootTier tier) {
        if (tier.items().isEmpty()) return;

        int count = ThreadLocalRandom.current().nextInt(
                tier.minItems(), tier.maxItems() + 1);

        List<Integer> usedSlots = new ArrayList<>();
        int invSize = inv.getSize();

        for (int i = 0; i < count; i++) {
            LootTier.LootEntry entry = pickWeightedItem(tier);
            if (entry == null) continue;

            ItemStack item = new ItemStack(entry.material(), entry.amount());

            int slot;
            int tries = 0;
            do {
                slot = ThreadLocalRandom.current().nextInt(invSize);
                tries++;
            } while (usedSlots.contains(slot) && tries < 50);

            usedSlots.add(slot);
            inv.setItem(slot, item);
        }
    }

    private LootTier.LootEntry pickWeightedItem(LootTier tier) {
        int totalWeight = tier.items().stream().mapToInt(LootTier.LootEntry::weight).sum();
        if (totalWeight <= 0) return null;
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int acc = 0;
        for (LootTier.LootEntry e : tier.items()) {
            acc += e.weight();
            if (roll < acc) return e;
        }
        return tier.items().get(0);
    }

    public void startParticles() {
        if (particleTask != null) return;
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin,
                this::drawAllParticles, PARTICLE_INTERVAL, PARTICLE_INTERVAL);
    }

    public void stopParticles() {
        if (particleTask != null) { particleTask.cancel(); particleTask = null; }
    }

    private void drawAllParticles() {
        if (chests.isEmpty()) return;
        particleAngle += ROTATION_PER_TICK;
        if (particleAngle > Math.PI * 2) particleAngle -= Math.PI * 2;

        Map<String, Particle.DustOptions> dustCache = new HashMap<>();

        for (LootChest chest : chests) {
            if (listener != null && listener.isOpened(chest)) continue;
            World world = Bukkit.getWorld(chest.getWorldName());
            if (world == null) continue;

            LootTier tier = lootConfig.getTier(chest.getTierId());
            if (tier == null) continue;

            Particle.DustOptions dust = dustCache.computeIfAbsent(chest.getTierId(),
                    id -> new Particle.DustOptions(tier.particleColor(), 1.2f));

            Location center = chest.centerLocation(world);

            boolean anyClose = false;
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distanceSquared(center) < PARTICLE_VIEW_DIST * PARTICLE_VIEW_DIST) {
                    anyClose = true;
                    break;
                }
            }
            if (!anyClose) continue;

            for (int i = 0; i < PARTICLES_PER_RING; i++) {
                double angle = particleAngle + (i * 2 * Math.PI / PARTICLES_PER_RING);
                double dx = Math.cos(angle) * PARTICLE_RADIUS;
                double dz = Math.sin(angle) * PARTICLE_RADIUS;
                world.spawnParticle(Particle.DUST,
                        center.getX() + dx,
                        center.getY() - 0.5,
                        center.getZ() + dz,
                        1, 0, 0, 0, 0, dust);
            }
        }
    }

    public record ScanResult(boolean ok, String message, int chestsFound, Map<String, Integer> distribution) {}
    public record RefillResult(int filled, int skipped) {}
}