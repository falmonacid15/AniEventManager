package org.falmdev.anieventmanager.minigames.boatracing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.managers.MiniGame;
import org.falmdev.anieventmanager.model.EventTeam;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class BoatRacingMiniGame implements MiniGame {

    public enum State { IDLE, PADDOCK, QUALY, RACE, FINISHED }

    private final Anieventmanager plugin;
    private final BoatRacingConfig config;
    private BoatRacingListener listener;

    private State state = State.IDLE;

    private final Map<UUID, RacerData> racers    = new LinkedHashMap<>();
    private final List<UUID>           gridOrder = new ArrayList<>();
    private final List<Boat>           boats     = new ArrayList<>();

    private BukkitTask qualyTimerTask;
    private int        qualyTimeLeft;
    private int        raceFinishCount = 0;

    private TrackRegion trackRegion;

    public BoatRacingMiniGame(Anieventmanager plugin) {
        this.plugin = plugin;
        this.config = new BoatRacingConfig(plugin);
    }

    // ── MiniGame interface ────────────────────────────────────────────────────

    @Override public String getId()          { return "boatracing"; }
    @Override public String getDisplayName() { return "Boat Racing"; }
    @Override public String getStateName()   { return state.name(); }
    @Override public boolean isIdle()        { return state == State.IDLE; }

    /**
     * Boat Racing tiene dos fases activas: QUALY y RACE.
     * El manager considera ambas como "en curso".
     */
    @Override
    public boolean isRunning() {
        return state == State.RACE || state == State.QUALY || state == State.PADDOCK;
    }

    /**
     * sendToLobby en Boat Racing equivale a sendToPaddock.
     */
    @Override
    public boolean sendToLobby() {
        return sendToPaddock();
    }

    /**
     * start() en Boat Racing inicia la qualy directamente desde IDLE.
     * Si ya está en PADDOCK, usa startQualy() manualmente desde el comando.
     */
    @Override
    public boolean start() {
        if (state == State.IDLE) {
            boolean paddock = sendToPaddock();
            if (!paddock) return false;
        }
        return startQualy();
    }

    @Override
    public void reloadConfig() {
        config.reload();
    }

    @Override
    public String validateConfig() {
        return config.validate();
    }

    // ── Paddock ───────────────────────────────────────────────────────────────

    public boolean sendToPaddock() {
        Location paddock = config.getPaddockSpawn();
        if (paddock == null) return false;

        racers.clear();
        gridOrder.clear();
        boats.clear();
        raceFinishCount = 0;
        trackRegion     = null;

        for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
            for (Player p : team.getOnlinePlayers()) {
                RacerData rd = new RacerData(p.getUniqueId(), p.getName());
                rd.setBoatType(config.getPlayerBoat(p.getUniqueId()));
                racers.put(p.getUniqueId(), rd);
                p.teleport(paddock);
                p.setGameMode(GameMode.ADVENTURE);
                p.sendMessage(Component.text(
                        "Bienvenido al paddock. La carrera comenzará pronto.",
                        NamedTextColor.YELLOW));
            }
        }
        state = State.PADDOCK;
        return true;
    }

    // ── Qualy ─────────────────────────────────────────────────────────────────

    public boolean startQualy() {
        if (state != State.PADDOCK && state != State.IDLE) return false;
        String error = config.validate();
        if (error != null) return false;
        if (racers.isEmpty()) return false;

        trackRegion = config.buildTrackRegion();
        state       = State.QUALY;

        spawnOnGrid(new ArrayList<>(racers.keySet()));
        if (listener != null) listener.setFrozen(true);

        broadcastAll(Component.text("━━━ VUELTA DE CLASIFICACIÓN ━━━", NamedTextColor.GOLD));
        broadcastAll(Component.text("Cruza la meta para comenzar tu vuelta cronometrada.",
                NamedTextColor.YELLOW));

        Title preTitle = Title.title(
                Component.text("🏎 CLASIFICACIÓN", NamedTextColor.GOLD),
                Component.text("Cuando las luces se apaguen... ¡cruza la meta!", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(500))
        );
        racers.keySet().stream()
                .map(Bukkit::getPlayer).filter(Objects::nonNull)
                .forEach(p -> p.showTitle(preTitle));

        if (listener != null) listener.startGridActionBar();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            new BoatRacingLights(plugin, config.getLights(), () -> {
                if (listener != null) {
                    listener.stopGridActionBar();
                    listener.setFrozen(false);
                    listener.startQualyActionBar();
                }
                broadcastAll(Component.text("🚦 ¡GO! — ¡Cruza la meta para iniciar tu cronómetro!",
                        NamedTextColor.GREEN));
                startQualyTimer();
            }).start();
        }, 60L);

        return true;
    }

    private void startQualyTimer() {
        qualyTimeLeft = config.getQualyDuration();
        qualyTimerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            qualyTimeLeft--;
            if (qualyTimeLeft == 30)
                broadcastAll(Component.text("⏱ Qualy: 30 segundos restantes.", NamedTextColor.YELLOW));
            if (qualyTimeLeft <= 0) {
                qualyTimerTask.cancel();
                broadcastAll(Component.text("⏱ Tiempo de qualy agotado.", NamedTextColor.RED));
                finishQualy();
            }
        }, 20L, 20L);
    }

    public void onQualyCrossedFinish(UUID uuid) {
        RacerData rd = racers.get(uuid);
        if (rd == null) return;

        if (!rd.isQualyStarted()) {
            rd.startQualy();
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(Component.text(
                    "⏱ ¡Cronómetro iniciado! Da la vuelta completa.", NamedTextColor.AQUA));
        } else if (!rd.isQualyFinished()) {
            rd.finishQualy();
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(Component.text(
                    "✔ Qualy: " + rd.getQualyTimeFormatted(), NamedTextColor.GREEN));

            boolean allDone = racers.values().stream().allMatch(RacerData::isQualyFinished);
            if (allDone) { if (qualyTimerTask != null) qualyTimerTask.cancel(); finishQualy(); }
        }
    }

    private void finishQualy() {
        if (listener != null) listener.setFrozen(true);
        removeAllBoats();
        state = State.PADDOCK;

        List<RacerData> sorted = new ArrayList<>(racers.values());
        sorted.sort((a, b) -> {
            if (a.isQualyFinished() && b.isQualyFinished())
                return Long.compare(a.getQualyTimeMs(), b.getQualyTimeMs());
            if (a.isQualyFinished()) return -1;
            if (b.isQualyFinished()) return 1;
            return 0;
        });

        gridOrder.clear();
        broadcastAll(Component.text("━━━ PARRILLA ━━━", NamedTextColor.GOLD));
        for (int i = 0; i < sorted.size(); i++) {
            RacerData rd = sorted.get(i);
            rd.setQualyPosition(i + 1);
            gridOrder.add(rd.getPlayerUUID());
            String medal = switch (i) { case 0 -> "🥇"; case 1 -> "🥈"; case 2 -> "🥉"; default -> (i+1) + "."; };
            broadcastAll(Component.text("  " + medal + " " + rd.getPlayerName()
                    + "  " + rd.getQualyTimeFormatted(), NamedTextColor.WHITE));
        }
        broadcastAll(Component.text("Usa /em boatracing startrace para iniciar la carrera.",
                NamedTextColor.GRAY));

        Location paddock = config.getPaddockSpawn();
        if (paddock != null) racers.keySet().stream()
                .map(Bukkit::getPlayer).filter(Objects::nonNull)
                .forEach(p -> p.teleport(paddock));
    }

    // ── Carrera ───────────────────────────────────────────────────────────────

    public boolean startRace() {
        if (state != State.PADDOCK) return false;
        String error = config.validate();
        if (error != null) return false;

        if (gridOrder.isEmpty()) gridOrder.addAll(racers.keySet());
        if (trackRegion == null) trackRegion = config.buildTrackRegion();

        state           = State.RACE;
        raceFinishCount = 0;

        spawnOnGrid(gridOrder);
        if (listener != null) listener.setFrozen(true);

        broadcastAll(Component.text("━━━ CARRERA — " + config.getTotalLaps()
                + " VUELTAS ━━━", NamedTextColor.GOLD));

        Title preTitle = Title.title(
                Component.text("🏎 ¡PREPÁRATE!", NamedTextColor.YELLOW),
                Component.text("Cuando las luces se apaguen... ¡ARRANCA!", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(500))
        );
        racers.keySet().stream()
                .map(Bukkit::getPlayer).filter(Objects::nonNull)
                .forEach(p -> p.showTitle(preTitle));

        if (listener != null) listener.startGridActionBar();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            new BoatRacingLights(plugin, config.getLights(), () -> {
                if (listener != null) {
                    listener.stopGridActionBar();
                    listener.setFrozen(false);
                    listener.startRaceActionBar();
                    listener.startCheckpointTick();
                }
                Title go = Title.title(
                        Component.text("🚦 GO!", NamedTextColor.GREEN),
                        Component.text("¡" + config.getTotalLaps() + " vueltas!", NamedTextColor.WHITE),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(200))
                );
                racers.keySet().stream()
                        .map(Bukkit::getPlayer).filter(Objects::nonNull)
                        .forEach(p -> p.showTitle(go));
                broadcastAll(Component.text("━━━ ¡LA CARRERA COMENZÓ! ━━━", NamedTextColor.GREEN));
            }).start();
        }, 60L);

        return true;
    }

    public void onRaceCrossedFinish(UUID uuid) {
        RacerData rd = racers.get(uuid);
        if (rd == null || rd.isRaceFinished()) return;

        Player p = Bukkit.getPlayer(uuid);

        if (!rd.isRaceStarted()) {
            rd.startRace();
            if (p != null) p.sendMessage(Component.text(
                    "🏁 ¡Vuelta 1/" + config.getTotalLaps() + " iniciada!", NamedTextColor.GREEN));
            return;
        }

        boolean done = rd.completeLap(config.getTotalLaps());

        if (done) {
            raceFinishCount++;
            rd.setRacePosition(raceFinishCount);
            broadcastAll(Component.text("🏁 " + rd.getPlayerName()
                    + " terminó en posición " + raceFinishCount + "!", NamedTextColor.YELLOW));
            if (p != null) {
                p.sendMessage(Component.text(
                        "🏁 ¡Terminaste en posición " + raceFinishCount + "!", NamedTextColor.GOLD));
                sendFinishedRacerToPaddock(p);
            }
            if (raceFinishCount >= racers.size()) finishRace();
        } else {
            if (p != null) p.sendMessage(Component.text(
                    "✔ Vuelta " + (rd.getCurrentLap() - 1) + "/" + config.getTotalLaps()
                            + "  " + RacerData.formatTime(rd.getLastLapTimeMs()), NamedTextColor.AQUA));
        }
    }

    private void sendFinishedRacerToPaddock(Player p) {
        Location paddock = config.getPaddockSpawn();
        if (paddock == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(paddock);
            p.sendMessage(Component.text(
                    "Serás espectador hasta que termine la carrera.", NamedTextColor.GRAY));
        }, 60L);
    }

    private void finishRace() {
        state = State.FINISHED;
        if (listener != null) {
            listener.setFrozen(true);
            listener.stopCheckpointTick();
            listener.stopRaceActionBar();
        }

        broadcastAll(Component.text("━━━ FIN DE CARRERA ━━━", NamedTextColor.GOLD));

        List<RacerData> dnf = racers.values().stream()
                .filter(rd -> !rd.isRaceFinished()).toList();
        for (RacerData rd : dnf) {
            raceFinishCount++;
            rd.setRacePosition(raceFinishCount);
        }

        Map<String, Integer> teamScores = new HashMap<>();
        racers.values().forEach(rd -> {
            int pts = config.getScoreForPosition(rd.getRacePosition());
            Player p = Bukkit.getPlayer(rd.getPlayerUUID());
            if (p == null) return;
            plugin.getTeamManager().getTeamOf(p).ifPresent(team ->
                    teamScores.merge(team.getId(), pts, Integer::sum));
        });

        broadcastAll(Component.text("━━━ Puntajes por equipo ━━━", NamedTextColor.GOLD));
        plugin.getTeamManager().getAllTeams().forEach(team -> {
            int score = teamScores.getOrDefault(team.getId(), 0);
            if (score > 0) {
                plugin.getScoreManager().addScore(team, score);
                broadcastAll(Component.text("  +" + score + " pts → ", NamedTextColor.YELLOW)
                        .append(Component.text(team.getDisplayName(), team.getColor())));
            }
        });

        broadcastAll(Component.text("━━━ Clasificación individual ━━━", NamedTextColor.GOLD));
        racers.values().stream()
                .sorted(Comparator.comparingInt(RacerData::getRacePosition))
                .forEach(rd -> broadcastAll(Component.text(
                        "  " + rd.getRacePosition() + ". " + rd.getPlayerName()
                                + "  Mejor vuelta: " + rd.getBestLapFormatted(), NamedTextColor.WHITE)));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeAllBoats();
            Location paddock = config.getPaddockSpawn();
            if (paddock != null) {
                racers.keySet().stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .forEach(p -> {
                            p.setGameMode(GameMode.ADVENTURE);
                            p.teleport(paddock);
                        });
            }
            state = State.IDLE;
        }, 200L);
    }

    @Override
    public void forceStop() {
        if (qualyTimerTask != null && !qualyTimerTask.isCancelled()) qualyTimerTask.cancel();
        if (listener != null) {
            listener.setFrozen(false);
            listener.stopCheckpointTick();
            listener.stopRaceActionBar();
            listener.stopGridActionBar();
            listener.stopQualyActionBar();
        }
        removeAllBoats();
        Location paddock = config.getPaddockSpawn();
        if (paddock != null) {
            racers.keySet().stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .forEach(p -> {
                        p.setGameMode(GameMode.ADVENTURE);
                        p.teleport(paddock);
                    });
        }
        broadcastAll(Component.text("La carrera fue detenida por un admin.", NamedTextColor.RED));
        state = State.IDLE;
    }

    // ── Parrilla ──────────────────────────────────────────────────────────────

    private void spawnOnGrid(List<UUID> order) {
        removeAllBoats();
        List<Location> spawns = config.getPlayerSpawns();
        for (int i = 0; i < order.size(); i++) {
            UUID uuid = order.get(i);
            RacerData rd = racers.get(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || rd == null) continue;

            Location loc = i < spawns.size() ? spawns.get(i) : spawns.get(spawns.size() - 1);
            Boat boat = (Boat) loc.getWorld().spawnEntity(loc, rd.getBoatType().getEntityType());
            boat.setInvulnerable(true);
            rd.setBoat(boat);
            boats.add(boat);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                boat.addPassenger(p);
                p.setGameMode(GameMode.SURVIVAL);
            }, 2L);
        }
    }

    private void removeAllBoats() {
        boats.forEach(b -> { if (b != null && !b.isDead()) b.remove(); });
        boats.clear();
    }

    // ── Posiciones en tiempo real ─────────────────────────────────────────────

    public List<UUID> getRealTimePositions() {
        return racers.values().stream()
                .sorted((a, b) -> {
                    if (a.isRaceFinished() && b.isRaceFinished())
                        return Integer.compare(a.getRacePosition(), b.getRacePosition());
                    if (a.isRaceFinished()) return -1;
                    if (b.isRaceFinished()) return 1;
                    if (a.getCurrentLap() != b.getCurrentLap())
                        return Integer.compare(b.getCurrentLap(), a.getCurrentLap());
                    return Integer.compare(b.getLastCheckpoint(), a.getLastCheckpoint());
                })
                .map(RacerData::getPlayerUUID)
                .collect(Collectors.toList());
    }

    public int getRealTimePosition(UUID uuid) {
        List<UUID> pos = getRealTimePositions();
        int idx = pos.indexOf(uuid);
        return idx == -1 ? 0 : idx + 1;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public void registerListener(BoatRacingListener l) { this.listener = l; }

    public State               getState()       { return state; }
    public BoatRacingConfig    getConfig()      { return config; }
    public TrackRegion         getTrackRegion() { return trackRegion; }
    public Map<UUID,RacerData> getRacers()      { return Collections.unmodifiableMap(racers); }
    public RacerData           getRacerData(Player p) { return racers.get(p.getUniqueId()); }

    private void broadcastAll(Component msg) {
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
    }
}