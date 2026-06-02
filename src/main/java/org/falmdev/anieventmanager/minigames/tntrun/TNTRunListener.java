package org.falmdev.anieventmanager.minigames.tntrun;

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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Listener del TNT Run.
 *
 * ── Fixes aplicados ───────────────────────────────────────────────────────────
 *
 *  1. BLOCK_CRACK → BLOCK_CRUMBLE (Paper 1.20.5+) con fallback a BLOCK para
 *     versiones anteriores. El nombre cambia entre versiones y una excepción
 *     silenciosa en la línea de partículas cortaba la ejecución del bloque
 *     entero, impidiendo que el SAND se convirtiera en AIR.
 *
 *  2. unregisterAll al finalizar: finish() y forceStop() ahora desregistran
 *     este listener para que no se acumulen listeners de partidas anteriores.
 *     Los listeners acumulados causaban que el countdown de una partida nueva
 *     bloqueara el movimiento con la lógica del countdown de la partida vieja.
 *
 *  3. El tick usa isStrictlyRunning() en vez de comparar el enum directamente,
 *     para que sea consistente con el resto del código.
 *
 *  4. checkWinCondition ya NO se llama con runTask separado dentro del tick
 *     (que podía generar race conditions con el estado). Ahora se llama
 *     directamente al final del tick en el hilo principal del scheduler.
 */
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

    // ── Tick periódico ────────────────────────────────────────────────────────

    public void startTick() {
        scheduledBlocks.clear();
        jumpTimestamps.clear();

        if (miniGame.getConfig().isDoubleJumpEnabled()) {
            plugin.getServer().getOnlinePlayers().stream()
                    .filter(miniGame::isActivePlayer)
                    .forEach(p -> p.setAllowFlight(true));
        }

        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // FIX: usar isStrictlyRunning() — solo procesar bloques cuando el
            // juego está realmente en curso, no durante el countdown
            if (!miniGame.isStrictlyRunning()) return;

            plugin.getServer().getOnlinePlayers().stream()
                    .filter(miniGame::isActivePlayer)
                    .forEach(player -> {
                        Location loc  = player.getLocation();
                        Block feet    = loc.getBlock();
                        Block below   = loc.clone().subtract(0, 1, 0).getBlock();

                        // Eliminación por agua
                        if (feet.getType() == Material.WATER || below.getType() == Material.WATER) {
                            plugin.getServer().getScheduler().runTask(plugin,
                                    () -> miniGame.eliminatePlayer(player));
                            return;
                        }

                        // Bloque central bajo los pies
                        scheduleIfSand(below);

                        // Bloques adyacentes para movimiento diagonal
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

            // FIX: checkWinCondition directo, sin runTask extra
            // El tick ya corre en el hilo principal — el runTask adicional
            // introducía un tick de delay donde el estado podía desincronizarse
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
        if (tickTask    != null && !tickTask.isCancelled())    tickTask.cancel();
        if (actionBarTask != null && !actionBarTask.isCancelled()) actionBarTask.cancel();
        scheduledBlocks.clear();

        plugin.getServer().getOnlinePlayers().forEach(p -> {
            p.setAllowFlight(false);
            p.setFlying(false);
        });

        cooldownTasks.values().forEach(id -> plugin.getServer().getScheduler().cancelTask(id));
        cooldownTasks.clear();
        jumpTimestamps.clear();

        // FIX: desregistrar el listener para que no se acumule entre partidas
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    // ── Congelar durante countdown ────────────────────────────────────────────

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

    // ── Doble salto ───────────────────────────────────────────────────────────

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
            long elapsed = now - lastJump;
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

    // ── Daño por agua ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!miniGame.isActivePlayer(player)) return;

        boolean isElimination =
                event.getCause() == EntityDamageEvent.DamageCause.DROWNING  ||
                        event.getCause() == EntityDamageEvent.DamageCause.LAVA      ||
                        event.getCause() == EntityDamageEvent.DamageCause.FIRE      ||
                        event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK;

        if (isElimination) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> miniGame.eliminatePlayer(player));
        }
    }

    // ── Helpers públicos ──────────────────────────────────────────────────────

    public void clearDoubleJumpState(Player player) {
        UUID uuid = player.getUniqueId();
        jumpTimestamps.remove(uuid);
        Integer taskId = cooldownTasks.remove(uuid);
        if (taskId != null) plugin.getServer().getScheduler().cancelTask(taskId);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private void scheduleIfSand(Block block) {
        if (block != null && block.getType() == Material.SAND) {
            scheduleRemoval(block);
        }
    }

    /**
     * Programa la eliminación del par SAND + TNT después del delay configurado.
     *
     * FIX de partículas: Particle.BLOCK_CRACK fue renombrado en diferentes
     * versiones de Paper. Usamos un try/catch para intentar BLOCK_CRUMBLE
     * (1.20.5+) y caer a BLOCK si no existe, evitando que una excepción
     * silenciosa interrumpa la eliminación del bloque.
     */
    private void scheduleRemoval(Block sand) {
        long key = blockKey(sand);
        if (!scheduledBlocks.add(key)) return;

        int delay = miniGame.getConfig().getBlockRemoveDelay();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (sand.getType() == Material.SAND) {
                // Partículas — con fallback por compatibilidad de versiones
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

    /**
     * Spawnea partículas de ruptura de bloque con compatibilidad entre versiones.
     * Paper 1.20.5+ renombró BLOCK_CRACK → BLOCK_CRUMBLE.
     * Versiones anteriores usan BLOCK_CRACK o simplemente BLOCK.
     * Si ninguna funciona, se ignora silenciosamente para no romper la mecánica.
     */
    private void spawnBreakParticle(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        BlockData data  = block.getBlockData();

        // Intentar en orden de preferencia según versión de Paper
        Particle particle = resolveBlockParticle();
        if (particle == null) return;

        try {
            block.getWorld().spawnParticle(particle, center, 20, 0.3, 0.3, 0.3, 0.1, data);
        } catch (Exception ignored) {
            // Si falla por cualquier razón, no interrumpir la eliminación del bloque
        }
    }

    /**
     * Detecta el nombre de partícula correcto en tiempo de ejecución.
     * Prueba BLOCK_CRUMBLE (1.20.5+), luego BLOCK_CRACK (pre-1.20.5).
     */
    private static Particle resolveBlockParticle() {
        // Intentar BLOCK_CRUMBLE primero (Paper 1.20.5+)
        try {
            return Particle.valueOf("BLOCK_CRUMBLE");
        } catch (IllegalArgumentException ignored) {}

        // Fallback: BLOCK_CRACK (Paper pre-1.20.5 / Spigot)
        try {
            return Particle.valueOf("BLOCK_CRACK");
        } catch (IllegalArgumentException ignored) {}

        // Fallback final: BLOCK (algunos builds intermedios)
        try {
            return Particle.valueOf("BLOCK");
        } catch (IllegalArgumentException ignored) {}

        return null; // no hay partícula disponible, omitir
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

    private void sendActionBar(Player player, String message) {
        player.sendActionBar(net.kyori.adventure.text.Component.text(message));
    }

    private long blockKey(Block block) {
        return ((long)(block.getX() & 0x3FFFFFF) << 38)
                | ((long)(block.getY() & 0xFFF)     << 26)
                |  (long)(block.getZ() & 0x3FFFFFF);
    }
}