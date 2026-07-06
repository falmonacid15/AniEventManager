package org.falmdev.anieventmanager.minigames.pvpfinal.combat;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.pvpfinal.PvpFinalMiniGame;
import org.falmdev.anieventmanager.minigames.pvpfinal.model.CombatState;

import java.util.UUID;


public class CombatListener implements Listener {

    private final Anieventmanager    plugin;
    private final PvpFinalMiniGame   game;

    public CombatListener(Anieventmanager plugin, PvpFinalMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Combat combat = game.getCombatManager().getCurrentCombat();
        if (combat == null) return;
        if (!combat.isParticipant(event.getPlayer().getUniqueId())) return;

        CombatState state = game.getCombatManager().getState();
        if (state != CombatState.PREPARING && state != CombatState.COUNTDOWN) return;

        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getZ() != event.getTo().getZ()
                || event.getFrom().getY() != event.getTo().getY()) {
            event.setTo(event.getFrom().clone()
                    .setDirection(event.getTo().getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Combat combat = game.getCombatManager().getCurrentCombat();
        if (combat == null) return;
        if (!combat.isParticipant(victim.getUniqueId())) return;

        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof org.bukkit.entity.Firework) {
            event.setCancelled(true);
            return;
        }

        CombatState state = game.getCombatManager().getState();

        if (state == CombatState.PREPARING || state == CombatState.COUNTDOWN
                || state == CombatState.ENDING) {
            event.setCancelled(true);
            return;
        }

        if (!combat.isAlive(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event);
        if (attacker == null) return;

        Combat combat = game.getCombatManager().getCurrentCombat();
        if (combat == null) return;

        UUID vUuid = victim.getUniqueId();
        UUID aUuid = attacker.getUniqueId();
        if (!combat.isParticipant(vUuid) || !combat.isParticipant(aUuid)) return;

        if (!combat.canDamage(aUuid, vUuid)) {
            event.setCancelled(true);
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        var damager = event.getDamager();
        if (damager instanceof Player p) return p;
        if (damager instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Combat combat = game.getCombatManager().getCurrentCombat();
        if (combat == null) return;
        if (!combat.isParticipant(victim.getUniqueId())) return;

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);

        Player killer = victim.getKiller();

        game.getCombatManager().onParticipantDeath(victim, killer);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!game.getCombatManager().isAwaitingCombatRespawn(player.getUniqueId())) return;
        game.getCombatManager().handleCombatRespawn(player, event);
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        Combat combat = game.getCombatManager().getCurrentCombat();
        if (combat == null) return;
        if (!combat.isParticipant(event.getPlayer().getUniqueId())) return;
        CombatState state = game.getCombatManager().getState();
        if (state == CombatState.PREPARING || state == CombatState.COUNTDOWN
                || state == CombatState.FIGHTING) {
            event.setCancelled(true);
        }
    }

}