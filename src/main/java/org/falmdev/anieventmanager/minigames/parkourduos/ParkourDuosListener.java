package org.falmdev.anieventmanager.minigames.parkourduos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.Optional;

public class ParkourDuosListener implements Listener {

    private final Anieventmanager     plugin;
    private final ParkourDuosMiniGame miniGame;

    public ParkourDuosListener(Anieventmanager plugin, ParkourDuosMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAbilityUse(PlayerInteractEvent event) {
        if (!miniGame.isRunning()) return;
        // Solo click derecho, solo mano principal
        if (event.getHand() != EquipmentSlot.HAND) return;
        boolean isRight = event.getAction() == Action.RIGHT_CLICK_BLOCK
                || event.getAction() == Action.RIGHT_CLICK_AIR;
        if (!isRight) return;

        Player player = event.getPlayer();
        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        HotbarAbility ability = HotbarAbility.fromItem(item);
        if (ability == null) return;

        event.setCancelled(true);
        miniGame.getAbilityManager().onAbilityUse(player, ability);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!miniGame.isRunning()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!miniGame.isRunning()) return;
        if (!(event.getDamager() instanceof Player) && !(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVoid(EntityDamageEvent event) {
        if (!miniGame.isRunning()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) return;

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) return;

        TeamParkourData data = miniGame.getDataFor(teamOpt.get());
        if (data != null && data.isFinished()) return;

        event.setCancelled(true);

        if (data == null) return;

        org.bukkit.Location respawn;
        int completed = data.getCompletedCheckpoints();
        if (completed > 0) {
            ParkourCheckpoint lastCp = data.getCheckpoints().get(completed - 1);
            respawn = lastCp.getCenter().clone().add(0, 1, 0);
        } else {
            respawn = miniGame.getConfig().getTeamSpawn1(teamOpt.get().getId());
        }

        if (respawn != null) {
            final org.bukkit.Location dest = respawn;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.teleport(dest);
                player.sendMessage(Component.text(
                        "Caíste al vacío. Te enviamos al último checkpoint.",
                        NamedTextColor.YELLOW));
            });
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (!miniGame.isRunning()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getTeamManager().getTeamOf(player).isEmpty()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!miniGame.isRunning()) return;
        if (plugin.getTeamManager().getTeamOf(event.getPlayer()).isEmpty()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!miniGame.isRunning()) return;
        if (plugin.getTeamManager().getTeamOf(event.getPlayer()).isEmpty()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!miniGame.isRunning()) return;
        if (plugin.getTeamManager().getTeamOf(event.getPlayer()).isEmpty()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!miniGame.isRunning()) return;
        Player player = event.getPlayer();
        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) return;

        miniGame.getAbilityManager().cleanup(player);

        for (Player member : teamOpt.get().getOnlinePlayers()) {
            if (!member.equals(player)) {
                member.sendMessage(Component.text("⚠ ", NamedTextColor.RED)
                        .append(Component.text(player.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" se desconectó. Estás solo.", NamedTextColor.RED)));
            }
        }
    }
}