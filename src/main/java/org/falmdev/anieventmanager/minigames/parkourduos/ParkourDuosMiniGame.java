package org.falmdev.anieventmanager.minigames.parkourduos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.managers.MiniGame;
import org.falmdev.anieventmanager.model.EventTeam;

import java.time.Duration;
import java.util.*;

public class ParkourDuosMiniGame implements MiniGame {

    public enum State { IDLE, COUNTDOWN, RUNNING, FINISHED }

    private static final int TEAMS_TO_TRIGGER_END = 3;

    private final Anieventmanager   plugin;
    private final ParkourDuosConfig config;
    private final ChainManager      chainManager;

    private CheckpointManager checkpointManager;
    private ParkourDuosListener gameListener;
    private AbilityManager abilityManager;

    private State state = State.IDLE;

    private final Map<String, TeamParkourData> teamData = new HashMap<>();

    private int finishCounter = 0;

    private BukkitTask timerTask;
    private BukkitTask countdownTask;
    private int timeLeftSeconds;
    private boolean finishing = false;

    public ParkourDuosMiniGame(Anieventmanager plugin) {
        this.plugin       = plugin;
        this.config       = new ParkourDuosConfig(plugin);
        this.chainManager = new ChainManager(plugin, true, config.getChainMaxDistance());
    }

    public void openAdminGUI(Player player) {
        plugin.getParkourDuosAdminGUI().open(player);
    }

    @Override public String getId()          { return "parkourduos"; }
    @Override public String getDisplayName() { return "Parkour Duos"; }
    @Override public String getStateName()   { return state.name(); }
    @Override public boolean isIdle()        { return state == State.IDLE; }

    @Override
    public boolean isRunning() {
        return state == State.RUNNING || state == State.COUNTDOWN;
    }

    @Override
    public boolean sendToLobby() {
        Location lobby = config.getLobby();
        if (lobby == null) return false;
        for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
            for (Player p : team.getOnlinePlayers()) {
                p.teleport(lobby);
                p.setGameMode(GameMode.ADVENTURE);
            }
        }
        return true;
    }

    @Override
    public void reloadConfig() { config.reload(); }

    @Override
    public String validateConfig() {
        String global = config.validateGlobal();
        if (global != null) return global;
        for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
            String err = config.validateTeam(team.getId());
            if (err != null) return err;
        }
        return null;
    }

    @Override
    public boolean start() {
        if (state != State.IDLE) return false;

        String globalError = config.validateGlobal();
        if (globalError != null) {
            plugin.getLogger().warning("[ParkourDuos] " + globalError);
            return false;
        }

        Collection<EventTeam> teams = plugin.getTeamManager().getAllTeams();
        if (teams.isEmpty()) return false;

        for (EventTeam team : teams) {
            String err = config.validateTeam(team.getId());
            if (err != null) {
                plugin.getLogger().warning("[ParkourDuos] " + err);
                return false;
            }
        }

        finishing    = false;
        finishCounter = 0;
        teamData.clear();

        Location lobby = config.getLobby();
        for (EventTeam team : teams) {
            for (Player p : team.getOnlinePlayers()) {
                if (lobby != null) p.teleport(lobby);
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
            }
        }

        for (EventTeam team : teams) {
            List<ParkourCheckpoint> cps = config.getCheckpoints(team.getId());
            teamData.put(team.getId(), new TeamParkourData(team, cps));
        }

        if (gameListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(gameListener);
        }
        gameListener = new ParkourDuosListener(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(gameListener, plugin);

        if (abilityManager != null) abilityManager.cleanupAll();
        abilityManager = new AbilityManager(plugin, this);

        state = State.COUNTDOWN;
        showIntroAndCountdown();
        return true;
    }

    @Override
    public void forceStop() {
        cleanup();
        broadcastAll(Component.text("Parkour Duos detenido por un admin.", NamedTextColor.RED));

        Location lobby = config.getLobby();
        for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
            for (Player p : team.getOnlinePlayers()) {
                p.getInventory().clear();
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.setFlying(false);
                if (lobby != null) p.teleport(lobby);
            }
        }

        state = State.FINISHED;
        teamData.clear();
        Bukkit.getScheduler().runTaskLater(plugin, () -> state = State.IDLE, 20L);
    }

    private void showIntroAndCountdown() {
        Title intro = Title.title(
                Component.text("PARKOUR DUOS", NamedTextColor.GOLD),
                Component.text("¡Llega al final con tu compañero!", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))
        );
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(intro));
        broadcastAll(Component.text("━━━ PARKOUR DUOS ━━━", NamedTextColor.GOLD));
        broadcastAll(Component.text(
                "Completa el recorrido con tu compañero antes de que acabe el tiempo.",
                NamedTextColor.YELLOW));
        broadcastAll(Component.text(
                "¡No se alejen demasiado — están encadenados!", NamedTextColor.GRAY));

        Bukkit.getScheduler().runTaskLater(plugin, this::startCountdown, 80L);
    }

    private void startCountdown() {
        int[] seconds = { config.getCountdownSeconds() };
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (seconds[0] <= 0) {
                countdownTask.cancel();
                beginGame();
                return;
            }
            Title countdown = Title.title(
                    Component.text(String.valueOf(seconds[0]), NamedTextColor.YELLOW),
                    Component.text("¡Prepárate!", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(100))
            );
            Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(countdown));
            seconds[0]--;
        }, 0L, 20L);
    }

    private void beginGame() {
        state = State.RUNNING;

        for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
            List<Player> members = team.getOnlinePlayers();
            Location sp1 = config.getTeamSpawn1(team.getId());
            Location sp2 = config.getTeamSpawn2(team.getId());
            if (sp1 != null && !members.isEmpty())  members.get(0).teleport(sp1);
            if (sp2 != null && members.size() > 1)  members.get(1).teleport(sp2);
            if (sp1 != null && members.size() == 1) members.get(0).teleport(sp1);

            for (Player p : members) {
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();
                abilityManager.giveAbilities(p);
            }
        }

        chainManager.setMaxDistance(config.getChainMaxDistance());
        chainManager.startForTeams(new ArrayList<>(plugin.getTeamManager().getAllTeams()));

        checkpointManager = new CheckpointManager(plugin, this);
        checkpointManager.start();

        Title go = Title.title(
                Component.text("¡YA!", NamedTextColor.GREEN),
                Component.text("¡Completa el parkour!", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1000), Duration.ofMillis(200))
        );
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(go));
        broadcastAll(Component.text("━━━ ¡PARKOUR DUOS COMENZÓ! ━━━", NamedTextColor.GREEN));
        broadcastAll(Component.text(
                "Tiempo límite: " + config.getDurationMinutes() + " minutos.",
                NamedTextColor.GRAY));

        timeLeftSeconds = config.getDurationMinutes() * 60;
        startTimer();
    }

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
                broadcastAll(Component.text("⏱ ¡Se acabó el tiempo!", NamedTextColor.RED));
                finishByTime();
            }
        }, 20L, 20L);
    }

    private void finishByTime() {
        if (finishing) return;
        finishing = true;

        List<TeamParkourData> ranking = new ArrayList<>(teamData.values());
        ranking.sort((a, b) -> {
            if (a.isFinished() && b.isFinished())
                return Integer.compare(a.getFinishRank(), b.getFinishRank());
            if (a.isFinished()) return -1;
            if (b.isFinished()) return  1;
            return Integer.compare(b.getCompletedCheckpoints(), a.getCompletedCheckpoints());
        });

        broadcastAll(Component.text("━━━ Resultado Final ━━━", NamedTextColor.GOLD));
        for (int i = 0; i < ranking.size(); i++) {
            TeamParkourData data = ranking.get(i);
            String status = data.isFinished()
                    ? "Completado"
                    : data.getCompletedCheckpoints() + "/" + data.getTotalCheckpoints() + " CPs";
            broadcastAll(Component.text("  " + (i + 1) + ". ", NamedTextColor.GRAY)
                    .append(Component.text(data.getTeam().getDisplayName(), data.getTeam().getColor()))
                    .append(Component.text(" — " + status, NamedTextColor.YELLOW)));
        }

        finish(ranking);
    }

    public void onTeamFinished(EventTeam team, TeamParkourData data) {
        if (data.isFinished()) return;
        finishCounter++;
        data.markFinished(finishCounter);

        int score = config.getScoreForPlace(finishCounter);
        data.addInternalScore(score);

        chainManager.removeTeam(team);

        for (Player p : team.getOnlinePlayers()) {
            p.setGameMode(GameMode.SPECTATOR);
            abilityManager.cleanup(p);
            p.getInventory().clear();
        }

        Title winTitle = Title.title(
                Component.text("¡FINISH!", NamedTextColor.GREEN),
                Component.text("Posición #" + finishCounter + " — +" + score + " pts",
                        NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(3),
                        Duration.ofMillis(500))
        );
        team.getOnlinePlayers().forEach(p -> p.showTitle(winTitle));

        broadcastAll(Component.text("🏁 ", NamedTextColor.GREEN)
                .append(Component.text(team.getDisplayName(), team.getColor()))
                .append(Component.text(
                        " terminó el parkour en posición #" + finishCounter + "!",
                        NamedTextColor.GREEN)));

        long teamsFinished = teamData.values().stream().filter(TeamParkourData::isFinished).count();

        if (teamsFinished >= TEAMS_TO_TRIGGER_END) {
            if (timerTask != null && !timerTask.isCancelled()) timerTask.cancel();
            broadcastAll(Component.text(
                    TEAMS_TO_TRIGGER_END + " equipos completaron el recorrido. ¡Fin de la partida!",
                    NamedTextColor.GOLD));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!finishing) finishByTime();
            }, 60L);
            return;
        }

        if (teamsFinished >= teamData.size()) {
            if (timerTask != null && !timerTask.isCancelled()) timerTask.cancel();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!finishing) finishByTime();
            }, 60L);
        }
    }

    private void finish(List<TeamParkourData> ranking) {
        cleanup();
        state = State.FINISHED;

        Location lobby = config.getLobby();

        broadcastAll(Component.text("━━━ Puntajes ━━━", NamedTextColor.GOLD));

        for (int i = 0; i < ranking.size(); i++) {
            TeamParkourData data = ranking.get(i);
            EventTeam team = data.getTeam();
            int position = i + 1;

            if (!data.isFinished()) {
                int positionScore = config.getScoreForPlace(position);
                data.addInternalScore(positionScore);
            }

            int totalScore = data.getInternalScore();
            plugin.getScoreManager().addScore(team, totalScore);

            broadcastAll(Component.text("  #" + position + " ", NamedTextColor.GRAY)
                    .append(Component.text(team.getDisplayName(), team.getColor()))
                    .append(Component.text(" — +" + totalScore + " pts", NamedTextColor.YELLOW)));

            for (Player p : team.getOnlinePlayers()) {
                p.getInventory().clear();
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.setFlying(false);
                if (lobby != null) p.teleport(lobby);
            }
        }

        teamData.clear();
        Bukkit.getScheduler().runTaskLater(plugin, () -> state = State.IDLE, 20L);
    }

    private void cleanup() {
        if (timerTask      != null && !timerTask.isCancelled())     timerTask.cancel();
        if (countdownTask  != null && !countdownTask.isCancelled()) countdownTask.cancel();
        if (checkpointManager != null) checkpointManager.stop();
        chainManager.stopTick();
        if (abilityManager != null) abilityManager.cleanupAll();
        if (gameListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(gameListener);
            gameListener = null;
        }
    }

    private void broadcastAll(Component msg) {
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
    }

    public State                        getState()           { return state; }
    public ParkourDuosConfig            getConfig()          { return config; }
    public ChainManager                 getChainManager()    { return chainManager; }
    public AbilityManager               getAbilityManager()  { return abilityManager; }
    public Map<String, TeamParkourData> getTeamData()        { return teamData; }
    public int                          getTimeLeftSeconds() { return timeLeftSeconds; }

    public String getTimeLeftFormatted() {
        int mins = timeLeftSeconds / 60;
        int secs = timeLeftSeconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    public TeamParkourData getDataFor(EventTeam team) {
        return teamData.get(team.getId());
    }
}