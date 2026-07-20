package org.falmdev.anieventmanager.minigames.battleroyale.death;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleMiniGame;
import org.falmdev.anieventmanager.minigames.battleroyale.model.BRPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeathListener implements Listener {

    private final Anieventmanager      plugin;
    private final BattleRoyaleMiniGame game;
    private final Map<UUID, Location>  pendingRespawnLocations = new HashMap<>();

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

        Player killer = victim.getKiller();

        String causeText = detectCauseText(victim, killer);

        event.setKeepInventory(false);
        event.setKeepLevel(false);

        event.deathMessage(null);

        pendingRespawnLocations.put(victim.getUniqueId(), victim.getLocation().clone());

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

        for (BRPlayer p : game.getAllPlayers().values()) {
            Player pl = Bukkit.getPlayer(p.getUuid());
            if (pl != null && pl.isOnline()) pl.sendMessage(msg);
        }
        plugin.getLogger().info("[BR-Death] " +
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(msg));

        game.handleDeath(victim, killer);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location deathLoc = pendingRespawnLocations.remove(player.getUniqueId());
        if (deathLoc == null) return;

        event.setRespawnLocation(deathLoc);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.setGameMode(GameMode.SPECTATOR);
            player.setHealth(20);
            player.setFoodLevel(20);
            player.getInventory().clear();
        });
    }

    public void clearPendingRespawns() {
        pendingRespawnLocations.clear();
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