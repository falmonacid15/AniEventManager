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

    // Jugadores con rescate en progreso: rescuer UUID → task
    private final Map<UUID, BukkitTask> rescueTasks = new HashMap<>();
    // Tick de reposición de bolas de nieve
    private BukkitTask snowballReplenishTask;

    public FrozenHeistListener(Anieventmanager plugin, FrozenHeistMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
        startSnowballReplenish();
    }

    // ── Bolas de nieve — disparo y cooldown ───────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!(snowball.getShooter() instanceof Player player)) return;
        if (!miniGame.isActivePlayer(player)) return;

        PlayerState ps = miniGame.getPlayerState(player.getUniqueId());
        if (ps == null) return;

        // Cancelar si está congelado
        if (ps.isFrozen()) {
            event.setCancelled(true);
            return;
        }

        // Cooldown entre disparos
        if (!ps.canShoot()) {
            event.setCancelled(true);
            return;
        }

        ps.recordShot();
    }

    // ── Impacto de bola de nieve ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onSnowballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!(snowball.getShooter() instanceof Player shooter)) return;
        if (!miniGame.isActivePlayer(shooter)) return;
        if (!(event.getHitEntity() instanceof Player victim)) return;
        if (!miniGame.isActivePlayer(victim)) return;

        // No puede golpearse a sí mismo
        if (shooter.getUniqueId().equals(victim.getUniqueId())) return;

        PlayerState victimPs = miniGame.getPlayerState(victim.getUniqueId());
        if (victimPs == null || victimPs.isFrozen()) return;

        // Verificar si el shooter es del mismo equipo (friendly fire off)
        Optional<EventTeam> shooterTeam = plugin.getTeamManager().getTeamOf(shooter);
        Optional<EventTeam> victimTeam  = plugin.getTeamManager().getTeamOf(victim);
        if (shooterTeam.isPresent() && victimTeam.isPresent()
                && shooterTeam.get().getId().equals(victimTeam.get().getId())) return;

        // Verificar si la víctima está en su base (zona segura)
        if (isInSafeBase(victim)) return;

        // Verificar si el shooter está dentro de una base segura (no puede disparar desde dentro)
        if (isInSafeBase(shooter)) return;

        // Registrar hit
        boolean shouldFreeze = victimPs.addHit();

        // Feedback visual del hit
        victim.sendActionBar(Component.text(
                "❄ " + victimPs.getHits() + "/" + PlayerState.HITS_TO_FREEZE + " hits",
                NamedTextColor.AQUA));

        if (shouldFreeze) {
            miniGame.freezePlayer(victim);
        }
    }

    // ── Movimiento — congelados no pueden moverse ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!miniGame.isActivePlayer(player)) return;

        PlayerState ps = miniGame.getPlayerState(player.getUniqueId());
        if (ps == null || !ps.isFrozen()) return;

        // Permitir rotar la cámara pero no desplazarse
        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;

        if (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(from.clone().setDirection(to.getDirection()));
        }

        // Mostrar tiempo restante en actionbar
        player.sendActionBar(Component.text(
                "❄ Congelado — " + ps.getFrozenSecondsLeft() + "s  |  "
                        + (ps.isBeingRescued() ? "¡Rescatando..." : "Espera a tu compañero"),
                NamedTextColor.AQUA));
    }

    // ── Rescate — click derecho sobre compañero congelado ─────────────────────

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

        // Verificar que son del mismo equipo
        Optional<EventTeam> rescuerTeam = plugin.getTeamManager().getTeamOf(rescuer);
        Optional<EventTeam> targetTeam  = plugin.getTeamManager().getTeamOf(target);
        if (rescuerTeam.isEmpty() || targetTeam.isEmpty()
                || !rescuerTeam.get().getId().equals(targetTeam.get().getId())) return;

        // No puede rescatar si lleva una bandera
        if (rescuerPs.isCarryingFlag()) {
            rescuer.sendMessage(Component.text(
                    "No puedes rescatar mientras llevas una bandera.", NamedTextColor.RED));
            return;
        }

        // Si ya está rescatando, ignorar
        if (rescuerPs.isRescuing()) return;

        // Iniciar progreso de rescate (1 segundo de click mantenido)
        targetPs.setBeingRescuedBy(rescuer.getUniqueId());
        rescuer.sendMessage(Component.text("Rescatando... mantén el click.", NamedTextColor.YELLOW));

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Verificar que ambos siguen válidos
            PlayerState rps = miniGame.getPlayerState(rescuer.getUniqueId());
            PlayerState tps = miniGame.getPlayerState(target.getUniqueId());
            if (rps == null || tps == null || !tps.isFrozen()) return;

            miniGame.unfreezePlayer(target, rescuer);
            rps.cancelRescue();
            rescueTasks.remove(rescuer.getUniqueId());
        }, (PlayerState.RESCUE_TIME_MS / 50));

        rescuerPs.startRescuing(target.getUniqueId(), task);
        rescueTasks.put(rescuer.getUniqueId(), task);

        event.setCancelled(true);
    }

    // ── Cancelar rescate si el rescatador se aleja o deja de hacer click ──────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMoveRescueCancel(PlayerMoveEvent event) {
        Player rescuer = event.getPlayer();
        PlayerState ps = miniGame.getPlayerState(rescuer.getUniqueId());
        if (ps == null || !ps.isRescuing()) return;

        // Si se mueve, cancelar rescate
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

        if (targetUUID != null) {
            PlayerState targetPs = miniGame.getPlayerState(targetUUID);
            if (targetPs != null) targetPs.setBeingRescuedBy(null);
        }
        rescuer.sendMessage(Component.text("Rescate cancelado.", NamedTextColor.RED));
    }

    // ── Banderas — recoger ────────────────────────────────────────────────────

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

        // FIX: solo mostrar el warning si la bandera cercana es enemiga
        // (la propia en IN_BASE no es recogible de todas formas)
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
            flagOwnerData.getTeam().getOnlinePlayers().forEach(p ->
                    p.sendActionBar(Component.text(
                            "⚠ ¡" + player.getName() + " robó tu bandera!",
                            NamedTextColor.RED)));
        }

        TeamHeistData playerData = miniGame.getTeamData().get(playerTeamId);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(
                Component.text("🚩 ", NamedTextColor.RED)
                        .append(Component.text(player.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" (", NamedTextColor.GRAY))
                        .append(Component.text(playerData != null
                                        ? playerData.getTeam().getDisplayName() : playerTeamId,
                                playerData != null ? playerData.getTeam().getColor() : NamedTextColor.WHITE))
                        .append(Component.text(") robó la bandera de ", NamedTextColor.RED))
                        .append(Component.text(flagTeamName, NamedTextColor.YELLOW))
                        .append(Component.text("!", NamedTextColor.RED))));
    }

    private void handleOwnFlagPickup(Player player, String teamId, TeamHeistData data) {
        // Speed boost temporal al recuperar bandera propia
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SPEED, 80, 1, false, false, false));

        miniGame.equipFlagHelmet(player, teamId);

        player.sendMessage(Component.text(
                "✦ Llevas tu bandera de vuelta a la base.", NamedTextColor.GREEN));

        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(
                Component.text("🚩 ", NamedTextColor.GREEN)
                        .append(Component.text(player.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" recuperó la bandera de ", NamedTextColor.GREEN))
                        .append(Component.text(data != null
                                        ? data.getTeam().getDisplayName() : teamId,
                                data != null ? data.getTeam().getColor() : NamedTextColor.WHITE))
                        .append(Component.text(".", NamedTextColor.GREEN))));
    }

    // ── Zona de captura — entregar bandera ────────────────────────────────────

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

        // ¿Está en la zona de captura de su equipo?
        if (!playerData.isInsideCaptureZone(player.getLocation())) return;

        // ¿Es una bandera enemiga? → captura (+5 pts)
        // ¿Es su propia bandera? → recuperación (+2 pts)
        boolean isOwnFlag = flagTeamId.equals(playerTeamId);

        ps.clearFlag();
        miniGame.removeSlownessEffect(player);
        miniGame.clearFlagHelmet(player);
        miniGame.getFlagManager().returnToBase(flagTeamId);

        if (isOwnFlag) {
            miniGame.addPoints(playerTeamId, TeamHeistData.POINTS_RECOVER, "bandera recuperada");
        } else {
            miniGame.addPoints(playerTeamId, TeamHeistData.POINTS_CAPTURE, "bandera capturada");

            // Título de captura
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

    // ── Daño directo — cancelar en zonas seguras y entre compañeros ───────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!miniGame.isActivePlayer(victim)) return;

        event.setCancelled(true); // Todo daño de bolas de nieve se maneja por ProjectileHitEvent
    }

    // ── Muerte — respawn instantáneo ─────────────────────────────────────────

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

    // ── Reponer bolas de nieve ────────────────────────────────────────────────

    private void startSnowballReplenish() {
        snowballReplenishTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!miniGame.isRunning()) return;
            miniGame.getTeamData().values().forEach(data ->
                    data.getTeam().getOnlinePlayers().forEach(p -> {
                        PlayerState ps = miniGame.getPlayerState(p.getUniqueId());
                        if (ps == null || ps.isFrozen()) return;
                        ItemStack slot0 = p.getInventory().getItem(0);
                        if (slot0 == null || slot0.getType() != org.bukkit.Material.SNOWBALL
                                || slot0.getAmount() < 16) {
                            p.getInventory().setItem(0,
                                    new ItemStack(org.bukkit.Material.SNOWBALL, 16));
                        }
                    })
            );
        }, 0L, 20L); // cada segundo
    }

    // ── Bloquear interacciones cuando está congelado ──────────────────────────

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

        // Verificar si el item tiene la clave de bandera en su PersistentData
        org.bukkit.inventory.meta.ItemMeta meta = event.getItem().getItemStack().getItemMeta();
        if (meta == null) return;

        boolean isFlagItem = meta.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(
                        org.falmdev.anieventmanager.Anieventmanager.getInstance(), "flag_owner"),
                org.bukkit.persistence.PersistentDataType.STRING);

        if (!isFlagItem) return;

        // Siempre cancelar — tu lógica en onPlayerMove2 controla el pickup
        event.setCancelled(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Devuelve true si el jugador está dentro de alguna base segura.
     */
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
        lastFlagWarnMs.clear();
    }
}