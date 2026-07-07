package org.falmdev.anieventmanager.minigames.parkourduos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.*;

public class AbilityManager {

    private static final long CONFIRM_WINDOW_MS   = 8_000;
    public static final  int  JUMP_BOOST_DURATION_S = 15;
    private static final int  JUMP_BOOST_AMPLIFIER  = 2;

    private static final int  BAR_LENGTH = 16;

    private final Map<UUID, Map<HotbarAbility, Long>> cooldownEnds    = new HashMap<>();
    private final Map<String, PendingConfirm>          pendingConfirms = new HashMap<>();
    private final Map<UUID, Map<HotbarAbility, BukkitTask>> cooldownTickers = new HashMap<>();

    private final Map<UUID, Long>      jumpBoostEndMs   = new HashMap<>();
    private final Map<UUID, BukkitTask> jumpBoostTickers = new HashMap<>();
    private final Map<UUID, BukkitTask> confirmTickers   = new HashMap<>();

    private final Anieventmanager     plugin;
    private final ParkourDuosMiniGame miniGame;

    public AbilityManager(Anieventmanager plugin, ParkourDuosMiniGame miniGame) {
        this.plugin   = plugin;
        this.miniGame = miniGame;
    }

    public void giveAbilities(Player player) {
        for (HotbarAbility ability : HotbarAbility.values()) {
            player.getInventory().setItem(ability.getSlot(), ability.buildItem());
        }
        player.getInventory().setHeldItemSlot(0);
    }

    public void onAbilityUse(Player player, HotbarAbility ability) {
        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) return;
        EventTeam team = teamOpt.get();

        if (isOnCooldown(player, ability)) {
            int left = getCooldownLeft(player, ability);
            player.sendActionBar(Component.text(
                    "⏱ " + ability.getDisplayName() + " — " + left + "s",
                    NamedTextColor.RED));
            switchToSlot0(player);
            return;
        }

        switchToSlot0(player);

        switch (ability) {
            case RETURN_CHECKPOINT    -> handleConfirmable(player, team, ability);
            case JUMP_BOOST           -> handleConfirmable(player, team, ability);
            case TELEPORT_TO_TEAMMATE -> handleTeleport(player, team);
        }
    }

    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();

        Map<HotbarAbility, BukkitTask> tickers = cooldownTickers.remove(uuid);
        if (tickers != null) tickers.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });

        BukkitTask jbt = jumpBoostTickers.remove(uuid);
        if (jbt != null && !jbt.isCancelled()) jbt.cancel();

        BukkitTask ct = confirmTickers.remove(uuid);
        if (ct != null && !ct.isCancelled()) ct.cancel();

        cooldownEnds.remove(uuid);
        jumpBoostEndMs.remove(uuid);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    public void cleanupAll() {
        for (Map<HotbarAbility, BukkitTask> tickers : cooldownTickers.values())
            tickers.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        jumpBoostTickers.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        confirmTickers.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        cooldownTickers.clear();
        jumpBoostTickers.clear();
        confirmTickers.clear();
        cooldownEnds.clear();
        jumpBoostEndMs.clear();
        pendingConfirms.clear();
    }

    private void handleConfirmable(Player player, EventTeam team, HotbarAbility ability) {
        String teamId = team.getId();
        PendingConfirm existing = pendingConfirms.get(teamId);

        if (existing == null || existing.ability != ability
                || System.currentTimeMillis() > existing.expiresAt) {

            PendingConfirm confirm = new PendingConfirm(ability, player.getUniqueId());
            pendingConfirms.put(teamId, confirm);

            for (Player p : team.getOnlinePlayers()) {
                if (p.equals(player)) {
                    startConfirmInitiatorTicker(p, ability, confirm);
                } else {
                    startConfirmReceiverTicker(p, player.getName(), ability, confirm);
                }
            }

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                PendingConfirm current = pendingConfirms.get(teamId);
                if (current != null && current.initiator.equals(player.getUniqueId())
                        && current.ability == ability) {
                    pendingConfirms.remove(teamId);
                    stopConfirmTicker(player.getUniqueId());
                    for (Player p : team.getOnlinePlayers()) {
                        stopConfirmTicker(p.getUniqueId());
                    }
                    if (player.isOnline()) {
                        player.sendActionBar(Component.text(
                                "✘ " + ability.getDisplayName() + " — sin confirmación.",
                                NamedTextColor.RED));
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
            stopConfirmTicker(p.getUniqueId());
            applyCooldown(p, ability);
        }

        switch (ability) {
            case RETURN_CHECKPOINT -> executeReturnCheckpoint(team);
            case JUMP_BOOST        -> executeJumpBoost(team);
            default -> {}
        }
    }

    private void startConfirmInitiatorTicker(Player player, HotbarAbility ability,
                                             PendingConfirm confirm) {
        UUID uuid = player.getUniqueId();
        stopConfirmTicker(uuid);

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !miniGame.isRunning()) {
                stopConfirmTicker(uuid);
                return;
            }
            if (System.currentTimeMillis() > confirm.expiresAt) {
                stopConfirmTicker(uuid);
                return;
            }
            player.sendActionBar(
                    Component.text("⚡ " + ability.getDisplayName() + " — ", NamedTextColor.YELLOW)
                            .append(Component.text("esperando confirmación...", NamedTextColor.GRAY)));
        }, 0L, 2L);

        confirmTickers.put(uuid, task);
    }

    private void startConfirmReceiverTicker(Player player, String initiatorName,
                                            HotbarAbility ability, PendingConfirm confirm) {
        UUID uuid = player.getUniqueId();
        stopConfirmTicker(uuid);

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !miniGame.isRunning()) {
                stopConfirmTicker(uuid);
                return;
            }
            if (System.currentTimeMillis() > confirm.expiresAt) {
                stopConfirmTicker(uuid);
                return;
            }
            player.sendActionBar(
                    Component.text("⚡ " + initiatorName + " quiere usar ", NamedTextColor.GREEN)
                            .append(Component.text(ability.getDisplayName(), NamedTextColor.WHITE))
                            .append(Component.text(" — usa el item para confirmar", NamedTextColor.GREEN)));
        }, 0L, 2L);

        confirmTickers.put(uuid, task);
    }

    private void stopConfirmTicker(UUID uuid) {
        BukkitTask t = confirmTickers.remove(uuid);
        if (t != null && !t.isCancelled()) t.cancel();
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
                p.sendMessage(Component.text("↩ Volviste al último checkpoint.", NamedTextColor.AQUA));
            });
        }
    }

    private void executeJumpBoost(EventTeam team) {
        int durationTicks = JUMP_BOOST_DURATION_S * 20;
        long endMs = System.currentTimeMillis() + JUMP_BOOST_DURATION_S * 1000L;

        PotionEffect effect = new PotionEffect(
                PotionEffectType.JUMP_BOOST, durationTicks, JUMP_BOOST_AMPLIFIER,
                false, false, true);

        for (Player p : team.getOnlinePlayers()) {
            p.addPotionEffect(effect);
            jumpBoostEndMs.put(p.getUniqueId(), endMs);
            startJumpBoostTicker(p);
        }
    }

    private void startJumpBoostTicker(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask old = jumpBoostTickers.remove(uuid);
        if (old != null && !old.isCancelled()) old.cancel();

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !miniGame.isRunning()) {
                BukkitTask self = jumpBoostTickers.remove(uuid);
                if (self != null && !self.isCancelled()) self.cancel();
                return;
            }
            Long endMs = jumpBoostEndMs.get(uuid);
            if (endMs == null) {
                BukkitTask self = jumpBoostTickers.remove(uuid);
                if (self != null && !self.isCancelled()) self.cancel();
                return;
            }
            long remaining = endMs - System.currentTimeMillis();
            if (remaining <= 0) {
                jumpBoostEndMs.remove(uuid);
                BukkitTask self = jumpBoostTickers.remove(uuid);
                if (self != null && !self.isCancelled()) self.cancel();
                player.sendActionBar(Component.text("🪶 Super salto terminó.", NamedTextColor.GRAY));
                return;
            }
            double fraction = (double) remaining / (JUMP_BOOST_DURATION_S * 1000L);
            String bar = buildBar(fraction, NamedTextColor.YELLOW, NamedTextColor.DARK_GRAY);
            int secs = (int) Math.ceil(remaining / 1000.0);
            player.sendActionBar(
                    Component.text("🪶 Super salto  ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                            .append(Component.text(bar, NamedTextColor.YELLOW))
                            .append(Component.text("  " + secs + "s", NamedTextColor.GRAY)));
        }, 0L, 2L);

        jumpBoostTickers.put(uuid, task);
    }

    private void handleTeleport(Player player, EventTeam team) {
        Player teammate = team.getOnlinePlayers().stream()
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
                    "✦ Te teletransportaste a " + teammate.getName() + ".",
                    NamedTextColor.LIGHT_PURPLE));
            teammate.sendMessage(Component.text(
                    player.getName() + " se teletransportó a tu posición.",
                    NamedTextColor.GRAY));
        });
    }

    public int getCooldownPercent(Player player, HotbarAbility ability) {
        Map<HotbarAbility, Long> map = cooldownEnds.get(player.getUniqueId());
        if (map == null) return 0;
        Long end = map.get(ability);
        if (end == null) return 0;
        long total = ability.getCooldownSeconds() * 1000L;
        long remaining = end - System.currentTimeMillis();
        if (remaining <= 0) return 0;
        return (int) Math.round((remaining / (double) total) * 100);
    }

    public int getJumpBoostSecondsLeft(Player player) {
        Long endMs = jumpBoostEndMs.get(player.getUniqueId());
        if (endMs == null) return 0;
        long diff = endMs - System.currentTimeMillis();
        return diff > 0 ? (int) Math.ceil(diff / 1000.0) : 0;
    }

    public int getJumpBoostPercent(Player player) {
        Long endMs = jumpBoostEndMs.get(player.getUniqueId());
        if (endMs == null) return 0;
        long remaining = endMs - System.currentTimeMillis();
        if (remaining <= 0) return 0;
        long total = JUMP_BOOST_DURATION_S * 1000L;
        return (int) Math.round((remaining / (double) total) * 100);
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

    public String getAbilityStatus(Player player, HotbarAbility ability) {
        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isPresent()) {
            PendingConfirm confirm = pendingConfirms.get(teamOpt.get().getId());
            if (confirm != null && confirm.ability == ability
                    && System.currentTimeMillis() <= confirm.expiresAt) {
                return "En espera";
            }
        }
        if (isOnCooldown(player, ability)) return "En uso";
        return "Listo";
    }

    private void applyCooldown(Player player, HotbarAbility ability) {
        UUID uuid = player.getUniqueId();
        cooldownEnds.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(ability, System.currentTimeMillis() + ability.getCooldownSeconds() * 1000L);
        startCooldownTicker(player, ability);
    }

    private void startCooldownTicker(Player player, HotbarAbility ability) {
        UUID uuid = player.getUniqueId();
        Map<HotbarAbility, BukkitTask> playerTickers =
                cooldownTickers.computeIfAbsent(uuid, k -> new HashMap<>());

        BukkitTask old = playerTickers.remove(ability);
        if (old != null && !old.isCancelled()) old.cancel();

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !miniGame.isRunning()) {
                cancelTicker(uuid, ability);
                return;
            }
            int left = getCooldownLeft(player, ability);
            if (left <= 0) {
                player.getInventory().setItem(ability.getSlot(), ability.buildItem());
                cancelTicker(uuid, ability);
            } else {
                player.getInventory().setItem(ability.getSlot(), ability.buildCooldownItem(left));
            }
        }, 0L, 20L);

        playerTickers.put(ability, task);
    }

    private void cancelTicker(UUID uuid, HotbarAbility ability) {
        Map<HotbarAbility, BukkitTask> playerTickers = cooldownTickers.get(uuid);
        if (playerTickers == null) return;
        BukkitTask t = playerTickers.remove(ability);
        if (t != null && !t.isCancelled()) t.cancel();
        if (playerTickers.isEmpty()) cooldownTickers.remove(uuid);
    }

    private void switchToSlot0(Player player) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> player.getInventory().setHeldItemSlot(0));
    }

    private String buildBar(double fraction, NamedTextColor filled, NamedTextColor empty) {
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        int filledCount = (int) Math.round(fraction * BAR_LENGTH);
        return "█".repeat(filledCount) + "░".repeat(BAR_LENGTH - filledCount);
    }

    private static class PendingConfirm {
        final HotbarAbility ability;
        final UUID          initiator;
        final long          expiresAt;

        PendingConfirm(HotbarAbility ability, UUID initiator) {
            this.ability   = ability;
            this.initiator = initiator;
            this.expiresAt = System.currentTimeMillis() + CONFIRM_WINDOW_MS;
        }
    }
}