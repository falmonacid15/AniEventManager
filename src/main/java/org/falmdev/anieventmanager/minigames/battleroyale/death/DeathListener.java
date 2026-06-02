package org.falmdev.anieventmanager.minigames.battleroyale.death;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleMiniGame;
import org.falmdev.anieventmanager.minigames.battleroyale.model.BRPlayer;

/**
 * DeathListener — gestiona la muerte de jugadores en el Battle Royale.
 *
 * - Detecta muerte vía PlayerDeathEvent
 * - Identifica killer si fue PvP
 * - Items se dropean en el suelo (comportamiento default de Bukkit)
 * - El jugador queda en SPECTATOR exactamente donde murió
 * - Broadcast del evento de muerte
 * - Delega a BattleRoyaleMiniGame.killPlayer() para sumar kill + coins + win check
 */
public class DeathListener implements Listener {

    private final Anieventmanager      plugin;
    private final BattleRoyaleMiniGame game;

    public DeathListener(Anieventmanager plugin, BattleRoyaleMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        if (!game.isRunning()) return;

        BRPlayer brp = game.getBRPlayer(victim);
        if (brp == null) return;
        if (brp.isDead()) return;

        // Killer (puede ser null si murió por zona, fall damage, etc.)
        Player killer = victim.getKiller();

        // Detectar causa para el broadcast
        String causeText = detectCauseText(victim, killer);

        // Drop de items: el comportamiento default de Bukkit dropea el inventario.
        // Confirmamos que sí se haga (algunos plugins lo cancelan).
        event.setKeepInventory(false);
        event.setKeepLevel(false);

        // Cancelamos el respawn screen — el jugador no debe ver "You Died"
        // Esto se hace dropeando los items y cambiando a SPECTATOR antes del respawn.
        // Como PlayerDeathEvent ocurre antes del respawn, mejor lo hacemos en el siguiente tick.
        event.deathMessage(null); // suprimir mensaje vanilla

        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!victim.isOnline()) return;
            // Forzar respawn inmediato (sin pantalla "You Died")
            victim.spigot().respawn();
            // Cambiar a spectator en la posición de muerte
            org.bukkit.Location deathLoc = event.getEntity().getLocation();
            victim.teleport(deathLoc);
            victim.setGameMode(GameMode.SPECTATOR);
            victim.setHealth(20);
            victim.setFoodLevel(20);
            victim.getInventory().clear();
        });

        // Broadcast personalizado
        Component msg;
        if (killer != null && !killer.equals(victim)) {
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            String weaponName = weapon != null && weapon.getType() != Material.AIR
                    ? formatItemName(weapon.getType())
                    : "puños";
            msg = Component.text("☠ ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text(victim.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" eliminado por ", NamedTextColor.GRAY))
                    .append(Component.text(killer.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" [" + weaponName + "]", NamedTextColor.DARK_GRAY));
        } else {
            msg = Component.text("☠ ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text(victim.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" " + causeText, NamedTextColor.GRAY));
        }

        // Enviar a todos los jugadores registrados en la partida
        for (BRPlayer p : game.getAllPlayers().values()) {
            Player pl = org.bukkit.Bukkit.getPlayer(p.getUuid());
            if (pl != null && pl.isOnline()) pl.sendMessage(msg);
        }
        plugin.getLogger().info("[BR-Death] " +
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(msg));

        // Delegar al MiniGame para sumar kill + coins + win check
        // Pero sin re-broadcastear (ya lo hicimos)
        game.handleDeath(victim, killer);
    }

    private String detectCauseText(Player victim, Player killer) {
        if (killer != null && !killer.equals(victim)) return "eliminado por " + killer.getName();
        var lastDmg = victim.getLastDamageCause();
        if (lastDmg == null) return "murió";
        return switch (lastDmg.getCause()) {
            case FALL          -> "murió por caída";
            case DROWNING      -> "se ahogó";
            case LAVA          -> "ardió en lava";
            case FIRE, FIRE_TICK -> "se quemó";
            case VOID          -> "cayó al vacío";
            case SUFFOCATION   -> "se asfixió";
            case STARVATION    -> "murió de hambre";
            case WORLD_BORDER  -> "murió fuera de la zona";
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> "explotó";
            case PROJECTILE    -> "recibió un proyectil mortal";
            default            -> "murió";
        };
    }

    private String formatItemName(Material mat) {
        String name = mat.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}