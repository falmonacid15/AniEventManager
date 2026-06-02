package org.falmdev.anieventmanager.minigames.battleroyale.drop;

import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleMiniGame;
import org.falmdev.anieventmanager.minigames.battleroyale.model.BRPlayer;

/**
 * Listener específico del módulo de drop.
 *
 * Maneja:
 *  - Shift para saltar del dragón
 *  - Cancelar daño del dragón a los pasajeros
 *  - Cancelar caída durante el paracaídas
 */
public class DropListener implements Listener {

    private final BattleRoyaleMiniGame game;

    public DropListener(BattleRoyaleMiniGame game) {
        this.game = game;
    }

    /**
     * Shift mientras está montado en el dragón → saltar.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        if (!game.isRunning()) return;

        Player player = event.getPlayer();
        BRPlayer brp = game.getBRPlayer(player);
        if (brp == null) return;

        if (brp.isOnDragon()) {
            game.getDropSystem().jumpOff(player);
        }
    }

    /**
     * Cancelar todo daño mientras el jugador está en el dragón o planeando.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!game.isRunning()) return;

        BRPlayer brp = game.getBRPlayer(player);
        if (brp == null) return;

        // Sin daño mientras está en el dragón o planeando
        if (brp.isOnDragon() || brp.isParachuting()) {
            event.setCancelled(true);
        }
    }

    /**
     * Cancelar daño causado por el dragón a cualquier jugador del juego.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDragonDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof EnderDragon)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!game.isRunning()) return;

        // Si el jugador está en nuestro juego, cancelar el daño
        if (game.getBRPlayer(player) != null) {
            event.setCancelled(true);
        }
    }
}