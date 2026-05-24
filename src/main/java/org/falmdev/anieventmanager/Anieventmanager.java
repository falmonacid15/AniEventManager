package org.falmdev.anieventmanager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.falmdev.anieventmanager.commands.EMCommand;
import org.falmdev.anieventmanager.listeners.TeamListener;
import org.falmdev.anieventmanager.lobby.ConfirmGUI;
import org.falmdev.anieventmanager.lobby.PlayerSelectorGUI;
import org.falmdev.anieventmanager.lobby.TeamAdminGUI;
import org.falmdev.anieventmanager.lobby.TeamLobbyListener;
import org.falmdev.anieventmanager.lobby.TeamLobbyManager;
import org.falmdev.anieventmanager.lobby.TeamSelectionGUI;
import org.falmdev.anieventmanager.managers.MiniGameManager;
import org.falmdev.anieventmanager.managers.ScoreManager;
import org.falmdev.anieventmanager.managers.TeamManager;
import org.falmdev.anieventmanager.minigames.bingo.BingoCommand;
import org.falmdev.anieventmanager.minigames.bingo.BingoEditGUI;
import org.falmdev.anieventmanager.minigames.bingo.BingoGUI;
import org.falmdev.anieventmanager.minigames.bingo.BingoMiniGame;
import org.falmdev.anieventmanager.minigames.boatracing.BoatRacingCommand;
import org.falmdev.anieventmanager.minigames.boatracing.BoatRacingListener;
import org.falmdev.anieventmanager.minigames.boatracing.BoatRacingMiniGame;
import org.falmdev.anieventmanager.minigames.frozenheist.FrozenHeistCommand;
import org.falmdev.anieventmanager.minigames.frozenheist.FrozenHeistMiniGame;
import org.falmdev.anieventmanager.minigames.tntrun.TNTRunCommand;
import org.falmdev.anieventmanager.minigames.tntrun.TNTRunMiniGame;
import org.falmdev.anieventmanager.placeholders.AniEventExpansion;
import org.falmdev.anieventmanager.minigames.bingo.BingoWallManager;
import org.falmdev.anieventmanager.minigames.parkourduos.ParkourDuosMiniGame;
import org.falmdev.anieventmanager.minigames.parkourduos.ParkourDuosCommand;

import java.util.logging.Logger;

public final class Anieventmanager extends JavaPlugin implements Listener {

    private static Anieventmanager instance;

    // ── Managers ──────────────────────────────────────────────────────────────
    private TeamManager      teamManager;
    private ScoreManager     scoreManager;
    private MiniGameManager  miniGameManager;
    private TeamLobbyManager teamLobbyManager;
    private TeamSelectionGUI teamSelectionGUI;
    private TeamAdminGUI     teamAdminGUI;
    private PlayerSelectorGUI playerSelectorGUI;
    private ConfirmGUI        confirmGUI;

    // ── Minijuegos ────────────────────────────────────────────────────────────
    private TNTRunMiniGame      tntRunMiniGame;
    private TNTRunCommand       tntRunCommand;

    private BingoMiniGame       bingoMiniGame;
    private BingoWallManager    bingoWallManager;
    private BingoCommand        bingoCommand;
    private BingoEditGUI        bingoEditGUI;

    private BoatRacingMiniGame  boatRacingMiniGame;
    private BoatRacingCommand   boatRacingCommand;

    private FrozenHeistMiniGame frozenHeistMiniGame;
    private FrozenHeistCommand  frozenHeistCommand;

    private ParkourDuosMiniGame parkourDuosMiniGame;
    private ParkourDuosCommand  parkourDuosCommand;

    @Override
    public void onEnable() {
        instance = this;
        Logger log = getLogger();

        printBanner(log);

        // ── Managers base ─────────────────────────────────────────────────────
        logSection(log, "TeamManager");
        this.teamManager = new TeamManager(this);
        logDone(log, "TeamManager");

        logSection(log, "ScoreManager");
        this.scoreManager = new ScoreManager(this);
        logDone(log, "ScoreManager");

        logSection(log, "MiniGameManager");
        this.miniGameManager = new MiniGameManager(this);
        logDone(log, "MiniGameManager");

        logSection(log, "TeamLobbyManager");
        this.teamLobbyManager = new TeamLobbyManager(this);
        this.teamSelectionGUI = new TeamSelectionGUI(this);
        this.teamAdminGUI     = new TeamAdminGUI(this);
        this.playerSelectorGUI = new PlayerSelectorGUI(this);
        this.confirmGUI        = new ConfirmGUI(this);
        logDone(log, "TeamLobbyManager",
                teamLobbyManager.getConfig().getAllSigns().size() + " carteles, "
                        + teamLobbyManager.getConfig().getAllLampBlocks().size() + " lámparas");

        // ── Minijuegos ────────────────────────────────────────────────────────
        logSection(log, "Minijuegos");

        this.tntRunMiniGame = new TNTRunMiniGame(this);
        this.tntRunCommand  = new TNTRunCommand(this, tntRunMiniGame);
        miniGameManager.register(tntRunMiniGame);
        logLoaded(log, "TNT Run");

        this.bingoMiniGame    = new BingoMiniGame(this);
        this.bingoCommand     = new BingoCommand(this, bingoMiniGame);
        this.bingoEditGUI     = new BingoEditGUI(this);
        this.bingoWallManager = new BingoWallManager(this);
        miniGameManager.register(bingoMiniGame);
        logLoaded(log, "Bingo");

        this.boatRacingMiniGame = new BoatRacingMiniGame(this);
        this.boatRacingCommand  = new BoatRacingCommand(this, boatRacingMiniGame);
        BoatRacingListener boatRacingListener = new BoatRacingListener(this, boatRacingMiniGame);
        this.boatRacingMiniGame.registerListener(boatRacingListener);
        miniGameManager.register(boatRacingMiniGame);
        logLoaded(log, "Boat Racing");

        this.frozenHeistMiniGame = new FrozenHeistMiniGame(this);
        this.frozenHeistCommand  = new FrozenHeistCommand(this, frozenHeistMiniGame);
        miniGameManager.register(frozenHeistMiniGame);
        logLoaded(log, "Frozen Heist");

        this.parkourDuosMiniGame = new ParkourDuosMiniGame(this);
        this.parkourDuosCommand  = new ParkourDuosCommand(this, parkourDuosMiniGame);
        miniGameManager.register(parkourDuosMiniGame);
        logLoaded(log, "Parkour Duos");

        logDone(log, "Minijuegos", miniGameManager.getIds().size() + " registrados en MiniGameManager");

        // ── Comandos ──────────────────────────────────────────────────────────
        logSection(log, "Comandos");

        EMCommand emCommand = new EMCommand(this);
        var emCmd = getCommand("em");
        if (emCmd != null) {
            emCmd.setExecutor(emCommand);
            emCmd.setTabCompleter(emCommand);
            logLoaded(log, "/em");
        }

        var bingoCmd = getCommand("bingo");
        if (bingoCmd != null) {
            bingoCmd.setExecutor(bingoCommand);
            bingoCmd.setTabCompleter(bingoCommand);
            logLoaded(log, "/bingo");
        }

        logDone(log, "Comandos");

        // ── Listeners ─────────────────────────────────────────────────────────
        logSection(log, "Listeners");
        Bukkit.getPluginManager().registerEvents(new TeamListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BingoGUI(this), this);
        Bukkit.getPluginManager().registerEvents(bingoEditGUI, this);
        Bukkit.getPluginManager().registerEvents(bingoWallManager, this);
        Bukkit.getPluginManager().registerEvents(boatRacingListener, this);
        Bukkit.getPluginManager().registerEvents(teamSelectionGUI, this);
        Bukkit.getPluginManager().registerEvents(teamAdminGUI, this);
        Bukkit.getPluginManager().registerEvents(playerSelectorGUI, this);
        Bukkit.getPluginManager().registerEvents(confirmGUI, this);
        Bukkit.getPluginManager().registerEvents(new TeamLobbyListener(this), this);
        Bukkit.getPluginManager().registerEvents(this, this);
        logDone(log, "Listeners");

        log.info(line());
        log.info("  AniEventManager v" + getDescription().getVersion() + " habilitado correctamente.");
        log.info(line());
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        Logger log = getLogger();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AniEventExpansion(this).register();
            log.info("  (PlaceholderAPI) -> Expansion registrada correctamente.");
        } else {
            log.warning("  (PlaceholderAPI) -> No encontrado — los placeholders no estaran disponibles.");
        }

        // Una vez los mundos están cargados, refrescamos el lobby
        Bukkit.getScheduler().runTaskLater(this,
                () -> teamLobbyManager.refreshAll(), 20L);
    }

    @Override
    public void onDisable() {
        miniGameManager.stopAll();
        Logger log = getLogger();
        log.info(line());
        log.info("  AniEventManager deshabilitado.");
        log.info(line());
    }

    // ── Reload ────────────────────────────────────────────────────────────────

    public void reloadAll(org.bukkit.entity.Player player) {
        for (String name : miniGameManager.reloadAll()) {
            player.sendMessage(Component.text("  \u2714 " + name + " recargado.", NamedTextColor.GREEN));
        }
        teamLobbyManager.getConfig().reload();
        teamLobbyManager.refreshAll();
        player.sendMessage(Component.text("  \u2714 Team Lobby recargado.", NamedTextColor.GREEN));

        player.sendMessage(Component.text("\u2714 Configuraci\u00f3n recargada.", NamedTextColor.GREEN));
        getLogger().info("Configuraci\u00f3n recargada por " + player.getName());
    }

    // ── Banner ────────────────────────────────────────────────────────────────

    private static final String RESET  = "\u001B[0m";
    private static final String CYAN   = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN  = "\u001B[32m";
    private static final String GRAY   = "\u001B[90m";
    private static final String WHITE  = "\u001B[97m";
    private static final String BOLD   = "\u001B[1m";

    private void printBanner(Logger log) {
        String v = getDescription().getVersion();
        log.info(" ");
        log.info(CYAN + BOLD + "  ___   _   _ _____ _____ _   _ _____ _   _ _____ " + RESET);
        log.info(CYAN + BOLD + " / _ \\ | \\ | |_   _|  ___| | | |  ___| \\ | |_   _|" + RESET);
        log.info(CYAN + BOLD + "/ /_\\ \\|  \\| | | | | |__ | | | | |__ |  \\| | | |  " + RESET);
        log.info(CYAN + BOLD + "|  _  || . ` | | | |  __|| | | |  __|| . ` | | |  " + RESET);
        log.info(CYAN + BOLD + "| | | || |\\  |_| |_| |___\\ \\_/ / |___| |\\  | | |  " + RESET);
        log.info(CYAN + BOLD + "\\_| |_/\\_| \\_/\\___/\\____/ \\___/\\____/\\_| \\_/ \\_/  " + RESET);
        log.info(" ");
        log.info(CYAN + BOLD + "___  ___  ___   _   _   ___  _____  ___________ " + RESET);
        log.info(CYAN + BOLD + "|  \\/  | / _ \\ | \\ | | / _ \\|  __ \\|  ___| ___ \\" + RESET);
        log.info(CYAN + BOLD + "| .  . |/ /_\\ \\|  \\| |/ /_\\ \\ |  \\/| |__ | |_/ /" + RESET);
        log.info(CYAN + BOLD + "| |\\/| ||  _  || . ` ||  _  | | __ |  __||    / " + RESET);
        log.info(CYAN + BOLD + "| |  | || | | || |\\  || | | | |_\\ \\| |___| |\\ \\ " + RESET);
        log.info(CYAN + BOLD + "\\_|  |_/\\_| |_/\\_| \\_/\\_| |_/\\____/\\____/\\_| \\_|" + RESET);
        log.info(" ");
        log.info(GRAY + "  \u250c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2510" + RESET);
        log.info(GRAY + "  \u2502  " + WHITE + BOLD + "AniEventManager" + RESET + GRAY + "  v" + YELLOW + v + RESET + GRAY + "                          \u2502" + RESET);
        log.info(GRAY + "  \u2502  " + GRAY + "Plugin para gesti\u00f3n del evento " + CYAN + "\"Ani Event\"" + GRAY + " de animalito  \u2502" + RESET);
        log.info(GRAY + "  \u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2518" + RESET);
        log.info(" ");
    }

    private void logSection(Logger log, String name) {
        log.info(GRAY + "  (" + CYAN + name + GRAY + ") -> Iniciando..." + RESET);
    }

    private void logDone(Logger log, String name) {
        log.info(GRAY + "  (" + CYAN + name + GRAY + ") -> " + GREEN + "OK" + RESET);
    }

    private void logDone(Logger log, String name, String extra) {
        log.info(GRAY + "  (" + CYAN + name + GRAY + ") -> " + GREEN + "OK" + GRAY + "  (" + extra + ")" + RESET);
    }

    private void logLoaded(Logger log, String name) {
        log.info(GRAY + "    \u2022 " + WHITE + name + RESET);
    }

    private String line() {
        return GRAY + "  \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" + RESET;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public static Anieventmanager getInstance()      { return instance; }
    public TeamManager      getTeamManager()         { return teamManager; }
    public ScoreManager     getScoreManager()        { return scoreManager; }
    public MiniGameManager  getMiniGameManager()     { return miniGameManager; }
    public TeamLobbyManager getTeamLobbyManager()    { return teamLobbyManager; }
    public TeamSelectionGUI getTeamSelectionGUI()    { return teamSelectionGUI; }
    public TeamAdminGUI     getTeamAdminGUI()        { return teamAdminGUI; }
    public PlayerSelectorGUI getPlayerSelectorGUI()  { return playerSelectorGUI; }
    public ConfirmGUI        getConfirmGUI()         { return confirmGUI; }
    public TNTRunMiniGame   getTNTRunMiniGame()      { return tntRunMiniGame; }
    public TNTRunCommand    getTNTRunCommand()       { return tntRunCommand; }
    public BingoMiniGame    getBingoMiniGame()       { return bingoMiniGame; }
    public BingoCommand     getBingoCommand()        { return bingoCommand; }
    public BingoEditGUI     getBingoEditGUI()        { return bingoEditGUI; }
    public BingoWallManager getBingoWallManager()    { return bingoWallManager; }
    public BoatRacingMiniGame  getBoatRacingMiniGame()  { return boatRacingMiniGame; }
    public BoatRacingCommand   getBoatRacingCommand()   { return boatRacingCommand; }
    public FrozenHeistMiniGame getFrozenHeistMiniGame() { return frozenHeistMiniGame; }
    public FrozenHeistCommand  getFrozenHeistCommand()  { return frozenHeistCommand; }
    public ParkourDuosMiniGame getParkourDuosMiniGame() { return parkourDuosMiniGame; }
    public ParkourDuosCommand  getParkourDuosCommand()  { return parkourDuosCommand; }
}