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
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.HashSet;
import java.util.Set;

/**
 * Listener del TNT Run.
 *
 * Mecánica de bloques:
 *   Un tick periódico verifica el bloque bajo los pies de cada jugador.
 *   Al detectar SAND:
 *     1. Se registra en scheduledBlocks para no procesarlo dos veces.
 *     2. Después del delay configurado, se eliminan SAND y TNT juntos.
 *
 *   El sand NO desaparece de inmediato — el jugador tiene el tiempo
 *   del delay para correr antes de que el bloque caiga.
 */
public class TNTRunListener implements Listener {

    private final Anieventmanager plugin;
    private final TNTRunMiniGame miniGame;

    // Claves de bloques ya programados — evita procesarlos más de una vez
    private final Set<Long> scheduledBlocks = new HashSet<>();

    private BukkitTask tickTask;

    public TNTRunListener(Anieventmanager plugin, TNTRunMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
    }

    // ── Tick periódico ────────────────────────────────────────────────────────

    public void startTick() {
        scheduledBlocks.clear();
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

            // Verificar condición de victoria — cubre el caso de
            // 1 jugador en pie o 2 jugadores del mismo equipo vivos
            plugin.getServer().getScheduler().runTask(plugin,
                    miniGame::checkWinCondition);

        }, 0L, 2L);
    }

    public void stopTick() {
        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();
        scheduledBlocks.clear();
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
     * Programa la eliminación del par SAND + TNT después del delay.
     * El sand permanece visible durante el delay — el jugador puede correr.
     * Una vez programado, el bloque se registra en scheduledBlocks para
     * que el tick no lo procese de nuevo antes de que desaparezca.
     */
    private void scheduleRemoval(Block sand) {
        long key = blockKey(sand);
        // Si ya está programado, no hacer nada
        if (!scheduledBlocks.add(key)) return;

        int delay = miniGame.getConfig().getBlockRemoveDelay();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Eliminar sand (si otro proceso ya lo eliminó, ignorar)
            if (sand.getType() == Material.SAND) {
                sand.setType(Material.AIR, false);
            }

            // Eliminar TNT exactamente debajo del sand
            Block tnt = sand.getRelative(0, -1, 0);
            if (tnt.getType() == Material.TNT) {
                tnt.setType(Material.AIR, false);
            }

            scheduledBlocks.remove(key);
        }, delay);
    }

    /**
     * Clave única por coordenada de bloque para el Set.
     */
    private long blockKey(Block block) {
        return ((long)(block.getX() & 0x3FFFFFF) << 38)
                | ((long)(block.getY() & 0xFFF)     << 26)
                |  (long)(block.getZ() & 0x3FFFFFF);
    }
}