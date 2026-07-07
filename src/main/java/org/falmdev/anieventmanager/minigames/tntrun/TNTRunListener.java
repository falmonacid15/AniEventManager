package org.falmdev.anieventmanager.minigames.tntrun;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TNTRunListener implements Listener {

    private static final double DOUBLE_JUMP_VELOCITY = 0.9;

    private final Anieventmanager plugin;
    private final TNTRunMiniGame  miniGame;

    private final Set<Long>          scheduledBlocks = new HashSet<>();
    private final Map<UUID, Long>    jumpTimestamps  = new HashMap<>();
    private final Map<UUID, Integer> cooldownTasks   = new HashMap<>();



    private BukkitTask tickTask;
    private BukkitTask actionBarTask;

    public TNTRunListener(Anieventmanager plugin, TNTRunMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
    }

    public void startTick() {
        scheduledBlocks.clear();
        jumpTimestamps.clear();


        if (miniGame.getConfig().isDoubleJumpEnabled()) {
            plugin.getServer().getOnlinePlayers().stream()
                    .filter(miniGame::isActivePlayer)
                    .forEach(p -> p.setAllowFlight(true));
        }

        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!miniGame.isStrictlyRunning()) return;

            plugin.getServer().getOnlinePlayers().stream()
                    .filter(miniGame::isActivePlayer)
                    .forEach(player -> {
                        Location loc  = player.getLocation();
                        Block feet    = loc.getBlock();
                        Block below   = loc.clone().subtract(0, 1, 0).getBlock();

                        if (feet.getType() == Material.WATER || below.getType() == Material.WATER) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (player.isOnline() && miniGame.isActivePlayer(player)) {
                                    miniGame.eliminatePlayer(player);
                                }
                            });
                            return;
                        }

                        scheduleIfSand(below);

                        double px = loc.getX() - Math.floor(loc.getX());
                        double pz = loc.getZ() - Math.floor(loc.getZ());
                        int bx = below.getX();
                        int by = below.getY();
                        int bz = below.getZ();

                        if (px < 0.35) {
                            scheduleIfSand(below.getWorld().getBlockAt(bx - 1, by, bz));
                        } else if (px > 0.65) {
                            scheduleIfSand(below.getWorld().getBlockAt(bx + 1, by, bz));
                        }

                        if (pz < 0.35) {
                            scheduleIfSand(below.getWorld().getBlockAt(bx, by, bz - 1));
                        } else if (pz > 0.65) {
                            scheduleIfSand(below.getWorld().getBlockAt(bx, by, bz + 1));
                        }
                    });

            miniGame.checkWinCondition();

        }, 0L, 2L);

        if (miniGame.getConfig().isDoubleJumpEnabled()) {
            actionBarTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (!miniGame.isStrictlyRunning()) return;
                long now = System.currentTimeMillis();
                int cooldownMs = miniGame.getConfig().getDoubleJumpCooldown() * 1000;

                plugin.getServer().getOnlinePlayers().stream()
                        .filter(miniGame::isActivePlayer)
                        .filter(p -> !cooldownTasks.containsKey(p.getUniqueId()))
                        .forEach(p -> {
                            Long lastJump = jumpTimestamps.get(p.getUniqueId());
                            boolean ready = lastJump == null || (now - lastJump) >= cooldownMs;
                            if (ready) sendActionBar(p, "§a✦ Doble salto disponible");
                        });
            }, 0L, 20L);
        }
    }

    public void stopTick() {
        if (tickTask      != null && !tickTask.isCancelled())      tickTask.cancel();
        if (actionBarTask != null && !actionBarTask.isCancelled()) actionBarTask.cancel();

        scheduledBlocks.clear();

        plugin.getServer().getOnlinePlayers().forEach(p -> {
            if (p.getGameMode() != GameMode.SPECTATOR) {
                p.setAllowFlight(false);
                p.setFlying(false);
            }
        });

        cooldownTasks.values().forEach(id -> plugin.getServer().getScheduler().cancelTask(id));
        cooldownTasks.clear();
        jumpTimestamps.clear();

        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!miniGame.isActivePlayer(player)) return;
        if (!miniGame.isCountingDown()) return;

        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;

        if (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(from.clone().setDirection(to.getDirection()));
        }
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        if (!miniGame.isActivePlayer(player)) return;
        if (!miniGame.isStrictlyRunning()) return;
        if (!miniGame.getConfig().isDoubleJumpEnabled()) return;
        if (!event.isFlying()) return;

        event.setCancelled(true);

        int cooldownSecs = miniGame.getConfig().getDoubleJumpCooldown();
        UUID uuid = player.getUniqueId();

        Long lastJump = jumpTimestamps.get(uuid);
        long now = System.currentTimeMillis();
        if (lastJump != null) {
            long elapsed    = now - lastJump;
            long cooldownMs = cooldownSecs * 1000L;
            if (elapsed < cooldownMs) {
                long remaining = (cooldownMs - elapsed + 999) / 1000;
                sendActionBar(player, "§c⏳ Doble salto en §f" + remaining + "s");
                return;
            }
        }

        jumpTimestamps.put(uuid, now);
        player.setAllowFlight(false);
        player.setFlying(false);

        org.bukkit.util.Vector vel = player.getVelocity();
        vel.setY(DOUBLE_JUMP_VELOCITY);
        player.setVelocity(vel);

        sendActionBar(player, "§a✦ ¡Doble salto!");

        if (cooldownSecs > 0) {
            startCooldownDisplay(player, cooldownSecs);
        } else {
            player.setAllowFlight(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!miniGame.isActivePlayer(player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    public void clearDoubleJumpState(Player player) {
        UUID uuid = player.getUniqueId();
        jumpTimestamps.remove(uuid);
        Integer taskId = cooldownTasks.remove(uuid);
        if (taskId != null) plugin.getServer().getScheduler().cancelTask(taskId);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    public int getJumpCooldownPercent(UUID uuid) {
        if (!miniGame.getConfig().isDoubleJumpEnabled()) return 0;
        int cooldownMs = miniGame.getConfig().getDoubleJumpCooldown() * 1000;
        if (cooldownMs <= 0) return 0;
        Long lastJump = jumpTimestamps.get(uuid);
        if (lastJump == null) return 0;
        long elapsed = System.currentTimeMillis() - lastJump;
        if (elapsed >= cooldownMs) return 0;
        return (int)(100 - (elapsed * 100L / cooldownMs));
    }

    private void scheduleIfSand(Block block) {
        if (block != null && block.getType() == Material.SAND) {
            scheduleRemoval(block);
        }
    }


    private void scheduleRemoval(Block sand) {
        long key = blockKey(sand);
        if (!scheduledBlocks.add(key)) return;

        int delay = miniGame.getConfig().getBlockRemoveDelay();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (sand.getType() == Material.SAND) {
                spawnBreakParticle(sand);
                sand.setType(Material.AIR, false);
            }
            Block tnt = sand.getRelative(0, -1, 0);
            if (tnt.getType() == Material.TNT) {
                tnt.setType(Material.AIR, false);
            }
            scheduledBlocks.remove(key);
        }, delay);
    }


    private void spawnBreakParticle(Block block) {
        Location  center   = block.getLocation().add(0.5, 0.5, 0.5);
        BlockData data     = block.getBlockData();
        Particle  particle = resolveBlockParticle();
        if (particle == null) return;

        try {
            block.getWorld().spawnParticle(particle, center, 20, 0.3, 0.3, 0.3, 0.1, data);
        } catch (Exception ignored) {

        }
    }

    private static Particle resolveBlockParticle() {
        try { return Particle.valueOf("BLOCK_CRUMBLE"); } catch (IllegalArgumentException ignored) {}
        try { return Particle.valueOf("BLOCK_CRACK");   } catch (IllegalArgumentException ignored) {}
        try { return Particle.valueOf("BLOCK");         } catch (IllegalArgumentException ignored) {}
        return null;
    }

    private void startCooldownDisplay(Player player, int totalSeconds) {
        UUID uuid = player.getUniqueId();

        Integer existing = cooldownTasks.remove(uuid);
        if (existing != null) plugin.getServer().getScheduler().cancelTask(existing);

        int[] remaining = { totalSeconds };

        int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!miniGame.isActivePlayer(player) || !miniGame.isStrictlyRunning()) {
                Integer id = cooldownTasks.remove(uuid);
                if (id != null) plugin.getServer().getScheduler().cancelTask(id);
                return;
            }

            if (remaining[0] <= 0) {
                player.setAllowFlight(true);
                sendActionBar(player, "§a✦ Doble salto listo");
                Integer id = cooldownTasks.remove(uuid);
                if (id != null) plugin.getServer().getScheduler().cancelTask(id);
                return;
            }

            sendActionBar(player, buildCooldownBar(remaining[0], totalSeconds));
            remaining[0]--;
        }, 0L, 20L).getTaskId();

        cooldownTasks.put(uuid, taskId);
    }

    private String buildCooldownBar(int remaining, int total) {
        int bars   = 10;
        int filled = (int) Math.round(((double)(total - remaining) / total) * bars);
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < bars; i++) {
            bar.append(i < filled ? "§c█" : "§7░");
        }
        bar.append("§8] §f").append(remaining).append("s");
        return bar.toString();
    }

    private void sendActionBar(Player player, String message) {
        player.sendActionBar(net.kyori.adventure.text.Component.text(message));
    }

    private long blockKey(Block block) {
        return ((long)(block.getX() & 0x3FFFFFF) << 38)
                | ((long)(block.getY() & 0xFFF)     << 26)
                |  (long)(block.getZ() & 0x3FFFFFF);
    }
}