package org.falmdev.anieventmanager.minigames.battleroyale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.managers.MiniGame;
import org.falmdev.anieventmanager.minigames.battleroyale.drop.DropSystem;
import org.falmdev.anieventmanager.minigames.battleroyale.loot.LootManager;
import org.falmdev.anieventmanager.minigames.battleroyale.loot.LootListener;
import org.falmdev.anieventmanager.minigames.battleroyale.economy.CoinManager;
import org.falmdev.anieventmanager.minigames.battleroyale.death.DeathListener;
import org.falmdev.anieventmanager.minigames.battleroyale.death.RespawnManager;
import org.falmdev.anieventmanager.minigames.battleroyale.model.BRPlayer;
import org.falmdev.anieventmanager.minigames.battleroyale.model.BRTeam;
import org.falmdev.anieventmanager.minigames.battleroyale.zone.ZoneManager;

import java.util.*;

public class BattleRoyaleMiniGame implements MiniGame {

    public enum State {
        IDLE, WAITING, STARTING, DROPPING, IN_GAME, ENDING
    }

    private final Anieventmanager    plugin;
    private final BattleRoyaleConfig config;
    private final DropSystem         dropSystem;
    private final ZoneManager        zoneManager;
    private final LootManager        lootManager;
    private final LootListener       lootListener;
    private final CoinManager        coinManager;
    private final DeathListener      deathListener;
    private final RespawnManager     respawnManager;

    private State      state         = State.IDLE;
    private BukkitTask countdownTask = null;
    private int        countdownSecs = 0;

    private final Map<UUID, BRPlayer> players = new LinkedHashMap<>();
    private final Map<String, BRTeam> teams   = new LinkedHashMap<>();

    public BattleRoyaleMiniGame(Anieventmanager plugin) {
        this.plugin      = plugin;
        this.config      = new BattleRoyaleConfig(plugin);
        this.dropSystem  = new DropSystem(plugin, config);
        this.zoneManager = new ZoneManager(plugin, this);
        this.lootManager = new LootManager(plugin, this);
        this.lootListener = new LootListener(plugin, this);
        this.lootManager.setListener(lootListener);
        this.coinManager = new CoinManager(plugin, this);
        this.respawnManager = new RespawnManager(plugin, this);
        this.deathListener = new DeathListener(plugin, this);

        // Registrar listeners
        org.bukkit.Bukkit.getPluginManager().registerEvents(lootListener, plugin);
        org.bukkit.Bukkit.getPluginManager().registerEvents(deathListener, plugin);
    }

    @Override public String  getId()          { return "battleroyale"; }
    @Override public String  getDisplayName() { return "Battle Royale"; }
    @Override public String  getStateName()   { return state.name(); }
    @Override public boolean isRunning()      { return state != State.IDLE && state != State.WAITING; }
    @Override public boolean isIdle()         { return state == State.IDLE; }

    @Override
    public boolean sendToLobby() {
        if (state != State.IDLE) return false;
        Location lobby = config.getLobbySpawn();
        if (lobby == null) return false;
        state = State.WAITING;
        loadPlayersFromTeams();
        for (BRPlayer brp : players.values()) {
            Player p = Bukkit.getPlayer(brp.getUuid());
            if (p != null && p.isOnline()) {
                p.setGameMode(GameMode.ADVENTURE);
                p.teleport(lobby);
                p.sendMessage(Component.text("Esperando el inicio del Battle Royale...",
                        NamedTextColor.YELLOW));
            }
        }
        return true;
    }

    @Override
    public boolean start() {
        String err = config.validate();
        if (err != null) {
            plugin.getLogger().warning("[BR] No se puede iniciar: " + err);
            return false;
        }
        if (state != State.IDLE && state != State.WAITING) return false;
        if (state == State.IDLE) loadPlayersFromTeams();
        if (players.size() < config.getMinPlayers()) {
            plugin.getLogger().warning("[BR] Jugadores insuficientes (" +
                    players.size() + "/" + config.getMinPlayers() + ")");
            return false;
        }
        transitionTo(State.STARTING);
        return true;
    }

    @Override
    public void forceStop() {
        if (state == State.IDLE) return;
        zoneManager.stop();
        lootManager.stopParticles();
        dropSystem.stop();
        cancelCountdown();
        restoreAllPlayers();
        players.clear();
        teams.clear();
        state = State.IDLE;
        broadcast(Component.text("Battle Royale detenido.", NamedTextColor.RED));
    }

    public void stop() { forceStop(); }

    @Override public void   reloadConfig()   { config.reload(); }
    @Override public String validateConfig() { return config.validate(); }

    // ── Transiciones ──────────────────────────────────────────────────────────

    private void transitionTo(State newState) {
        this.state = newState;
        plugin.getLogger().info("[BR] Estado → " + newState);
        switch (newState) {
            case STARTING -> startCountdown();
            case DROPPING -> startDrop();
            case IN_GAME  -> startGame();
            case ENDING   -> endGame();
            default       -> {}
        }
    }

    private void startCountdown() {
        countdownSecs = config.getCountdownSeconds();
        broadcast(Component.text("Battle Royale comienza en " + countdownSecs + "s", NamedTextColor.YELLOW));
        Location lobby = config.getLobbySpawn();
        if (lobby != null) {
            for (BRPlayer brp : players.values()) {
                Player p = Bukkit.getPlayer(brp.getUuid());
                if (p != null) { p.setGameMode(GameMode.ADVENTURE); p.teleport(lobby); }
            }
        }
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (countdownSecs <= 0) { cancelCountdown(); transitionTo(State.DROPPING); return; }
            if (countdownSecs <= 5 || countdownSecs % 10 == 0)
                broadcast(Component.text("⏱ " + countdownSecs + "...", NamedTextColor.GOLD));
            countdownSecs--;
        }, 20L, 20L);
    }

    private void startDrop() {
        broadcast(Component.text("¡Los avión despega! Presioná SHIFT para saltar.", NamedTextColor.AQUA));
        dropSystem.start(players, () -> {
            if (state == State.DROPPING) transitionTo(State.IN_GAME);
        });
    }

    private void startGame() {
        broadcast(Component.text("¡La partida comenzó! Último en pie gana.", NamedTextColor.GREEN));
        zoneManager.start();
        lootManager.startParticles();
        coinManager.resetAll();

        // Refill inicial si está habilitado
        if (lootManager.getLootConfig().isRefillOnGameStart()) {
            var res = lootManager.refill();
            broadcast(Component.text("📦 Loot inicial: " + res.filled() + " cofres rellenados.",
                    NamedTextColor.GREEN));
        }
    }

    private void endGame() {
        BRPlayer winner = players.values().stream().filter(BRPlayer::isAlive).findFirst().orElse(null);
        if (winner != null)
            broadcast(Component.text("🏆 ¡" + winner.getName() + " ganó el Battle Royale!", NamedTextColor.GOLD));
        else
            broadcast(Component.text("La partida terminó sin ganador.", NamedTextColor.GRAY));
        Bukkit.getScheduler().runTaskLater(plugin, this::forceStop, 100L);
    }

    /**
     * Procesa la muerte de un jugador. Llamado desde DeathListener.
     * NO hace broadcast (eso lo gestiona DeathListener).
     * Solo actualiza el estado, suma kill al killer y verifica win condition.
     */
    public void handleDeath(Player victim, Player killer) {
        BRPlayer brp = players.get(victim.getUniqueId());
        if (brp == null || brp.isDead()) return;
        brp.setState(BRPlayer.State.SPECTATING);

        if (killer != null && !killer.equals(victim)) {
            BRPlayer killerBrp = players.get(killer.getUniqueId());
            if (killerBrp != null) {
                killerBrp.addKill();
                int reward = config.getCoinsPerKill();
                if (reward > 0) coinManager.add(killer, reward);
            }
        }
        checkWinCondition();
    }

    /** API de compatibilidad — delega a handleDeath. */
    public void killPlayer(Player player, Player killer) {
        handleDeath(player, killer);
    }

    private void checkWinCondition() {
        if (state != State.IN_GAME) return;
        if (players.values().stream().filter(BRPlayer::isAlive).count() <= 1)
            transitionTo(State.ENDING);
    }

    private void loadPlayersFromTeams() {
        players.clear();
        teams.clear();
        plugin.getTeamManager().getAllTeams().forEach(team -> {
            teams.put(team.getId(), new BRTeam(team));
            team.getMembers().forEach(uuid -> {
                Player p = Bukkit.getPlayer(uuid);
                String name = p != null ? p.getName()
                        : Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName())
                          .orElse(uuid.toString().substring(0, 8));
                players.put(uuid, new BRPlayer(uuid, name));
            });
        });
    }

    private void restoreAllPlayers() {
        Location lobby = config.getLobbySpawn();
        for (BRPlayer brp : players.values()) {
            Player p = Bukkit.getPlayer(brp.getUuid());
            if (p == null || !p.isOnline()) continue;
            p.setGameMode(GameMode.SURVIVAL);
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
            p.setGliding(false);
            if (lobby != null) p.teleport(lobby);
        }
    }

    private void cancelCountdown() {
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void broadcast(Component msg) {
        for (BRPlayer brp : players.values()) {
            Player p = Bukkit.getPlayer(brp.getUuid());
            if (p != null && p.isOnline()) p.sendMessage(msg);
        }
        plugin.getLogger().info("[BR] " +
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(msg));
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public State              getState()       { return state; }
    public BattleRoyaleConfig getConfig()      { return config; }
    public DropSystem         getDropSystem()  { return dropSystem; }
    public ZoneManager        getZoneManager() { return zoneManager; }
    public LootManager        getLootManager() { return lootManager; }
    public CoinManager        getCoinManager() { return coinManager; }
    public RespawnManager     getRespawnManager() { return respawnManager; }
    public DeathListener      getDeathListener() { return deathListener; }
    public BRPlayer           getBRPlayer(Player p) { return players.get(p.getUniqueId()); }
    public Map<UUID, BRPlayer> getAllPlayers() { return Collections.unmodifiableMap(players); }
    public Map<String, BRTeam> getAllTeams()   { return Collections.unmodifiableMap(teams); }
    public long getAlivePlayers() { return players.values().stream().filter(BRPlayer::isAlive).count(); }
}