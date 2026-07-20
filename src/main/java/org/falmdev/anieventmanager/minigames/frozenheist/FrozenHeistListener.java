package org.falmdev.anieventmanager.minigames.frozenheist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;
import net.kyori.adventure.title.Title;

import java.util.*;

public class FrozenHeistListener implements Listener {

    private final Anieventmanager plugin;
    private final FrozenHeistMiniGame miniGame;
    private final Map<UUID, Long> lastFlagWarnMs = new HashMap<>();

    private final Map<UUID, BukkitTask> rescueTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> rescueProgressTasks = new HashMap<>();
    private BukkitTask snowballReplenishTask;

    public FrozenHeistListener(Anieventmanager plugin, FrozenHeistMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
        startSnowballReplenish();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!(snowball.getShooter() instanceof Player player)) return;
        if (!miniGame.isActivePlayer(player)) return;

        PlayerState ps = miniGame.getPlayerState(player.getUniqueId());
        if (ps == null) return;

        if (ps.isFrozen()) {
            event.setCancelled(true);
            return;
        }

        if (!ps.canShoot()) {
            event.setCancelled(true);
            return;
        }

        ps.recordShot();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSnowballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!(snowball.getShooter() instanceof Player shooter)) return;
        if (!miniGame.isActivePlayer(shooter)) return;
        if (!(event.getHitEntity() instanceof Player victim)) return;
        if (!miniGame.isActivePlayer(victim)) return;

        if (shooter.getUniqueId().equals(victim.getUniqueId())) return;

        PlayerState victimPs = miniGame.getPlayerState(victim.getUniqueId());
        if (victimPs == null || victimPs.isFrozen()) return;

        Optional<EventTeam> shooterTeam = plugin.getTeamManager().getTeamOf(shooter);
        Optional<EventTeam> victimTeam  = plugin.getTeamManager().getTeamOf(victim);
        if (shooterTeam.isPresent() && victimTeam.isPresent()
                && shooterTeam.get().getId().equals(victimTeam.get().getId())) return;

        if (isInSafeBase(victim)) return;

        if (isInSafeBase(shooter)) return;

        boolean shouldFreeze = victimPs.addHit();

        victim.sendActionBar(Component.text(
                "❄ " + victimPs.getHits() + "/" + PlayerState.HITS_TO_FREEZE + " hits",
                NamedTextColor.AQUA));

        if (shouldFreeze) {
            miniGame.freezePlayer(victim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!miniGame.isActivePlayer(player)) return;

        PlayerState ps = miniGame.getPlayerState(player.getUniqueId());
        if (ps == null || !ps.isFrozen()) return;

        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;

        if (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(from.clone().setDirection(to.getDirection()));
        }

        player.sendActionBar(miniGame.buildFrozenStatusBar(ps));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        Player rescuer = event.getPlayer();
        if (!miniGame.isActivePlayer(rescuer)) return;
        if (!(event.getRightClicked() instanceof Player target)) return;
        if (!miniGame.isActivePlayer(target)) return;

        PlayerState rescuerPs = miniGame.getPlayerState(rescuer.getUniqueId());
        PlayerState targetPs  = miniGame.getPlayerState(target.getUniqueId());

        if (rescuerPs == null || targetPs == null) return;
        if (rescuerPs.isFrozen()) return;
        if (!targetPs.isFrozen()) return;

        Optional<EventTeam> rescuerTeam = plugin.getTeamManager().getTeamOf(rescuer);
        Optional<EventTeam> targetTeam  = plugin.getTeamManager().getTeamOf(target);
        if (rescuerTeam.isEmpty() || targetTeam.isEmpty()
                || !rescuerTeam.get().getId().equals(targetTeam.get().getId())) return;

        if (rescuer.getInventory().getItemInMainHand().getType() != Material.BLAZE_POWDER) {
            rescuer.sendMessage(Component.text(
                    "Necesitas Polvo de Blaze para descongelar.", NamedTextColor.RED));
            return;
        }

        if (rescuerPs.isCarryingFlag()) {
            rescuer.sendMessage(Component.text(
                    "No puedes rescatar mientras llevas una bandera.", NamedTextColor.RED));
            return;
        }

        if (rescuerPs.isRescuing()) return;

        targetPs.setBeingRescuedBy(rescuer.getUniqueId());
        rescuer.sendMessage(Component.text("Rescatando... mantén el click.", NamedTextColor.YELLOW));

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PlayerState rps = miniGame.getPlayerState(rescuer.getUniqueId());
            PlayerState tps = miniGame.getPlayerState(target.getUniqueId());
            stopRescueProgress(rescuer);
            if (rps == null || tps == null || !tps.isFrozen()) return;

            miniGame.unfreezePlayer(target, rescuer);
            rps.cancelRescue();
            rescueTasks.remove(rescuer.getUniqueId());
        }, (PlayerState.RESCUE_TIME_MS / 50));

        rescuerPs.startRescuing(target.getUniqueId(), task);
        rescueTasks.put(rescuer.getUniqueId(), task);
        startRescueProgress(rescuer);

        event.setCancelled(true);
    }

    private void startRescueProgress(Player rescuer) {
        stopRescueProgress(rescuer);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            PlayerState rps = miniGame.getPlayerState(rescuer.getUniqueId());
            if (rps == null || !rps.isRescuing()) {
                stopRescueProgress(rescuer);
                return;
            }
            double ratio = (System.currentTimeMillis() - rps.getRescueStartMs())
                    / (double) PlayerState.RESCUE_TIME_MS;
            rescuer.sendActionBar(miniGame.buildRescueProgressBar(ratio));
        }, 0L, 2L);
        rescueProgressTasks.put(rescuer.getUniqueId(), task);
    }

    private void stopRescueProgress(Player rescuer) {
        BukkitTask task = rescueProgressTasks.remove(rescuer.getUniqueId());
        if (task != null && !task.isCancelled()) task.cancel();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMoveRescueCancel(PlayerMoveEvent event) {
        Player rescuer = event.getPlayer();
        PlayerState ps = miniGame.getPlayerState(rescuer.getUniqueId());
        if (ps == null || !ps.isRescuing()) return;

        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;

        if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
            cancelRescue(rescuer, ps);
        }
    }

    private void cancelRescue(Player rescuer, PlayerState ps) {
        UUID targetUUID = ps.getRescuingTarget();
        ps.cancelRescue();
        rescueTasks.remove(rescuer.getUniqueId());
        stopRescueProgress(rescuer);

        if (targetUUID != null) {
            PlayerState targetPs = miniGame.getPlayerState(targetUUID);
            if (targetPs != null) targetPs.setBeingRescuedBy(null);
        }
        rescuer.sendMessage(Component.text("Rescate cancelado.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove2(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!miniGame.isActivePlayer(player)) return;

        PlayerState ps = miniGame.getPlayerState(player.getUniqueId());
        if (ps == null || ps.isFrozen()) return;

        String nearbyFlagTeam = miniGame.getFlagManager()
                .getNearbyFlag(player.getLocation(), 2.0);
        if (nearbyFlagTeam == null) return;

        Optional<EventTeam> playerTeamOpt = plugin.getTeamManager().getTeamOf(player);
        if (playerTeamOpt.isEmpty()) return;

        String playerTeamId = playerTeamOpt.get().getId();
        boolean isOwnFlag = nearbyFlagTeam.equals(playerTeamId);

        if (ps.isCarryingFlag()) {
            if (!isOwnFlag) {
                long now = System.currentTimeMillis();
                Long lastWarn = lastFlagWarnMs.get(player.getUniqueId());
                if (lastWarn == null || now - lastWarn > 2000) {
                    lastFlagWarnMs.put(player.getUniqueId(), now);
                    player.sendActionBar(Component.text(
                            "⚠ Ya llevas una bandera — entrégala primero.", NamedTextColor.RED));
                }
            }
            return;
        }

        boolean picked = miniGame.getFlagManager().tryPickup(
                player.getUniqueId(), nearbyFlagTeam, playerTeamId, ps);
        if (!picked) return;

        TeamHeistData ownerData = miniGame.getTeamData().get(nearbyFlagTeam);
        String ownerName = ownerData != null
                ? ownerData.getTeam().getDisplayName() : nearbyFlagTeam;

        if (isOwnFlag) {
            handleOwnFlagPickup(player, playerTeamId, ownerData);
        } else {
            handleEnemyFlagPickup(player, playerTeamId, nearbyFlagTeam, ownerName);
        }
    }

    private void handleEnemyFlagPickup(Player player, String playerTeamId,
                                       String flagTeamId, String flagTeamName) {
        miniGame.applyFlagSlowness(player);
        miniGame.equipFlagHelmet(player, flagTeamId);

        TeamHeistData flagOwnerData = miniGame.getTeamData().get(flagTeamId);
        if (flagOwnerData != null) {
            Component ownerMsg = Component.text("⚠ ¡", NamedTextColor.RED)
                    .append(Component.text(player.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" robó tu bandera!", NamedTextColor.RED));
            flagOwnerData.getTeam().getOnlinePlayers().forEach(p -> p.sendMessage(ownerMsg));
        }

        TeamHeistData playerData = miniGame.getTeamData().get(playerTeamId);
        if (playerData != null) {
            Component teamMsg = Component.text("🚩 Tu compañero ", NamedTextColor.GOLD)
                    .append(Component.text(player.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" robó la bandera de " + flagTeamName + "!", NamedTextColor.GOLD));
            playerData.getTeam().getOnlinePlayers().stream()
                    .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                    .forEach(p -> p.sendMessage(teamMsg));
        }
    }

    private void handleOwnFlagPickup(Player player, String teamId, TeamHeistData data) {
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SPEED, 80, 1, false, false, false));

        miniGame.equipFlagHelmet(player, teamId);

        player.sendMessage(Component.text(
                "✦ Llevas tu bandera de vuelta a la base.", NamedTextColor.GREEN));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMoveCapture(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!miniGame.isActivePlayer(player)) return;

        PlayerState ps = miniGame.getPlayerState(player.getUniqueId());
        if (ps == null || !ps.isCarryingFlag() || ps.isFrozen()) return;

        Optional<EventTeam> playerTeamOpt = plugin.getTeamManager().getTeamOf(player);
        if (playerTeamOpt.isEmpty()) return;

        String playerTeamId = playerTeamOpt.get().getId();
        String flagTeamId   = ps.getCarryingFlagOf();
        TeamHeistData playerData = miniGame.getTeamData().get(playerTeamId);
        if (playerData == null) return;

        if (!playerData.isInsideCaptureZone(player.getLocation())) return;

        boolean isOwnFlag = flagTeamId.equals(playerTeamId);

        ps.clearFlag();
        miniGame.removeSlownessEffect(player);
        miniGame.clearFlagHelmet(player);
        miniGame.getFlagManager().returnToBase(flagTeamId);

        if (isOwnFlag) {
            miniGame.addPoints(playerTeamId, TeamHeistData.POINTS_RECOVER);

            Component recoverMsg = Component.text("✦ ¡Recuperaste tu bandera! (+"
                    + TeamHeistData.POINTS_RECOVER + " pts)", NamedTextColor.GREEN);
            playerData.getTeam().getOnlinePlayers().forEach(p -> p.sendMessage(recoverMsg));
        } else {
            miniGame.addPoints(playerTeamId, TeamHeistData.POINTS_CAPTURE);

            TeamHeistData flagOwnerData = miniGame.getTeamData().get(flagTeamId);
            String flagTeamName = flagOwnerData != null
                    ? flagOwnerData.getTeam().getDisplayName() : flagTeamId;

            Component captureMsg = Component.text("🚩 ¡Capturaste la bandera de " + flagTeamName
                    + "! (+" + TeamHeistData.POINTS_CAPTURE + " pts)", NamedTextColor.GOLD);
            playerData.getTeam().getOnlinePlayers().forEach(p -> p.sendMessage(captureMsg));

            Title captureTitle = Title.title(
                    Component.text("🚩 +" + TeamHeistData.POINTS_CAPTURE, NamedTextColor.GOLD),
                    Component.text("¡Bandera capturada!", NamedTextColor.GREEN),
                    Title.Times.times(
                            java.time.Duration.ofMillis(100),
                            java.time.Duration.ofMillis(1500),
                            java.time.Duration.ofMillis(300))
            );
            playerData.getTeam().getOnlinePlayers()
                    .forEach(p -> p.showTitle(captureTitle));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!miniGame.isActivePlayer(victim)) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!miniGame.isActivePlayer(player)) return;

        event.setDeathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.spigot().respawn();
            miniGame.respawn(player);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!miniGame.isActivePlayer(player)) return;

        plugin.getTeamManager().getTeamOf(player).ifPresent(team -> {
            TeamHeistData data = miniGame.getTeamData().get(team.getId());
            if (data != null && data.getAnyBaseSpawn() != null) {
                event.setRespawnLocation(data.getAnyBaseSpawn());
            }
        });
    }

    private void startSnowballReplenish() {
        snowballReplenishTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!miniGame.isRunning()) return;
            miniGame.getTeamData().values().forEach(data ->
                    data.getTeam().getOnlinePlayers().forEach(p -> {
                        PlayerState ps = miniGame.getPlayerState(p.getUniqueId());
                        if (ps == null || ps.isFrozen()) return;

                        ItemStack slot0 = p.getInventory().getItem(0);
                        if (slot0 == null || slot0.getType() != Material.SNOWBALL
                                || slot0.getAmount() < 16) {
                            p.getInventory().setItem(0, new ItemStack(Material.SNOWBALL, 16));
                        }

                        ItemStack slot1 = p.getInventory().getItem(1);
                        if (slot1 == null || slot1.getType() != Material.BLAZE_POWDER) {
                            p.getInventory().setItem(1, miniGame.buildRescueItem());
                        }
                    })
            );
        }, 0L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!miniGame.isActivePlayer(player)) return;
        PlayerState ps = miniGame.getPlayerState(player.getUniqueId());
        if (ps != null && ps.isFrozen()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        org.bukkit.inventory.meta.ItemMeta meta = event.getItem().getItemStack().getItemMeta();
        if (meta == null) return;

        boolean isFlagItem = meta.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(
                        org.falmdev.anieventmanager.Anieventmanager.getInstance(), "flag_owner"),
                org.bukkit.persistence.PersistentDataType.STRING);

        if (!isFlagItem) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRescueItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!miniGame.isActivePlayer(player)) return;

        org.bukkit.inventory.meta.ItemMeta meta = event.getItemDrop().getItemStack().getItemMeta();
        if (meta == null) return;

        boolean isRescueItem = meta.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(
                        org.falmdev.anieventmanager.Anieventmanager.getInstance(), "fh_rescue_item"),
                org.bukkit.persistence.PersistentDataType.BYTE);

        if (isRescueItem) event.setCancelled(true);
    }

    private boolean isInSafeBase(Player player) {
        for (TeamHeistData data : miniGame.getTeamData().values()) {
            if (data.isInsideBase(player.getLocation())) return true;
        }
        return false;
    }

    public void cleanup() {
        if (snowballReplenishTask != null && !snowballReplenishTask.isCancelled())
            snowballReplenishTask.cancel();
        rescueTasks.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        rescueTasks.clear();
        rescueProgressTasks.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        rescueProgressTasks.clear();
        lastFlagWarnMs.clear();
    }
}