package org.falmdev.anieventmanager.minigames.frozenheist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.managers.MiniGame;
import org.falmdev.anieventmanager.model.EventTeam;
import org.falmdev.anieventmanager.utils.TeamColorUtil;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class FrozenHeistMiniGame implements MiniGame {

    public enum State { IDLE, RUNNING, FINISHED }

    private final Anieventmanager plugin;
    private final FrozenHeistConfig config;
    private FrozenHeistListener listener;

    private State state = State.IDLE;

    private final Map<String, TeamHeistData> teamData     = new LinkedHashMap<>();
    private final Map<UUID, PlayerState>      playerStates = new HashMap<>();

    private FlagManager flagManager;

    private BukkitTask timerTask;
    private BukkitTask scoreboardTask;
    private int timeLeftSeconds;

    public FrozenHeistMiniGame(Anieventmanager plugin) {
        this.plugin = plugin;
        this.config = new FrozenHeistConfig(plugin);
    }

    public void openAdminGUI(Player player) {
        plugin.getFrozenHeistAdminGUI().open(player);
    }

    // ── MiniGame interface ────────────────────────────────────────────────────

    @Override public String getId()          { return "frozenheist"; }
    @Override public String getDisplayName() { return "Frozen Heist"; }
    @Override public String getStateName()   { return state.name(); }
    @Override public boolean isIdle()        { return state == State.IDLE; }

    @Override
    public boolean isRunning() {
        return state == State.RUNNING;
    }

    /**
     * Frozen Heist no tiene lobby previo: sendToLobby inicia directamente.
     */
    @Override
    public boolean sendToLobby() {
        return start();
    }

    @Override
    public void reloadConfig() {
        config.reload();
    }

    @Override
    public String validateConfig() {
        Collection<EventTeam> teams = plugin.getTeamManager().getAllTeams();
        return config.validate(teams);
    }

    // ── Inicio ────────────────────────────────────────────────────────────────

    @Override
    public boolean start() {
        if (state != State.IDLE) return false;

        Collection<EventTeam> teams = plugin.getTeamManager().getAllTeams();
        if (teams.isEmpty()) return false;

        String error = config.validate(teams);
        if (error != null) return false;

        teamData.clear();
        playerStates.clear();

        for (EventTeam team : teams) {
            if (team.getOnlinePlayers().isEmpty()) continue;
            TeamHeistData data = new TeamHeistData(team);
            config.applyToTeamData(team.getId(), data);
            teamData.put(team.getId(), data);
        }

        flagManager = new FlagManager(plugin, teamData);
        flagManager.initAll();

        if (listener != null) org.bukkit.event.HandlerList.unregisterAll(listener);
        listener = new FrozenHeistListener(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        for (TeamHeistData data : teamData.values()) {
            for (Player p : data.getTeam().getOnlinePlayers()) {
                playerStates.put(p.getUniqueId(), new PlayerState(p.getUniqueId()));
                teleportToBase(p, data);
                equipPlayer(p);
            }
        }

        state = State.RUNNING;
        timeLeftSeconds = config.getDurationMinutes() * 60;

        broadcastAll(Component.text("━━━ ¡FROZEN HEIST COMENZÓ! ━━━", NamedTextColor.AQUA));
        broadcastAll(Component.text("Roba las banderas enemigas y llévalas a tu zona de captura.",
                NamedTextColor.YELLOW));
        broadcastAll(Component.text("Tiempo: " + config.getDurationMinutes() + " minutos.",
                NamedTextColor.GRAY));

        startTimer();
        startScoreboardUpdater();
        return true;
    }

    @Override
    public void forceStop() {
        finish(true);
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private void startTimer() {
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            timeLeftSeconds--;

            if (timeLeftSeconds == 300)
                broadcastAll(Component.text("⏱ Quedan 5 minutos.", NamedTextColor.YELLOW));
            else if (timeLeftSeconds == 60)
                broadcastAll(Component.text("⏱ ¡Queda 1 minuto!", NamedTextColor.RED));
            else if (timeLeftSeconds <= 10 && timeLeftSeconds > 0)
                broadcastAll(Component.text("⏱ " + timeLeftSeconds + "...", NamedTextColor.RED));

            if (timeLeftSeconds <= 0) {
                timerTask.cancel();
                finish(false);
            }
        }, 20L, 20L);
    }

    // ── Scoreboard updater ────────────────────────────────────────────────────

    private void startScoreboardUpdater() {
        scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.RUNNING) return;

            String time = String.format("%02d:%02d", timeLeftSeconds / 60, timeLeftSeconds % 60);

            playerStates.forEach((uuid, ps) -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) return;

                Component bar;

                if (ps.isFrozen()) {
                    bar = ps.isBeingRescued()
                            ? Component.text("❄ Siendo rescatado... " + ps.getFrozenSecondsLeft() + "s", NamedTextColor.AQUA)
                            : Component.text("❄ Congelado — " + ps.getFrozenSecondsLeft() + "s  |  Espera a tu compañero", NamedTextColor.AQUA);
                } else if (ps.isRescuing()) {
                    bar = Component.text("⏳ Rescatando... mantén el click", NamedTextColor.YELLOW);
                } else if (ps.isCarryingFlag()) {
                    String flagTeamId  = ps.getCarryingFlagOf();
                    String playerTeamId = plugin.getTeamManager().getTeamOf(p)
                            .map(t -> t.getId()).orElse("");
                    boolean isOwn = flagTeamId.equals(playerTeamId);
                    TeamHeistData flagData = teamData.get(flagTeamId);
                    String flagTeamName = flagData != null ? flagData.getTeam().getDisplayName() : flagTeamId;
                    bar = isOwn
                            ? Component.text("🚩 Llevas tu bandera — ¡llévala a tu base!", NamedTextColor.GREEN)
                            : Component.text("🚩 Llevas la bandera de " + flagTeamName + " — ¡entrégala!", NamedTextColor.GOLD);
                } else {
                    bar = Component.text("⏱ " + time, NamedTextColor.YELLOW);
                }

                p.sendActionBar(bar);
            });

        }, 0L, 20L);
    }

    // ── Fin ───────────────────────────────────────────────────────────────────

    private void finish(boolean cancelled) {
        if (timerTask      != null && !timerTask.isCancelled())      timerTask.cancel();
        if (scoreboardTask != null && !scoreboardTask.isCancelled()) scoreboardTask.cancel();
        if (listener != null) {
            listener.cleanup();
            org.bukkit.event.HandlerList.unregisterAll(listener);
        }
        if (flagManager != null) flagManager.returnAll();

        state = State.FINISHED;

        if (!cancelled) {
            List<TeamHeistData> ranking = teamData.values().stream()
                    .sorted((a, b) -> Integer.compare(b.getPoints(), a.getPoints()))
                    .toList();

            broadcastAll(Component.text("━━━ FIN — FROZEN HEIST ━━━", NamedTextColor.GOLD));
            for (int i = 0; i < ranking.size(); i++) {
                TeamHeistData d = ranking.get(i);
                String medal = switch (i) { case 0 -> "🥇"; case 1 -> "🥈"; case 2 -> "🥉"; default -> (i+1) + "."; };
                broadcastAll(Component.text("  " + medal + " ", NamedTextColor.WHITE)
                        .append(Component.text(d.getTeam().getDisplayName(), d.getTeam().getColor()))
                        .append(Component.text(" — " + d.getPoints() + " pts", NamedTextColor.YELLOW)));
            }

            if (!ranking.isEmpty()) {
                TeamHeistData winner = ranking.get(0);
                Title winTitle = Title.title(
                        Component.text("🏆 " + winner.getTeam().getDisplayName(), winner.getTeam().getColor()),
                        Component.text("¡Ganó el Frozen Heist!", NamedTextColor.GOLD),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(500))
                );
                Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(winTitle));
            }
        }

        playerStates.keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(this::cleanupPlayer);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            teamData.clear();
            playerStates.clear();
            state = State.IDLE;
        }, 20L);
    }

    // ── Respawn ───────────────────────────────────────────────────────────────

    public void respawn(Player player) {
        PlayerState ps = playerStates.get(player.getUniqueId());
        if (ps == null) return;

        if (ps.isCarryingFlag()) {
            flagManager.dropFlag(ps.getCarryingFlagOf(), player.getLocation());
            ps.clearFlag();
            removeSlownessEffect(player);
            clearFlagHelmet(player);
        }

        if (ps.isFrozen()) {
            ps.unfreeze();
            restoreMovement(player);
        }

        ps.resetHits();

        if (ps.isBeingRescued()) {
            Player rescuer = Bukkit.getPlayer(ps.getBeingRescuedBy());
            if (rescuer != null) {
                PlayerState rps = playerStates.get(rescuer.getUniqueId());
                if (rps != null) rps.cancelRescue();
            }
        }

        plugin.getTeamManager().getTeamOf(player).ifPresent(team -> {
            TeamHeistData data = teamData.get(team.getId());
            if (data != null && data.getBaseSpawn() != null) {
                teleportToBase(player, data);
                equipPlayer(player);
            }
        });

        player.setHealth(20);
        player.setFoodLevel(20);
        player.setFireTicks(0);
    }

    // ── Congelación ───────────────────────────────────────────────────────────

    public void freezePlayer(Player player) {
        PlayerState ps = playerStates.get(player.getUniqueId());
        if (ps == null || ps.isFrozen()) return;

        if (ps.isCarryingFlag()) {
            String flagTeamId = ps.getCarryingFlagOf();
            flagManager.dropFlag(flagTeamId, player.getLocation());
            ps.clearFlag();
            removeSlownessEffect(player);
            clearFlagHelmet(player);
            broadcastAll(Component.text("🚩 ", NamedTextColor.YELLOW)
                    .append(Component.text(player.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" soltó la bandera al quedar congelado.", NamedTextColor.YELLOW)));
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PlayerState current = playerStates.get(player.getUniqueId());
            if (current != null && current.isFrozen()) {
                current.unfreeze();
                restoreMovement(player);
                player.sendMessage(Component.text("✦ Ya puedes moverte.", NamedTextColor.AQUA));
            }
        }, (PlayerState.FREEZE_DURATION_MS / 50));

        ps.freeze(task);
        applyFreezeEffects(player);

        player.sendMessage(Component.text("❄ ¡Quedaste congelado por "
                + (PlayerState.FREEZE_DURATION_MS / 1000) + " segundos!", NamedTextColor.AQUA));
        broadcastNearby(player, Component.text("❄ ", NamedTextColor.AQUA)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(" quedó congelado.", NamedTextColor.AQUA)));
    }

    public void unfreezePlayer(Player player, Player rescuer) {
        PlayerState ps = playerStates.get(player.getUniqueId());
        if (ps == null || !ps.isFrozen()) return;

        ps.unfreeze();
        restoreMovement(player);

        player.sendMessage(Component.text("✦ ", NamedTextColor.GREEN)
                .append(Component.text(rescuer.getName(), NamedTextColor.WHITE))
                .append(Component.text(" te rescató.", NamedTextColor.GREEN)));
        rescuer.sendMessage(Component.text("✦ Rescataste a ", NamedTextColor.GREEN)
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.GREEN)));
    }

    // ── Puntos ────────────────────────────────────────────────────────────────

    public void addPoints(String teamId, int points, String reason) {
        TeamHeistData data = teamData.get(teamId);
        if (data == null) return;
        data.addPoints(points);
        broadcastAll(Component.text("+" + points + " pts → ", NamedTextColor.GOLD)
                .append(Component.text(data.getTeam().getDisplayName(), data.getTeam().getColor()))
                .append(Component.text("  (" + reason + ")", NamedTextColor.GRAY)));
    }

    // ── Efectos ───────────────────────────────────────────────────────────────

    private void applyFreezeEffects(Player p) {
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false, false));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 128, false, false, false));
        p.setFreezeTicks((int)(PlayerState.FREEZE_DURATION_MS / 50));
    }

    private void restoreMovement(Player p) {
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
        p.setFreezeTicks(0);
        PlayerState ps = playerStates.get(p.getUniqueId());
        if (ps != null && ps.isCarryingFlag()) applyFlagSlowness(p);
    }

    public void applyFlagSlowness(Player p) {
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 0, false, false, false));
    }

    public void removeSlownessEffect(Player p) {
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
    }

    private void equipPlayer(Player p) {
        p.getInventory().clear();
        ItemStack snowball = new ItemStack(org.bukkit.Material.SNOWBALL, 16);
        p.getInventory().setItem(0, snowball);
        p.setGameMode(GameMode.SURVIVAL);

        plugin.getTeamManager().getTeamOf(p).ifPresent(team -> {
            org.bukkit.Color armorColor = TeamColorUtil.toArmorColor(team.getColor());
            p.getInventory().setChestplate(buildLeatherArmor(org.bukkit.Material.LEATHER_CHESTPLATE, armorColor));
            p.getInventory().setLeggings(buildLeatherArmor(org.bukkit.Material.LEATHER_LEGGINGS, armorColor));
            p.getInventory().setBoots(buildLeatherArmor(org.bukkit.Material.LEATHER_BOOTS, armorColor));
            p.getInventory().setHelmet(null);
        });
    }

    public void equipFlagHelmet(Player p, String flagTeamId) {
        TeamHeistData data = teamData.get(flagTeamId);
        if (data == null) return;
        ItemStack banner = flagManager.buildPublicFlagItem(flagTeamId, data.getTeam());
        p.getInventory().setHelmet(banner);
    }

    public void clearFlagHelmet(Player p) {
        p.getInventory().setHelmet(null);
    }

    private ItemStack buildLeatherArmor(org.bukkit.Material material, org.bukkit.Color color) {
        ItemStack item = new ItemStack(material);
        org.bukkit.inventory.meta.LeatherArmorMeta meta =
                (org.bukkit.inventory.meta.LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        meta.setUnbreakable(true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    private void teleportToBase(Player p, TeamHeistData data) {
        if (data.getBaseSpawn() != null) p.teleport(data.getBaseSpawn());
    }

    private void cleanupPlayer(Player p) {
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
        p.getInventory().setHelmet(null);
        p.getInventory().setChestplate(null);
        p.getInventory().setLeggings(null);
        p.getInventory().setBoots(null);
        p.getInventory().clear();
    }

    private void broadcastAll(Component msg) {
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
    }

    private void broadcastNearby(Player center, Component msg) {
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(center.getWorld())
                        && p.getLocation().distanceSquared(center.getLocation()) <= 400)
                .forEach(p -> p.sendMessage(msg));
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public State getState()                         { return state; }
    public FrozenHeistConfig getConfig()            { return config; }
    public FlagManager getFlagManager()             { return flagManager; }
    public Map<String, TeamHeistData> getTeamData() { return Collections.unmodifiableMap(teamData); }
    public PlayerState getPlayerState(UUID uuid)    { return playerStates.get(uuid); }
    public int getTimeLeftSeconds()                 { return timeLeftSeconds; }

    public String getTimeLeftFormatted() {
        return String.format("%02d:%02d", timeLeftSeconds / 60, timeLeftSeconds % 60);
    }

    public boolean isActivePlayer(Player p) {
        return playerStates.containsKey(p.getUniqueId());
    }
}