package org.falmdev.anieventmanager.minigames.parkourduos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.*;

public class AbilityManager implements Listener {

    private static final long CONFIRM_WINDOW_MS = 8_000;
    public static final int DOUBLE_JUMP_DURATION_S = 15;

    private final Map<UUID, Map<HotbarAbility, Long>> cooldownEnds = new HashMap<>();
    private final Map<String, PendingConfirm> pendingConfirms = new HashMap<>();
    private final Set<UUID> doubleJumpActive = new HashSet<>();
    private final Map<UUID, BukkitTask> doubleJumpTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> cooldownTickers = new HashMap<>();

    private final Anieventmanager plugin;
    private final ParkourDuosMiniGame miniGame;


    public AbilityManager(Anieventmanager plugin, ParkourDuosMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void giveAbilities(Player player) {
        for (HotbarAbility ability : HotbarAbility.values()) {
            player.getInventory().setItem(ability.getSlot(), ability.buildItem());
        }
    }

    public void onAbilityUse(Player player, HotbarAbility ability) {
        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) return;
        EventTeam team = teamOpt.get();

        // Verificar cooldown
        if (isOnCooldown(player, ability)) {
            int left = getCooldownLeft(player, ability);
            player.sendActionBar(Component.text(
                    "⏱ " + ability.getDisplayName() + " en cooldown — " + left + "s",
                    NamedTextColor.RED));
            return;
        }

        switch (ability) {
            case RETURN_CHECKPOINT  -> handleConfirmable(player, team, ability);
            case DOUBLE_JUMP        -> handleConfirmable(player, team, ability);
            case TELEPORT_TO_TEAMMATE -> handleTeleport(player, team);
        }
    }

    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();

        cancelDoubleJump(player);

        BukkitTask ticker = cooldownTickers.remove(uuid);
        if (ticker != null && !ticker.isCancelled()) ticker.cancel();

        cooldownEnds.remove(uuid);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    public void cleanupAll() {
        for (UUID uuid : new HashSet<>(doubleJumpActive)) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) cleanup(p);
        }
        doubleJumpActive.clear();
        doubleJumpTasks.clear();
        cooldownTickers.clear();
        cooldownEnds.clear();
        pendingConfirms.clear();
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!doubleJumpActive.contains(player.getUniqueId())) return;
        if (!miniGame.isRunning()) return;

        event.setCancelled(true);
        player.setFlying(false);
        player.setVelocity(player.getVelocity().setY(0.9));

        player.sendActionBar(Component.text(
                "🪶 Doble salto usado", NamedTextColor.YELLOW));
    }

    private void handleConfirmable(Player player, EventTeam team, HotbarAbility ability) {
        String teamId = team.getId();
        PendingConfirm existing = pendingConfirms.get(teamId);

        if (existing == null || existing.ability != ability
                || System.currentTimeMillis() > existing.expiresAt) {
            pendingConfirms.put(teamId, new PendingConfirm(ability, player.getUniqueId()));

            String msg = "⚡ " + player.getName() + " quiere usar " + ability.getDisplayName()
                    + ". ¡Usa el mismo item para confirmar! (" + (CONFIRM_WINDOW_MS / 1000) + "s)";
            for (Player p : team.getOnlinePlayers()) {
                p.sendActionBar(Component.text(msg,
                        p.equals(player) ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
            }

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                PendingConfirm current = pendingConfirms.get(teamId);
                if (current != null && current.initiator.equals(player.getUniqueId())
                        && current.ability == ability) {
                    pendingConfirms.remove(teamId);
                    if (player.isOnline()) {
                        player.sendActionBar(Component.text(
                                "✘ Confirmación expirada.", NamedTextColor.RED));
                    }
                }
            }, CONFIRM_WINDOW_MS / 50);

            return;
        }

        if (existing.initiator.equals(player.getUniqueId())) {
            player.sendActionBar(Component.text(
                    "Espera a que tu compañero confirme.", NamedTextColor.GRAY));
            return;
        }

        pendingConfirms.remove(teamId);

        for (Player p : team.getOnlinePlayers()) {
            applyCooldown(p, ability);
        }

        switch (ability) {
            case RETURN_CHECKPOINT -> executeReturnCheckpoint(team);
            case DOUBLE_JUMP       -> executeDoubleJump(team);
            default -> {}
        }
    }

    private void executeReturnCheckpoint(EventTeam team) {
        TeamParkourData data = miniGame.getTeamData().get(team.getId());
        if (data == null) return;

        Location dest;
        int completed = data.getCompletedCheckpoints();

        if (completed > 0) {
            ParkourCheckpoint lastCp = data.getCheckpoints().get(completed - 1);
            dest = lastCp.getCenter().clone().add(0, 1.5, 0);
        } else {
            dest = miniGame.getConfig().getTeamSpawn1(team.getId());
        }

        if (dest == null) return;
        final Location finalDest = dest;

        for (Player p : team.getOnlinePlayers()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                p.teleport(finalDest);
                p.sendMessage(Component.text(
                        "↩ Volviste al último checkpoint.", NamedTextColor.AQUA));
            });
        }

        broadcastTeam(team, Component.text(
                "↩ El equipo volvió al último checkpoint.", NamedTextColor.AQUA));
    }

    private void executeDoubleJump(EventTeam team) {
        for (Player p : team.getOnlinePlayers()) {
            activateDoubleJump(p);
        }

        broadcastTeam(team, Component.text(
                "🪶 Doble salto activo durante " + DOUBLE_JUMP_DURATION_S + "s.",
                NamedTextColor.YELLOW));
    }

    private void activateDoubleJump(Player player) {
        UUID uuid = player.getUniqueId();
        doubleJumpActive.add(uuid);

        player.setAllowFlight(true);
        player.setFlying(false);
        player.setGameMode(GameMode.ADVENTURE);

        player.sendMessage(Component.text(
                "🪶 Doble salto activado — " + DOUBLE_JUMP_DURATION_S + "s. Presiona espacio en el aire.",
                NamedTextColor.YELLOW));

        BukkitTask old = doubleJumpTasks.remove(uuid);
        if (old != null && !old.isCancelled()) old.cancel();

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            cancelDoubleJump(player);
            if (player.isOnline()) {
                player.sendActionBar(Component.text("🪶 Doble salto expiró.", NamedTextColor.GRAY));
            }
        }, DOUBLE_JUMP_DURATION_S * 20L);

        doubleJumpTasks.put(uuid, task);
    }

    private void cancelDoubleJump(Player player) {
        UUID uuid = player.getUniqueId();
        doubleJumpActive.remove(uuid);
        player.setAllowFlight(false);
        player.setFlying(false);

        BukkitTask task = doubleJumpTasks.remove(uuid);
        if (task != null && !task.isCancelled()) task.cancel();
    }

    private void handleTeleport(Player player, EventTeam team) {
        List<Player> members = team.getOnlinePlayers();
        Player teammate = members.stream()
                .filter(p -> !p.equals(player))
                .findFirst()
                .orElse(null);

        if (teammate == null) {
            player.sendActionBar(Component.text(
                    "Tu compañero no está conectado.", NamedTextColor.RED));
            return;
        }

        applyCooldown(player, HotbarAbility.TELEPORT_TO_TEAMMATE);

        Location dest = teammate.getLocation();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.teleport(dest);
            player.sendMessage(Component.text(
                    "✦ Te teletransportaste a " + teammate.getName() + ".", NamedTextColor.LIGHT_PURPLE));
            teammate.sendActionBar(Component.text(
                    player.getName() + " se teletransportó a ti.", NamedTextColor.GRAY));
        });
    }

    public boolean isOnCooldown(Player player, HotbarAbility ability) {
        Map<HotbarAbility, Long> map = cooldownEnds.get(player.getUniqueId());
        if (map == null) return false;
        Long end = map.get(ability);
        return end != null && System.currentTimeMillis() < end;
    }

    public int getCooldownLeft(Player player, HotbarAbility ability) {
        Map<HotbarAbility, Long> map = cooldownEnds.get(player.getUniqueId());
        if (map == null) return 0;
        Long end = map.get(ability);
        if (end == null) return 0;
        long diff = end - System.currentTimeMillis();
        return diff > 0 ? (int) Math.ceil(diff / 1000.0) : 0;
    }

    private void applyCooldown(Player player, HotbarAbility ability) {
        UUID uuid = player.getUniqueId();
        cooldownEnds.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(ability, System.currentTimeMillis() + ability.getCooldownSeconds() * 1000L);

        startCooldownTicker(player, ability);
    }

    private void startCooldownTicker(Player player, HotbarAbility ability) {
        UUID uuid = player.getUniqueId();

        BukkitTask old = cooldownTickers.remove(uuid);
        if (old != null && !old.isCancelled()) old.cancel();

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !miniGame.isRunning()) {
                BukkitTask self = cooldownTickers.remove(uuid);
                if (self != null && !self.isCancelled()) self.cancel();
                return;
            }

            int left = getCooldownLeft(player, ability);
            if (left <= 0) {
                player.getInventory().setItem(ability.getSlot(), ability.buildItem());
                BukkitTask self = cooldownTickers.remove(uuid);
                if (self != null && !self.isCancelled()) self.cancel();
            } else {
                player.getInventory().setItem(ability.getSlot(), ability.buildCooldownItem(left));
            }
        }, 0L, 20L);

        cooldownTickers.put(uuid, task);
    }

    private void broadcastTeam(EventTeam team, Component msg) {
        team.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
    }

    private static class PendingConfirm {
        final HotbarAbility ability;
        final UUID initiator;
        final long expiresAt;

        PendingConfirm(HotbarAbility ability, UUID initiator) {
            this.ability   = ability;
            this.initiator = initiator;
            this.expiresAt = System.currentTimeMillis() + CONFIRM_WINDOW_MS;
        }
    }
}