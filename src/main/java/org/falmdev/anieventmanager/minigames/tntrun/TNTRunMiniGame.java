package org.falmdev.anieventmanager.minigames.tntrun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.time.Duration;
import java.util.*;

public class TNTRunMiniGame {

    public enum State { IDLE, LOBBY, COUNTDOWN, RUNNING, FINISHED }

    private final Anieventmanager plugin;
    private final TNTRunConfig    config;
    private TNTRunArena    arena;
    private TNTRunListener gameListener;

    private State state = State.IDLE;

    private final Set<UUID>       activePlayers = new HashSet<>();
    private final List<EventTeam> aliveTeams    = new ArrayList<>();
    private final List<EventTeam> elimination   = new ArrayList<>();

    private BukkitTask countdownTask;
    private long gameStartTime = 0;

    public TNTRunMiniGame(Anieventmanager plugin) {
        this.plugin = plugin;
        this.config = new TNTRunConfig(plugin);
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    public boolean sendToLobby() {
        if (config.validate() != null) return false;

        state = State.LOBBY;
        Location lobby = config.getLobbySpawn();

        for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
            for (Player p : team.getOnlinePlayers()) {
                p.teleport(lobby);
                p.setGameMode(GameMode.ADVENTURE);
                p.sendMessage(Component.text("Bienvenido al lobby de TNT Run. Espera el inicio.", NamedTextColor.YELLOW));
            }
        }
        return true;
    }

    public boolean start() {
        if (state != State.LOBBY && state != State.IDLE) return false;
        if (config.validate() != null) return false;

        activePlayers.clear();
        aliveTeams.clear();
        elimination.clear();

        List<Location> spawns = config.getPlayerSpawns();
        List<EventTeam> teams = new ArrayList<>(plugin.getTeamManager().getAllTeams());
        if (teams.isEmpty()) return false;

        // Generar arena con la configuración actual
        arena = new TNTRunArena(config.getArenaCenter(), config.buildArenaConfig());
        arena.generate();

        // Teletransportar jugadores
        int spawnIndex = 0;
        for (EventTeam team : teams) {
            if (team.getOnlinePlayers().isEmpty()) continue;
            aliveTeams.add(team);
            for (Player p : team.getOnlinePlayers()) {
                activePlayers.add(p.getUniqueId());
                p.setGameMode(GameMode.SURVIVAL);

                Location spawn = spawns.size() > spawnIndex
                        ? spawns.get(spawnIndex)
                        : config.getArenaCenter().clone().add(0, arena.getPlayerSpawnY() - config.getArenaCenter().getY(), 0);
                p.teleport(spawn);
                spawnIndex++;

                applyNightVision(p);
            }
        }

        // Registrar listener
        gameListener = new TNTRunListener(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(gameListener, plugin);

        startCountdown();
        return true;
    }

    public void forceStop() {
        cancelTasks();
        broadcastAll(Component.text("El TNT Run fue detenido por un admin.", NamedTextColor.RED));
        finish(null);
    }

    // ── Cuenta regresiva ──────────────────────────────────────────────────────

    private void startCountdown() {
        state = State.COUNTDOWN;
        int[] seconds = { config.getCountdownSeconds() };

        broadcastAll(Component.text("¡TNT Run comienza en " + seconds[0] + " segundos!", NamedTextColor.GOLD));

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (seconds[0] <= 0) {
                countdownTask.cancel();
                beginGame();
                return;
            }
            Title title = Title.title(
                    Component.text(String.valueOf(seconds[0]), NamedTextColor.YELLOW),
                    Component.text("¡No te caigas!", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(100))
            );
            forEachActive(p -> p.showTitle(title));
            seconds[0]--;
        }, 0L, 20L);
    }

    private void beginGame() {
        state = State.RUNNING;
        gameStartTime = System.currentTimeMillis();

        if (gameListener != null) gameListener.startTick();

        Title go = Title.title(
                Component.text("¡YA!", NamedTextColor.GREEN),
                Component.text(config.isDoubleJumpEnabled()
                                ? "¡Corre! §7(doble salto activo)"
                                : "¡Corre!",
                        NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(200))
        );
        forEachActive(p -> p.showTitle(go));
        broadcastAll(Component.text("━━━ ¡TNT RUN COMENZÓ! ━━━", NamedTextColor.GREEN));
    }

    // ── Eliminación ───────────────────────────────────────────────────────────

    public void eliminatePlayer(Player player) {
        if (!activePlayers.remove(player.getUniqueId())) return;

        // Limpiar estado de doble salto
        if (gameListener != null) gameListener.clearDoubleJumpState(player);

        removeNightVision(player);

        Location spectator = config.getSpectatorSpawn();
        if (spectator != null) player.teleport(spectator);
        player.setGameMode(GameMode.SPECTATOR);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        applyNightVision(player);
        player.sendMessage(Component.text("Caíste. Ahora eres espectador.", NamedTextColor.RED));

        aliveTeams.removeIf(t -> {
            boolean hasNoActivePlayers = t.getOnlinePlayers().stream()
                    .noneMatch(p -> activePlayers.contains(p.getUniqueId()));
            if (hasNoActivePlayers && !elimination.contains(t)) {
                elimination.add(0, t);
            }
            return hasNoActivePlayers;
        });

        Optional<EventTeam> teamOpt = plugin.getTeamManager().getTeamOf(player);
        if (teamOpt.isEmpty()) { checkWinCondition(); return; }

        EventTeam team = teamOpt.get();
        boolean teamEliminated = team.getOnlinePlayers().stream()
                .noneMatch(p -> activePlayers.contains(p.getUniqueId()));

        if (teamEliminated && aliveTeams.remove(team)) {
            elimination.add(0, team);
            broadcastAll(Component.text("💀 El equipo ", NamedTextColor.RED)
                    .append(Component.text(team.getDisplayName(), team.getColor()))
                    .append(Component.text(" fue eliminado. Quedan " + aliveTeams.size() + " equipos.", NamedTextColor.RED)));
            checkWinCondition();
        }
    }

    public void checkWinCondition() {
        if (state != State.RUNNING) return;

        List<EventTeam> teamsWithPlayers = aliveTeams.stream()
                .filter(t -> t.getOnlinePlayers().stream()
                        .anyMatch(p -> activePlayers.contains(p.getUniqueId())))
                .toList();

        if (teamsWithPlayers.size() <= 1) {
            EventTeam winner = teamsWithPlayers.isEmpty() ? null : teamsWithPlayers.get(0);
            finish(winner);
            return;
        }

        if (activePlayers.size() == 1) {
            UUID lastUUID = activePlayers.iterator().next();
            Player lastPlayer = Bukkit.getPlayer(lastUUID);
            if (lastPlayer != null) {
                plugin.getTeamManager().getTeamOf(lastPlayer).ifPresent(this::finish);
            }
        }
    }

    // ── Fin del juego ─────────────────────────────────────────────────────────

    private void finish(EventTeam winner) {
        cancelTasks();
        state = State.FINISHED;

        if (gameListener != null) gameListener.stopTick();
        gameStartTime = 0;

        Bukkit.getOnlinePlayers().forEach(this::removeNightVision);

        List<EventTeam> ranking = new ArrayList<>();
        if (winner != null) ranking.add(winner);
        ranking.addAll(elimination);

        if (winner != null) {
            Title winTitle = Title.title(
                    Component.text("🏆 " + winner.getDisplayName(), winner.getColor()),
                    Component.text("¡Ganó el TNT Run!", NamedTextColor.GOLD),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))
            );
            Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(winTitle));
            broadcastAll(Component.text("🏆 ¡El equipo ", NamedTextColor.GOLD)
                    .append(Component.text(winner.getDisplayName(), winner.getColor()))
                    .append(Component.text(" ganó el TNT Run!", NamedTextColor.GOLD)));
        }

        for (int i = 0; i < ranking.size(); i++) {
            EventTeam team = ranking.get(i);
            int score = config.getScoreForPlace(i + 1);
            plugin.getScoreManager().addScore(team, score);
            broadcastAll(Component.text("  +" + score + " pts → ", NamedTextColor.YELLOW)
                    .append(Component.text(team.getDisplayName(), team.getColor())));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (arena != null) arena.restore();
            returnToMainLobby();
            state = State.IDLE;
        }, 100L);
    }

    private void returnToMainLobby() {
        Location lobby = config.getLobbySpawn();
        if (lobby == null) return;
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(lobby);
            removeNightVision(p);
        });
        activePlayers.clear();
        aliveTeams.clear();
    }

    // ── Visión nocturna ───────────────────────────────────────────────────────

    private void applyNightVision(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION,
                PotionEffect.INFINITE_DURATION,
                0, true, false, false
        ));
    }

    private void removeNightVision(Player player) {
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    public boolean isActivePlayer(Player player) { return activePlayers.contains(player.getUniqueId()); }
    public boolean isCountingDown() { return state == State.COUNTDOWN; }
    public boolean isRunning()      { return state == State.RUNNING; }
    public State   getState()       { return state; }
    public TNTRunConfig getConfig() { return config; }
    public TNTRunArena  getArena()  { return arena; }

    public List<EventTeam> getAliveTeams()    { return Collections.unmodifiableList(aliveTeams); }
    public int getActivePlayerCount()         { return activePlayers.size(); }
    public int getAliveTeamCount()            { return aliveTeams.size(); }

    public long getElapsedSeconds() {
        return gameStartTime > 0 ? (System.currentTimeMillis() - gameStartTime) / 1000 : 0;
    }

    public String getElapsedFormatted() {
        long secs = getElapsedSeconds();
        return String.format("%02d:%02d", secs / 60, secs % 60);
    }

    private void forEachActive(java.util.function.Consumer<Player> action) {
        activePlayers.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(action);
    }

    private void broadcastAll(Component message) {
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(message));
    }

    private void cancelTasks() {
        if (countdownTask != null && !countdownTask.isCancelled()) countdownTask.cancel();
    }
}