package org.falmdev.anieventmanager.minigames.pvpfinal.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.pvpfinal.PvpFinalMiniGame;
import org.falmdev.anieventmanager.minigames.pvpfinal.arena.PvpArena;
import org.falmdev.anieventmanager.minigames.pvpfinal.kit.PvpKit;
import org.falmdev.anieventmanager.minigames.pvpfinal.model.CombatMode;
import org.falmdev.anieventmanager.minigames.pvpfinal.model.CombatState;

import java.time.Duration;
import java.util.*;


public class CombatManager {

    private static final int COUNTDOWN_SECONDS = 5;
    private static final int VICTORY_FIREWORK_BURSTS = 3;
    private static final long VICTORY_FIREWORK_INTERVAL = 15L;
    private static final long POST_VICTORY_DELAY = 60L;

    private final Anieventmanager  plugin;
    private final PvpFinalMiniGame game;

    private CombatState state           = CombatState.IDLE;
    private Combat     currentCombat    = null;
    private BukkitTask preparingTask    = null;
    private BukkitTask countdownTask    = null;
    private final Set<UUID> awaitingRespawn = new HashSet<>();
    private final PvpFinalHologramManager hologramManager;


    public CombatManager(Anieventmanager plugin, PvpFinalMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
        this.hologramManager = new PvpFinalHologramManager(plugin);
    }

    public CombatState getState()         { return state; }
    public Combat      getCurrentCombat() { return currentCombat; }
    public boolean     isActive()         { return state != CombatState.IDLE; }

    public boolean startCombat(CombatMode mode, List<Player> participants,
                               Map<UUID, String> teamByPlayer, PvpKit kit,
                               boolean friendlyFire) {
        if (isActive()) return false;
        if (participants.size() < 2) return false;

        PvpArena arena = game.getArenaManager().getArena();
        if (arena == null || !arena.isReady()) return false;
        if (arena.getSpawnCount() < participants.size() && mode == CombatMode.ONE_VS_ONE) {
            return false;
        }

        currentCombat = new Combat(mode, participants, teamByPlayer, kit, friendlyFire);
        state = CombatState.PREPARING;

        assignSpawnsAndApplyKit(participants, arena, kit);

        hologramManager.show(arena.getHologramLocation());

        broadcast(Component.text("⚔ Combate ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(mode.getLabel() + " ", NamedTextColor.YELLOW))
                .append(Component.text("iniciado · " + participants.size() + " jugadores",
                        NamedTextColor.GRAY)));

        preparingTask = Bukkit.getScheduler().runTaskLater(plugin, this::startCountdown, 20L);
        return true;
    }

    public void stopCombat() {
        if (!isActive()) return;
        endCombat(null, "Combate cancelado por administrador.");
    }

    private void assignSpawnsAndApplyKit(List<Player> participants, PvpArena arena, PvpKit kit) {
        List<Location> spawns = arena.getSpawns();

        for (int i = 0; i < participants.size(); i++) {
            Player p = participants.get(i);
            Location spawn = spawns.get(i % spawns.size());

            p.teleport(spawn);
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
            p.setFoodLevel(20);
            p.setSaturation(20);
            p.setFireTicks(0);
            for (var effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());

            game.getKitManager().apply(kit, p);
        }
    }

    private void startCountdown() {
        state = CombatState.COUNTDOWN;
        final int[] seconds = {COUNTDOWN_SECONDS};

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentCombat == null) {
                countdownTask.cancel();
                return;
            }
            if (seconds[0] > 0) {
                Title title = Title.title(
                        Component.text(String.valueOf(seconds[0]),
                                NamedTextColor.YELLOW, TextDecoration.BOLD),
                        Component.text("Preparate...", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100)));
                for (UUID uuid : currentCombat.getParticipants()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.showTitle(title);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                }
                seconds[0]--;
            } else {
                Title goTitle = Title.title(
                        Component.text("¡PELEEN!", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(200)));
                for (UUID uuid : currentCombat.getParticipants()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.showTitle(goTitle);
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    }
                }
                state = CombatState.FIGHTING;
                countdownTask.cancel();
                countdownTask = null;
            }
        }, 0L, 20L);
    }

    public void onParticipantDeath(Player victim, Player killer) {
        if (currentCombat == null) return;
        if (!currentCombat.isAlive(victim.getUniqueId())) return;

        currentCombat.markDead(victim.getUniqueId());
        awaitingRespawn.add(victim.getUniqueId());

        Component msg;
        if (killer != null && !killer.equals(victim)) {
            msg = Component.text("☠ ", NamedTextColor.RED)
                    .append(Component.text(victim.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" eliminado por ", NamedTextColor.GRAY))
                    .append(Component.text(killer.getName(), NamedTextColor.YELLOW));
        } else {
            msg = Component.text("☠ ", NamedTextColor.RED)
                    .append(Component.text(victim.getName() + " eliminado.", NamedTextColor.WHITE));
        }
        broadcast(msg);

        checkWinCondition();
    }

    private void checkWinCondition() {
        if (currentCombat == null) return;

        int aliveTeams = currentCombat.countAliveTeams();
        if (aliveTeams <= 1) {
            String winnerInfo;
            Set<UUID> survivors = currentCombat.getAlive();
            if (survivors.isEmpty()) {
                winnerInfo = "Sin ganadores (todos murieron)";
            } else if (currentCombat.getMode() == CombatMode.ONE_VS_ONE
                    || currentCombat.getMode() == CombatMode.FFA) {
                List<String> names = new ArrayList<>();
                for (UUID uuid : survivors) {
                    Player p = Bukkit.getPlayer(uuid);
                    names.add(p != null ? p.getName() : uuid.toString().substring(0, 8));
                }
                winnerInfo = String.join(", ", names);
            } else {
                Set<String> aliveTeamIds = currentCombat.getAliveTeamIds();
                if (aliveTeamIds.isEmpty()) {
                    winnerInfo = "Sin ganadores";
                } else {
                    String teamId = aliveTeamIds.iterator().next();
                    var teamOpt = plugin.getTeamManager().getTeam(teamId);
                    String teamName = teamOpt.map(t -> t.getDisplayName()).orElse(teamId);

                    List<String> names = new ArrayList<>();
                    for (UUID uuid : survivors) {
                        Player p = Bukkit.getPlayer(uuid);
                        names.add(p != null ? p.getName() : uuid.toString().substring(0, 8));
                    }
                    winnerInfo = teamName + " (" + String.join(", ", names) + ")";
                }
            }
            endCombat(winnerInfo, null);
        }
    }

    private void endCombat(String winnerInfo, String cancelReason) {
        state = CombatState.ENDING;
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (preparingTask != null) { preparingTask.cancel(); preparingTask = null; }

        Combat finishedCombat = currentCombat;

        if (cancelReason != null) {
            broadcast(Component.text("⚠ " + cancelReason, NamedTextColor.YELLOW));
            finishCombat(finishedCombat);
            return;
        }

        if (winnerInfo != null) {
            Title title = Title.title(
                    Component.text("¡GANADOR!", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text(winnerInfo, NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500)));
            for (Player p : Bukkit.getOnlinePlayers()) p.showTitle(title);
            broadcast(Component.text("🏆 ", NamedTextColor.GOLD)
                    .append(Component.text("Ganador: ", NamedTextColor.GRAY))
                    .append(Component.text(winnerInfo, NamedTextColor.GOLD, TextDecoration.BOLD)));
            launchVictoryFireworks(finishedCombat);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> finishCombat(finishedCombat), POST_VICTORY_DELAY);
    }

    private void launchVictoryFireworks(Combat combat) {
        if (combat == null) return;
        Set<UUID> survivors = combat.getAlive();
        BukkitTask[] holder = new BukkitTask[1];
        int[] count = {0};
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (count[0] >= VICTORY_FIREWORK_BURSTS) {
                holder[0].cancel();
                return;
            }
            for (UUID uuid : survivors) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) spawnFirework(p.getLocation());
            }
            count[0]++;
        }, 0L, VICTORY_FIREWORK_INTERVAL);
    }

    private void spawnFirework(Location location) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        FireworkEffect effect = FireworkEffect.builder()
                .withColor(Color.YELLOW, Color.ORANGE, Color.WHITE)
                .withFade(Color.RED)
                .with(FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .flicker(true)
                .build();
        meta.addEffect(effect);
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }

    private void finishCombat(Combat combat) {
        hologramManager.hide();
        PvpArena arena = game.getArenaManager().getArena();
        if (combat != null && arena != null && arena.getLobby() != null) {
            for (UUID uuid : combat.getParticipants()) {
                if (awaitingRespawn.contains(uuid)) continue;
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;
                p.teleport(arena.getLobby());
                p.setGameMode(GameMode.SURVIVAL);
                game.getKitManager().clearInventory(p);
                p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
                p.setFoodLevel(20);
                for (var effect : p.getActivePotionEffects()) p.removePotionEffect(effect.getType());
            }
        }
        currentCombat = null;
        state = CombatState.IDLE;
    }

    public boolean isAwaitingCombatRespawn(UUID uuid) {
        return awaitingRespawn.contains(uuid);
    }

    public void handleCombatRespawn(Player player, PlayerRespawnEvent event) {
        UUID uuid = player.getUniqueId();
        if (!awaitingRespawn.remove(uuid)) return;

        PvpArena arena = game.getArenaManager().getArena();
        Location lobby = arena != null ? arena.getLobby() : null;
        if (lobby != null) event.setRespawnLocation(lobby);

        GameMode targetMode = (state == CombatState.ENDING || state == CombatState.IDLE)
                ? GameMode.SURVIVAL : GameMode.SPECTATOR;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.setGameMode(targetMode);
            game.getKitManager().clearInventory(player);
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            player.setSaturation(20);
            player.setFireTicks(0);
            for (var effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        });
    }

    private void broadcast(Component msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
    }
}