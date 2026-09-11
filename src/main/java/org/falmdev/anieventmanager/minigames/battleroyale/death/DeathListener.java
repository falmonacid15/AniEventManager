package org.falmdev.anieventmanager.minigames.battleroyale.death;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleMiniGame;
import org.falmdev.anieventmanager.minigames.battleroyale.model.BRPlayer;

public class DeathListener implements Listener {

    private final Anieventmanager      plugin;
    private final BattleRoyaleMiniGame game;

    public DeathListener(Anieventmanager plugin, BattleRoyaleMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFatalDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!game.isRunning()) return;

        BRPlayer brp = game.getBRPlayer(victim);
        if (brp == null || brp.isDead()) return;
        if (brp.isOnDragon() || brp.isParachuting()) return;

        double remainingHealth = victim.getHealth() - event.getFinalDamage();
        if (remainingHealth > 0) return;

        event.setCancelled(true);

        Player killer = null;
        if (event instanceof EntityDamageByEntityEvent damageByEntity
                && damageByEntity.getDamager() instanceof Player attacker) {
            killer = attacker;
        }

        processDeath(victim, killer, event.getCause());
    }

    private void processDeath(Player victim, Player killer, EntityDamageEvent.DamageCause cause) {
        String causeText = detectCauseText(killer, cause);

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

        victim.setHealth(20);
        victim.setFoodLevel(20);
        victim.setFireTicks(0);
        victim.setGameMode(GameMode.SPECTATOR);
        victim.getInventory().clear();

        game.handleDeath(victim, killer);
    }

    private String detectCauseText(Player killer, EntityDamageEvent.DamageCause cause) {
        if (killer != null) return "eliminado por " + killer.getName();
        return switch (cause) {
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