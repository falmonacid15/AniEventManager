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

public class DropListener implements Listener {

    private final BattleRoyaleMiniGame game;

    public DropListener(BattleRoyaleMiniGame game) {
        this.game = game;
    }

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

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!game.isRunning()) return;

        BRPlayer brp = game.getBRPlayer(player);
        if (brp == null) return;

        if (brp.isOnDragon() || brp.isParachuting()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDragonDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof EnderDragon)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!game.isRunning()) return;

        if (game.getBRPlayer(player) != null) {
            event.setCancelled(true);
        }
    }
}