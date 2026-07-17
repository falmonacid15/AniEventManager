package org.falmdev.anieventmanager.minigames.tntrun;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.managers.MiniGame;
import org.falmdev.anieventmanager.model.EventTeam;

import java.time.Duration;
import java.util.*;

public class TNTRunMiniGame implements MiniGame {

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

        arena = new TNTRunArena(config.getArenaCenter(), config.buildArenaConfig());
        arena.generate();

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

        if (gameListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(gameListener);
            gameListener = null;
        }

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

    public void eliminatePlayer(Player player) {
        if (!activePlayers.remove(player.getUniqueId())) return;

        if (gameListener != null) gameListener.clearDoubleJumpState(player);
        applyNightVision(player);
        player.setFireTicks(0);

        player.setAllowFlight(true);
        player.setFlying(true);
        player.setGameMode(GameMode.SPECTATOR);

        Location spectator = config.getSpectatorSpawn();
        if (spectator != null) player.teleport(spectator);

        player.sendMessage(Component.text("Caíste. Ahora eres espectador.", NamedTextColor.RED));


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


    private void finish(EventTeam winner) {
        cancelTasks();
        state = State.FINISHED;

        if (gameListener != null) {
            gameListener.stopTick();
            gameListener = null;
        }
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

        final EventTeam winnerFinal = winner;
        final BukkitTask[] fireworkTask = { null };

        if (winnerFinal != null) {
            org.bukkit.Color teamColor =
                    org.falmdev.anieventmanager.utils.TeamColorUtil.toArmorColor(winnerFinal.getColor());

            scheduleFireworkSalvo(winnerFinal, teamColor, fireworkTask, 10L);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (fireworkTask[0] != null && !fireworkTask[0].isCancelled()) {
                fireworkTask[0].cancel();
            }
            if (arena != null) arena.restore();
            returnToMainLobby();
            state = State.IDLE;
        }, 30 * 20L);
    }

    private void scheduleFireworkSalvo(EventTeam winner,
                                       org.bukkit.Color teamColor,
                                       BukkitTask[] taskHolder,
                                       long delayTicks) {
        taskHolder[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<org.bukkit.entity.Player> members = winner.getOnlinePlayers();
            if (members.isEmpty()) return;

            int count = 3 + (int) (Math.random() * 2);
            for (int i = 0; i < count; i++) {

                org.bukkit.entity.Player target = members.get((int) (Math.random() * members.size()));
                if (target.isOnline()) {
                    spawnWinnerFirework(target, teamColor);
                }
            }

            long nextDelay = (100L + (long) (Math.random() * 100L));
            scheduleFireworkSalvo(winner, teamColor, taskHolder, nextDelay);

        }, delayTicks);
    }

    private void spawnWinnerFirework(org.bukkit.entity.Player player, org.bukkit.Color teamColor) {
        double offsetX = (Math.random() - 0.5) * 3.0;
        double offsetZ = (Math.random() - 0.5) * 3.0;

        org.bukkit.Location spawnLoc = player.getLocation().add(offsetX, 0, offsetZ);

        org.bukkit.entity.Firework fw = player.getWorld().spawn(spawnLoc, org.bukkit.entity.Firework.class);

        org.bukkit.FireworkEffect.Type type = switch ((int) (Math.random() * 3)) {
            case 0  -> org.bukkit.FireworkEffect.Type.STAR;
            case 1  -> org.bukkit.FireworkEffect.Type.BURST;
            default -> org.bukkit.FireworkEffect.Type.BALL_LARGE;
        };

        org.bukkit.Color fadeColor = org.bukkit.Color.fromRGB(
                Math.min(255, teamColor.getRed()   + 80),
                Math.min(255, teamColor.getGreen() + 80),
                Math.min(255, teamColor.getBlue()  + 80)
        );

        org.bukkit.FireworkEffect effect = org.bukkit.FireworkEffect.builder()
                .with(type)
                .withColor(teamColor, teamColor)
                .withFade(fadeColor)
                .trail(true)
                .flicker(Math.random() < 0.5)
                .build();

        org.bukkit.inventory.meta.FireworkMeta meta =
                (org.bukkit.inventory.meta.FireworkMeta) fw.getFireworkMeta();
        meta.addEffect(effect);
        meta.setPower(2);
        fw.setFireworkMeta(meta);
    }

    public boolean isBelowArena(Player player) {
        if (arena == null) return false;
        int minY = arena.getCenter().getBlockY() - 15;
        return player.getLocation().getY() < minY;
    }

    private void returnToMainLobby() {
        Location lobby = config.getLobbySpawn();
        if (lobby == null) return;
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.setAllowFlight(false);
            p.setFlying(false);
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(lobby);
            removeNightVision(p);
        });
        activePlayers.clear();
        aliveTeams.clear();
    }

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

    public int getPlayerFloor(Player player) {
        if (arena == null || !isStrictlyRunning()) return -1;
        int py = player.getLocation().getBlockY() - 1;
        int baseY = arena.getCenter().getBlockY();
        TNTRunArena.ArenaConfig cfg = arena.getArenaConfig();

        for (int i = cfg.layerCount() - 1; i >= 0; i--) {
            int layerSandY = baseY + 10 + i * (2 + cfg.layerGap()) + 1;
            if (py >= layerSandY - 2) {
                return cfg.layerCount() - i;
            }
        }
        return cfg.layerCount();
    }

    public int getTotalFloors() {
        if (arena == null) return 0;
        return arena.getArenaConfig().layerCount();
    }

    private int getFloorSandY(int floorIndex) {
        if (arena == null) return -1;
        int baseY = arena.getCenter().getBlockY();
        TNTRunArena.ArenaConfig cfg = arena.getArenaConfig();
        int layerIdx = cfg.layerCount() - floorIndex;
        if (layerIdx < 0 || layerIdx >= cfg.layerCount()) return -1;
        return baseY + 10 + layerIdx * (2 + cfg.layerGap()) + 1;
    }

    public int getSandCountOnFloor(int floorIndex) {
        if (arena == null || !isStrictlyRunning()) return 0;
        int targetY = getFloorSandY(floorIndex);
        if (targetY < 0) return 0;
        int count = 0;
        for (Location loc : arena.getSandBlocks()) {
            if (loc.getBlockY() == targetY && loc.getBlock().getType() == Material.SAND) {
                count++;
            }
        }
        return count;
    }

    public int getTotalSandOnFloor(int floorIndex) {
        if (arena == null) return 0;
        int targetY = getFloorSandY(floorIndex);
        if (targetY < 0) return 0;
        int count = 0;
        for (Location loc : arena.getSandBlocks()) {
            if (loc.getBlockY() == targetY) count++;
        }
        return count;
    }

    public List<Player> getPlayersOnFloor(int floorIndex) {
        if (arena == null || !isStrictlyRunning()) return List.of();
        int targetY = getFloorSandY(floorIndex);
        if (targetY < 0) return List.of();
        List<Player> result = new ArrayList<>();
        for (UUID uuid : activePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            int py = p.getLocation().getBlockY() - 1;
            TNTRunArena.ArenaConfig cfg = arena.getArenaConfig();
            if (py >= targetY - 1 && py <= targetY + cfg.layerGap() + 1) {
                result.add(p);
            }
        }
        return result;
    }

    public TNTRunListener getGameListener() { return gameListener; }

    public boolean isActivePlayer(Player player) { return activePlayers.contains(player.getUniqueId()); }
    public boolean isCountingDown() { return state == State.COUNTDOWN; }
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

    @Override public String getId()          { return "tntrun"; }
    @Override public String getDisplayName() { return "TNT Run"; }
    @Override public String getStateName()   { return state.name(); }
    @Override public boolean isIdle()        { return state == State.IDLE; }

    @Override
    public boolean isRunning() {
        return state == State.RUNNING || state == State.COUNTDOWN;
    }

    public boolean isStrictlyRunning() {
        return state == State.RUNNING;
    }

    @Override
    public void reloadConfig() {
        config.reload();
    }

    @Override
    public String validateConfig() {
        return config.validate();
    }
}