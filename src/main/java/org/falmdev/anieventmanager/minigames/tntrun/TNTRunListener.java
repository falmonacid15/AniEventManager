package org.falmdev.anieventmanager.minigames.tntrun;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
 * ── Mecánica de bloques ───────────────────────────────────────────────────────
 *   Un tick periódico detecta el bloque SAND bajo cada jugador activo.
 *   Al detectarlo, programa su eliminación (junto al TNT debajo) tras el delay
 *   configurado. El bloque permanece visible durante el delay — el jugador tiene
 *   tiempo de correr antes de que caiga.
 *
 * ── Doble salto ───────────────────────────────────────────────────────────────
 *   Si {@link TNTRunConfig#isDoubleJumpEnabled()} es true, todos los jugadores
 *   activos reciben {@code setAllowFlight(true)} sin entrar en modo vuelo real.
 *   Al detectar {@link PlayerToggleFlightEvent} se cancela el vuelo, se aplica
 *   impulso vertical y se inicia el cooldown. Tras el cooldown el vuelo se
 *   re-habilita automáticamente.
 *
 *   Cooldown: configurable con /em tntrun setjumpcooldown <segundos>.
 *             Durante el cooldown el jugador ve la barra de acción con el tiempo
 *             restante.
 */
public class TNTRunListener implements Listener {

    // Velocidad vertical del doble salto (bloques/tick aprox.)
    private static final double DOUBLE_JUMP_VELOCITY = 0.9;

    private final Anieventmanager plugin;
    private final TNTRunMiniGame  miniGame;

    /** Claves de bloques ya programados para no procesarlos dos veces. */
    private final Set<Long>          scheduledBlocks = new HashSet<>();
    /** Timestamp (ms) del último doble salto por jugador. */
    private final Map<UUID, Long>    jumpTimestamps  = new HashMap<>();
    /** Task de cooldown activa por jugador (para cancelar si el jugador es eliminado). */
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

        // Habilitar vuelo para doble salto en todos los jugadores activos
        if (miniGame.getConfig().isDoubleJumpEnabled()) {
            plugin.getServer().getOnlinePlayers().stream()
                    .filter(miniGame::isActivePlayer)
                    .forEach(p -> p.setAllowFlight(true));
        }

        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!miniGame.getState().equals(TNTRunMiniGame.State.RUNNING)) return;

            plugin.getServer().getOnlinePlayers().stream()
                    .filter(miniGame::isActivePlayer)
                    .forEach(player -> {
                        Block feet  = player.getLocation().getBlock();
                        Block below = player.getLocation().clone().subtract(0, 1, 0).getBlock();

                        // Eliminación por agua
                        if (feet.getType()  == Material.WATER
                                || below.getType() == Material.WATER) {
                            plugin.getServer().getScheduler().runTask(plugin,
                                    () -> miniGame.eliminatePlayer(player));
                            return;
                        }

                        // Programar eliminación del sand bajo los pies
                        if (below.getType() == Material.SAND) {
                            scheduleRemoval(below);
                        }
                    });

            plugin.getServer().getScheduler().runTask(plugin,
                    miniGame::checkWinCondition);

        }, 0L, 2L);

        // Task de action bar — corre cada segundo para mostrar "salto disponible"
        // a los jugadores que no están en cooldown (los que sí lo están tienen su
        // propio task en startCooldownDisplay que sobreescribe este mensaje)
        if (miniGame.getConfig().isDoubleJumpEnabled()) {
            actionBarTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (!miniGame.getState().equals(TNTRunMiniGame.State.RUNNING)) return;
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
        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();
        if (actionBarTask != null && !actionBarTask.isCancelled()) actionBarTask.cancel();
        scheduledBlocks.clear();

        // Deshabilitar vuelo de todos los jugadores online
        plugin.getServer().getOnlinePlayers().forEach(p -> {
            p.setAllowFlight(false);
            p.setFlying(false);
        });

        // Cancelar todos los tasks de cooldown pendientes
        cooldownTasks.values().forEach(id -> plugin.getServer().getScheduler().cancelTask(id));
        cooldownTasks.clear();
        jumpTimestamps.clear();
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

    /**
     * Intercepta el intento de vuelo del jugador y lo convierte en un doble salto.
     *
     * Paper/Bukkit dispara {@link PlayerToggleFlightEvent} cuando un jugador
     * con {@code allowFlight=true} presiona doble-espacio. Cancelamos el evento
     * (para que no empiece a volar de verdad) y aplicamos un impulso vertical.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        // Solo actuar si el jugador está activo en una partida en curso
        if (!miniGame.isActivePlayer(player)) return;
        if (!miniGame.isRunning()) return;
        if (!miniGame.getConfig().isDoubleJumpEnabled()) return;

        // Solo nos interesa cuando intenta activar el vuelo (doble salto)
        if (!event.isFlying()) return;

        event.setCancelled(true);

        int cooldownSecs = miniGame.getConfig().getDoubleJumpCooldown();
        UUID uuid = player.getUniqueId();

        // Verificar cooldown
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

        // Ejecutar doble salto
        jumpTimestamps.put(uuid, now);
        player.setAllowFlight(false);
        player.setFlying(false);

        // Impulso vertical — preserva la velocidad horizontal actual
        org.bukkit.util.Vector vel = player.getVelocity();
        vel.setY(DOUBLE_JUMP_VELOCITY);
        player.setVelocity(vel);

        sendActionBar(player, "§a✦ ¡Doble salto!");

        // Re-habilitar el vuelo tras el cooldown
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Limpia el estado de doble salto de un jugador cuando es eliminado.
     * Llamado desde {@link TNTRunMiniGame#eliminatePlayer(Player)}.
     */
    public void clearDoubleJumpState(Player player) {
        UUID uuid = player.getUniqueId();
        jumpTimestamps.remove(uuid);
        Integer taskId = cooldownTasks.remove(uuid);
        if (taskId != null) plugin.getServer().getScheduler().cancelTask(taskId);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    /**
     * Programa la eliminación del par SAND + TNT después del delay configurado.
     * El sand permanece visible durante el delay — el jugador puede correr.
     */
    private void scheduleRemoval(Block sand) {
        long key = blockKey(sand);
        if (!scheduledBlocks.add(key)) return;

        int delay = miniGame.getConfig().getBlockRemoveDelay();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (sand.getType() == Material.SAND) {
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
     * Muestra el cooldown del doble salto en la barra de acción y re-habilita
     * el vuelo cuando termina.
     */
    private void startCooldownDisplay(Player player, int totalSeconds) {
        UUID uuid = player.getUniqueId();

        // Cancelar task anterior si existe
        Integer existing = cooldownTasks.remove(uuid);
        if (existing != null) plugin.getServer().getScheduler().cancelTask(existing);

        int[] remaining = { totalSeconds };

        int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // Verificar que el jugador sigue activo
            if (!miniGame.isActivePlayer(player) || !miniGame.isRunning()) {
                Integer id = cooldownTasks.remove(uuid);
                if (id != null) plugin.getServer().getScheduler().cancelTask(id);
                return;
            }

            if (remaining[0] <= 0) {
                // Cooldown terminó — re-habilitar vuelo
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

    /** Genera una barra visual de cooldown en la action bar. */
    private String buildCooldownBar(int remaining, int total) {
        int bars = 10;
        int filled = (int) Math.round(((double)(total - remaining) / total) * bars);
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < bars; i++) {
            bar.append(i < filled ? "§c█" : "§7░");
        }
        bar.append("§8] §f").append(remaining).append("s");
        return bar.toString();
    }

    /** Envía un mensaje a la action bar del jugador. */
    private void sendActionBar(Player player, String message) {
        player.sendActionBar(net.kyori.adventure.text.Component.text(message));
    }

    /** Clave única por coordenada de bloque para el Set de bloques programados. */
    private long blockKey(Block block) {
        return ((long)(block.getX() & 0x3FFFFFF) << 38)
                | ((long)(block.getY() & 0xFFF)     << 26)
                |  (long)(block.getZ() & 0x3FFFFFF);
    }
}