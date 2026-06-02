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
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.pvpfinal.PvpFinalMiniGame;
import org.falmdev.anieventmanager.minigames.pvpfinal.model.CombatState;

import java.util.UUID;

/**
 * CombatListener — gestiona eventos durante el combate.
 *
 * - Cancela daño durante PREPARING y COUNTDOWN
 * - Cancela movimiento durante PREPARING y COUNTDOWN
 * - Aplica regla de friendly fire en daño PvP durante FIGHTING
 * - Cancela drop de items al morir
 * - Detecta muerte y notifica al CombatManager
 */
public class CombatListener implements Listener {

    private final Anieventmanager    plugin;
    private final PvpFinalMiniGame   game;

    public CombatListener(Anieventmanager plugin, PvpFinalMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    // ── Movimiento durante preparación/countdown ──────────────────────────────

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Combat combat = game.getCombatManager().getCurrentCombat();
        if (combat == null) return;
        if (!combat.isParticipant(event.getPlayer().getUniqueId())) return;

        CombatState state = game.getCombatManager().getState();
        if (state != CombatState.PREPARING && state != CombatState.COUNTDOWN) return;

        // Solo cancelar movimiento en X/Z (permitir mirar)
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getZ() != event.getTo().getZ()
                || event.getFrom().getY() != event.getTo().getY()) {
            event.setTo(event.getFrom().clone()
                    .setDirection(event.getTo().getDirection()));
        }
    }

    // ── Daño durante el combate ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Combat combat = game.getCombatManager().getCurrentCombat();
        if (combat == null) return;
        if (!combat.isParticipant(victim.getUniqueId())) return;

        CombatState state = game.getCombatManager().getState();

        // Inmunes durante preparación/countdown
        if (state == CombatState.PREPARING || state == CombatState.COUNTDOWN) {
            event.setCancelled(true);
            return;
        }

        // Si está muerto en el combate (spectator), no recibe daño
        if (!combat.isAlive(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        // Resolver al atacante real (proyectil → shooter, etc.)
        Player attacker = resolveAttacker(event);
        if (attacker == null) return;

        Combat combat = game.getCombatManager().getCurrentCombat();
        if (combat == null) return;

        UUID vUuid = victim.getUniqueId();
        UUID aUuid = attacker.getUniqueId();

        // Ambos deben ser parte del combate
        if (!combat.isParticipant(vUuid) || !combat.isParticipant(aUuid)) return;

        // Si están en mismo equipo y NO hay friendly fire → cancelar
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

    // ── Muerte ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Combat combat = game.getCombatManager().getCurrentCombat();
        if (combat == null) return;
        if (!combat.isParticipant(victim.getUniqueId())) return;

        // No dropear items
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);

        // Killer (puede ser null si fue por entorno)
        Player killer = victim.getKiller();

        // Notificar al manager — él se encarga de respawn, spectator, win check
        game.getCombatManager().onParticipantDeath(victim, killer);
    }

    // Cancelar drops manuales también
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