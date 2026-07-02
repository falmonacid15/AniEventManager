package org.falmdev.anieventmanager.minigames.battleroyale.drop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleConfig;
import org.falmdev.anieventmanager.minigames.battleroyale.model.BRPlayer;

import java.util.*;

public class DropSystem {

    private static final String PARACHUTE_TAG = "br_parachute";
    private static final int    GHAST_CAPACITY = 4;
    private static final double GHAST_SPACING  = 6.0; // bloques entre ghasts

    private final Anieventmanager    plugin;
    private final BattleRoyaleConfig config;

    private Runnable    onAllLanded;
    private BukkitTask  moveTask = null;
    private BukkitTask  landTask = null;

    private final List<HappyGhast>    ghasts = new ArrayList<>();
    private final Map<UUID, HappyGhast> playerGhast = new HashMap<>(); // jugador → su ghast

    private Location currentPos = null;
    private Vector   stepVec    = null;
    private Vector   sideVec    = null; // vector perpendicular para formación
    private float    flightYaw  = 0f;

    private final Map<UUID, BRPlayer> brPlayers = new LinkedHashMap<>();
    private int pendingLanding = 0;

    public DropSystem(Anieventmanager plugin, BattleRoyaleConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean isActive() { return !ghasts.isEmpty(); }

    // ── Inicio ────────────────────────────────────────────────────────────────

    public void start(Map<UUID, BRPlayer> players, Runnable onAllLanded) {
        this.onAllLanded = onAllLanded;
        this.brPlayers.clear();
        this.brPlayers.putAll(players);

        List<Player> onlinePlayers = brPlayers.values().stream()
                .map(brp -> Bukkit.getPlayer(brp.getUuid()))
                .filter(p -> p != null && p.isOnline())
                .toList();

        this.pendingLanding = onlinePlayers.size();

        if (onlinePlayers.isEmpty()) {
            plugin.getLogger().warning("[BR] No hay jugadores online para el drop.");
            if (onAllLanded != null) Bukkit.getScheduler().runTask(plugin, onAllLanded);
            return;
        }

        Location startLoc = config.getDropStart();
        Location endLoc   = config.getDropEnd();
        if (startLoc == null || endLoc == null) {
            plugin.getLogger().severe("[BR] dropStart o dropEnd no configurados.");
            if (onAllLanded != null) Bukkit.getScheduler().runTask(plugin, onAllLanded);
            return;
        }

        final double height = config.getDropHeight();
        currentPos = startLoc.clone();
        currentPos.setY(height);

        // Dirección automática start → end
        double dx = endLoc.getX() - startLoc.getX();
        double dz = endLoc.getZ() - startLoc.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            plugin.getLogger().warning("[BR] start y end son el mismo punto.");
            if (onAllLanded != null) Bukkit.getScheduler().runTask(plugin, onAllLanded);
            return;
        }

        final double speed = config.getDropSpeed() * 2;
        stepVec = new Vector(dx / len * speed, 0, dz / len * speed);

        // Vector perpendicular para spacing entre ghasts (en plano XZ)
        sideVec = new Vector(-stepVec.getZ(), 0, stepVec.getX()).normalize();

        // Yaw para que los ghasts miren hacia adelante
        flightYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        currentPos.setYaw(flightYaw);
        currentPos.setPitch(0);

        plugin.getLogger().info(String.format(
                "[BR] Drop: %d jugadores  start=(%.0f,%.0f,%.0f) end=(%.0f,%.0f,%.0f)",
                onlinePlayers.size(),
                startLoc.getX(), height, startLoc.getZ(),
                endLoc.getX(),   height, endLoc.getZ()));

        // Calcular cuántos ghasts necesitamos
        int needed = (int) Math.ceil(onlinePlayers.size() / (double) GHAST_CAPACITY);
        plugin.getLogger().info("[BR] Spawneando " + needed + " HappyGhasts.");

        // Spawnear ghasts en formación
        for (int i = 0; i < needed; i++) {
            // Offset desde el centro: -1.5, -0.5, 0.5, 1.5 para 4; -0.5, 0.5 para 2; etc
            double offset = (i - (needed - 1) / 2.0) * GHAST_SPACING;
            Location ghastLoc = currentPos.clone().add(sideVec.clone().multiply(offset));
            ghastLoc.setYaw(flightYaw);

            HappyGhast ghast = (HappyGhast) ghastLoc.getWorld().spawnEntity(ghastLoc, EntityType.HAPPY_GHAST);
            ghast.setAI(false);
            ghast.setInvulnerable(true);
            ghast.setSilent(true);
            ghast.setRemoveWhenFarAway(false);
            ghast.setGravity(false);

            ghasts.add(ghast);
        }

        // Asignar jugadores a ghasts (4 por ghast)
        int idx = 0;
        for (Player p : onlinePlayers) {
            BRPlayer brp = brPlayers.get(p.getUniqueId());
            if (brp == null) continue;

            int ghastIdx = idx / GHAST_CAPACITY;
            if (ghastIdx >= ghasts.size()) ghastIdx = ghasts.size() - 1;

            HappyGhast ghast = ghasts.get(ghastIdx);

            brp.setState(BRPlayer.State.ON_DRAGON); // reutilizamos el estado
            brp.setDragonSeatIndex(idx % GHAST_CAPACITY);

            // ADVENTURE + flight para que pueda mirar libremente
            p.setGameMode(GameMode.ADVENTURE);
            p.setAllowFlight(true);

            ghast.addPassenger(p);
            playerGhast.put(p.getUniqueId(), ghast);

            p.sendMessage(Component.text("▼ Presioná ", NamedTextColor.YELLOW)
                    .append(Component.text("SHIFT", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" para saltar y planear.", NamedTextColor.YELLOW)));

            idx++;
        }

        // Precalcular para la actionbar
        final double totalDist = len;
        final Location endFixed = endLoc.clone();
        endFixed.setY(height);

        // ── Task de movimiento ────────────────────────────────────────────────
        moveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (ghasts.isEmpty()) { stopMove(); return; }

            double remX = endFixed.getX() - currentPos.getX();
            double remZ = endFixed.getZ() - currentPos.getZ();
            double distToEnd = Math.sqrt(remX * remX + remZ * remZ);

            if (distToEnd < speed * 2) {
                forceJumpAll();
                return;
            }

            currentPos.add(stepVec);
            currentPos.setY(height);
            currentPos.setYaw(flightYaw);

            // Mover cada ghast a su posición en la formación
            for (int i = 0; i < ghasts.size(); i++) {
                HappyGhast g = ghasts.get(i);
                if (!g.isValid()) continue;
                double offset = (i - (ghasts.size() - 1) / 2.0) * GHAST_SPACING;
                Location gPos = currentPos.clone().add(sideVec.clone().multiply(offset));
                gPos.setYaw(flightYaw);
                g.teleport(gPos);
            }

            Component actionbar = Component.text(
                    String.format("⬇ SHIFT para saltar  |  %.0f bloques restantes", distToEnd),
                    NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false);

            for (BRPlayer brp : brPlayers.values()) {
                if (!brp.isOnDragon()) continue;
                Player p = Bukkit.getPlayer(brp.getUuid());
                if (p != null && p.isOnline()) p.sendActionBar(actionbar);
            }

        }, 2L, 2L);

        // ── Task de aterrizaje ────────────────────────────────────────────────
        landTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (BRPlayer brp : brPlayers.values()) {
                if (!brp.isParachuting()) continue;
                Player p = Bukkit.getPlayer(brp.getUuid());
                if (p == null || !p.isOnline()) continue;
                if (p.isOnGround()) {
                    land(p, brp);
                } else {
                    p.sendActionBar(Component.text(
                            String.format("⬇ Planeando  |  Altura: %.0f", p.getLocation().getY()),
                            NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                }
            }
        }, 0L, 1L);
    }

    // ── Salto ─────────────────────────────────────────────────────────────────

    public void jumpOff(Player player) {
        BRPlayer brp = brPlayers.get(player.getUniqueId());
        if (brp == null || !brp.isOnDragon()) return;

        brp.setState(BRPlayer.State.PARACHUTING);

        // Posición del ghast donde va el jugador
        HappyGhast ghast = playerGhast.get(player.getUniqueId());
        Location jumpLoc;
        if (ghast != null && ghast.isValid()) {
            jumpLoc = ghast.getLocation().clone();
        } else {
            jumpLoc = currentPos.clone();
        }
        jumpLoc.setYaw(player.getLocation().getYaw());
        jumpLoc.setPitch(0);

        // Desmontar
        if (ghast != null) ghast.removePassenger(player);
        playerGhast.remove(player.getUniqueId());

        // SURVIVAL en la posición del ghast
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.teleport(jumpLoc);

        equipParachute(player);

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_FALLING,
                config.getParachuteTicks(),
                config.getParachuteSFAmplifier(),
                false, false, false));

        // Impulso hacia adelante con un poco hacia abajo para activar elytra
        Vector forward = jumpLoc.getDirection();
        forward.setY(-0.05);
        player.setVelocity(forward.multiply(1.2));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.setGliding(true);
        }, 1L);

        player.sendMessage(Component.text(
                "▼ Planeando — mirá hacia donde querés aterrizar.", NamedTextColor.AQUA));
    }

    public void forceJumpAll() {
        stopMove();
        for (BRPlayer brp : new ArrayList<>(brPlayers.values())) {
            if (!brp.isOnDragon()) continue;
            Player p = Bukkit.getPlayer(brp.getUuid());
            if (p != null && p.isOnline()) {
                p.sendMessage(Component.text(
                        "¡Fin del recorrido! Saltando automáticamente.", NamedTextColor.RED));
                jumpOff(p);
            }
        }
        despawnGhasts();
    }

    // ── Aterrizaje ────────────────────────────────────────────────────────────

    private void land(Player player, BRPlayer brp) {
        if (brp.hasLanded()) return;
        brp.setHasLanded(true);
        brp.setState(BRPlayer.State.ALIVE);
        brp.setLastGroundLocation(player.getLocation().clone());

        ItemStack chest = player.getInventory().getChestplate();
        if (isParachuteElytra(chest)) player.getInventory().setChestplate(null);

        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        player.setGliding(false);
        player.setAllowFlight(false);

        player.sendMessage(Component.text("✔ Aterrizaste. ¡Buscá loot!", NamedTextColor.GREEN));
        player.sendActionBar(Component.empty());

        pendingLanding--;
        if (pendingLanding <= 0) {
            stopLand();
            despawnGhasts(); // limpiar ghasts si quedaron
            if (onAllLanded != null)
                Bukkit.getScheduler().runTask(plugin, onAllLanded);
        }
    }

    // ── Stop / cleanup ────────────────────────────────────────────────────────

    public void stop() {
        stopMove();
        stopLand();
        despawnGhasts();
        brPlayers.clear();
        playerGhast.clear();
        currentPos = null;
        stepVec    = null;
        sideVec    = null;
    }

    private void stopMove() {
        if (moveTask != null && !moveTask.isCancelled()) { moveTask.cancel(); moveTask = null; }
    }

    private void stopLand() {
        if (landTask != null && !landTask.isCancelled()) { landTask.cancel(); landTask = null; }
    }

    private void despawnGhasts() {
        for (HappyGhast g : ghasts) {
            if (g != null && g.isValid()) {
                for (Entity e : g.getPassengers()) {
                    g.removePassenger(e);
                    if (e instanceof Player p) {
                        p.setGameMode(GameMode.SURVIVAL);
                        p.setAllowFlight(false);
                    }
                }
                g.remove();
            }
        }
        ghasts.clear();
        playerGhast.clear();
    }

    // ── Elytra ────────────────────────────────────────────────────────────────

    private void equipParachute(Player player) {
        ItemStack elytra = buildParachuteElytra();
        ItemStack current = player.getInventory().getChestplate();
        if (current != null && current.getType() != Material.AIR
                && !isParachuteElytra(current)) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(current);
            if (!leftover.isEmpty())
                player.getWorld().dropItem(player.getLocation(), current);
        }
        player.getInventory().setChestplate(elytra);
    }

    private ItemStack buildParachuteElytra() {
        ItemStack item = new ItemStack(Material.ELYTRA);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text("Paracaídas")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(PARACHUTE_TAG, NamedTextColor.BLACK)
                .decoration(TextDecoration.ITALIC, false)));
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isParachuteElytra(ItemStack item) {
        if (item == null || item.getType() != Material.ELYTRA) return false;
        if (!item.hasItemMeta() || item.getItemMeta().lore() == null) return false;
        return item.getItemMeta().lore().stream().anyMatch(c ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(c).equals(PARACHUTE_TAG));
    }

    public Location getCurrentPos() { return currentPos; }
}