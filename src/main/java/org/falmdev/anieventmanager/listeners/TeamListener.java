package org.falmdev.anieventmanager.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.falmdev.anieventmanager.Anieventmanager;

public class TeamListener implements Listener {

    private final Anieventmanager plugin;

    public TeamListener(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Solo nos interesa jugador golpeando a jugador
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        // Si el friendly fire está activado, no hacemos nada
        if (plugin.getTeamManager().isFriendlyFireEnabled()) return;

        // Si están en el mismo equipo, cancelar el daño
        var attackerTeam = plugin.getTeamManager().getTeamOf(attacker);
        var victimTeam   = plugin.getTeamManager().getTeamOf(victim);

        if (attackerTeam.isPresent()
                && victimTeam.isPresent()
                && attackerTeam.get().getId().equals(victimTeam.get().getId())) {
            event.setCancelled(true);
            attacker.sendActionBar(
                    net.kyori.adventure.text.Component.text(
                            "No puedes atacar a tu compañero de equipo.",
                            net.kyori.adventure.text.format.NamedTextColor.RED
                    )
            );
        }
    }
}