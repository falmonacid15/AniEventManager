package org.falmdev.anieventmanager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.falmdev.anieventmanager.cinematics.*;
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
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleCommand;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleMiniGame;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyalePlaceholders;
import org.falmdev.anieventmanager.minigames.battleroyale.drop.DropListener;
import org.falmdev.anieventmanager.minigames.bingo.*;
import org.falmdev.anieventmanager.minigames.boatracing.*;
import org.falmdev.anieventmanager.minigames.frozenheist.FrozenHeistAdminGUI;
import org.falmdev.anieventmanager.minigames.frozenheist.FrozenHeistCommand;
import org.falmdev.anieventmanager.minigames.frozenheist.FrozenHeistMagicStick;
import org.falmdev.anieventmanager.minigames.frozenheist.FrozenHeistMiniGame;
import org.falmdev.anieventmanager.minigames.parkourduos.ParkourDuosAdminGUI;
import org.falmdev.anieventmanager.minigames.parkourduos.ParkourDuosMagicStick;
import org.falmdev.anieventmanager.minigames.tntrun.*;
import org.falmdev.anieventmanager.placeholders.AniEventExpansion;
import org.falmdev.anieventmanager.minigames.parkourduos.ParkourDuosMiniGame;
import org.falmdev.anieventmanager.minigames.parkourduos.ParkourDuosCommand;
import org.falmdev.anieventmanager.minigames.pvpfinal.PvpFinalCommand;
import org.falmdev.anieventmanager.minigames.pvpfinal.PvpFinalMiniGame;
import org.falmdev.anieventmanager.minigames.pvpfinal.PvpFinalPlaceholders;
import org.falmdev.anieventmanager.utils.interval.IntervalManager;

import java.util.logging.Logger;

public final class Anieventmanager extends JavaPlugin implements Listener {

    private static Anieventmanager instance;

    // ── Managers ──────────────────────────────────────────────────────────────
    private TeamManager       teamManager;
    private ScoreManager      scoreManager;
    private MiniGameManager   miniGameManager;
    private TeamLobbyManager  teamLobbyManager;
    private TeamSelectionGUI  teamSelectionGUI;
    private TeamAdminGUI      teamAdminGUI;
    private PlayerSelectorGUI playerSelectorGUI;
    private ConfirmGUI        confirmGUI;

    private EventManagerGUI  eventManagerGUI;
    private TNTRunAdminGUI   tntRunAdminGUI;
    private TNTRunMagicStick tntRunMagicStick;
    private TNTRunPlaceholders tntRunPlaceholders;

    private BingoAdminGUI    bingoAdminGUI;
    private BingoMagicStick  bingoMagicStick;

    // ── Minijuegos ────────────────────────────────────────────────────────────
    private TNTRunMiniGame   tntRunMiniGame;
    private TNTRunCommand    tntRunCommand;

    private BingoMiniGame    bingoMiniGame;
    private BingoWallManager bingoWallManager;
    private BingoCommand     bingoCommand;
    private BingoEditGUI     bingoEditGUI;

    private BoatRacingMiniGame   boatRacingMiniGame;
    private BoatRacingCommand    boatRacingCommand;
    private BoatRacingAdminGUI   boatRacingAdminGUI;
    private BoatRacingMagicStick boatRacingMagicStick;

    private FrozenHeistMiniGame   frozenHeistMiniGame;
    private FrozenHeistCommand    frozenHeistCommand;
    private FrozenHeistAdminGUI   frozenHeistAdminGUI;
    private FrozenHeistMagicStick frozenHeistMagicStick;

    private ParkourDuosMiniGame   parkourDuosMiniGame;
    private ParkourDuosCommand    parkourDuosCommand;
    private ParkourDuosAdminGUI   parkourDuosAdminGUI;
    private ParkourDuosMagicStick parkourDuosMagicStick;

    // ── Battle Royale ─────────────────────────────────────────────────────────
    private BattleRoyaleMiniGame   battleRoyaleMiniGame;
    private BattleRoyaleCommand    battleRoyaleCommand;
    private BattleRoyalePlaceholders battleRoyalePlaceholders;
    private DropListener           dropListener;

    private PvpFinalMiniGame      pvpFinalMiniGame;
    private PvpFinalCommand       pvpFinalCommand;
    private PvpFinalPlaceholders  pvpFinalPlaceholders;

    // ── Cinematicas ───────────────────────────────────────────────────────────
    private CinematicManager  cinematicManager;
    private CinematicAdminGUI cinematicAdminGUI;
    private CinematicMarkerGUI cinematicMarkerGUI;

    private IntervalManager intervalManager;

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

        logSection(log, "CinematicManager");
        this.cinematicManager  = new CinematicManager(this);
        this.cinematicAdminGUI = new CinematicAdminGUI(this);
        this.cinematicMarkerGUI = new CinematicMarkerGUI(this);
        logDone(log, "CinematicManager",
                cinematicManager.getIds().size() + " cinematicas");

        logSection(log, "TeamLobbyManager");
        this.teamLobbyManager  = new TeamLobbyManager(this);
        this.teamSelectionGUI  = new TeamSelectionGUI(this);
        this.teamAdminGUI      = new TeamAdminGUI(this);
        this.playerSelectorGUI = new PlayerSelectorGUI(this);
        this.confirmGUI        = new ConfirmGUI(this);
        logDone(log, "TeamLobbyManager",
                teamLobbyManager.getConfig().getAllSigns().size() + " carteles, "
                        + teamLobbyManager.getConfig().getAllLampBlocks().size() + " lámparas");

        // ── Minijuegos ────────────────────────────────────────────────────────
        logSection(log, "Minijuegos");

        this.tntRunMiniGame  = new TNTRunMiniGame(this);
        this.tntRunCommand   = new TNTRunCommand(this, tntRunMiniGame);
        this.tntRunAdminGUI  = new TNTRunAdminGUI(this);
        this.tntRunMagicStick = new TNTRunMagicStick(this);
        miniGameManager.register(tntRunMiniGame);
        this.tntRunPlaceholders = new TNTRunPlaceholders(this, tntRunMiniGame);
        logLoaded(log, "TNT Run");
        logLoaded(log, "TNT Run");

        this.bingoMiniGame    = new BingoMiniGame(this);
        this.bingoCommand     = new BingoCommand(this, bingoMiniGame);
        this.bingoEditGUI     = new BingoEditGUI(this);
        this.bingoWallManager = new BingoWallManager(this);
        this.bingoAdminGUI    = new BingoAdminGUI(this);
        this.bingoMagicStick  = new BingoMagicStick(this);
        miniGameManager.register(bingoMiniGame);
        logLoaded(log, "Bingo");

        this.boatRacingMiniGame = new BoatRacingMiniGame(this);
        this.boatRacingCommand  = new BoatRacingCommand(this, boatRacingMiniGame);
        BoatRacingListener boatRacingListener = new BoatRacingListener(this, boatRacingMiniGame);
        this.boatRacingMiniGame.registerListener(boatRacingListener);
        this.boatRacingAdminGUI   = new BoatRacingAdminGUI(this);
        this.boatRacingMagicStick = new BoatRacingMagicStick(this);
        miniGameManager.register(boatRacingMiniGame);
        logLoaded(log, "Boat Racing");

        this.frozenHeistMiniGame  = new FrozenHeistMiniGame(this);
        this.frozenHeistCommand   = new FrozenHeistCommand(this, frozenHeistMiniGame);
        this.frozenHeistAdminGUI  = new FrozenHeistAdminGUI(this);
        this.frozenHeistMagicStick = new FrozenHeistMagicStick(this);
        miniGameManager.register(frozenHeistMiniGame);
        logLoaded(log, "Frozen Heist");

        this.parkourDuosMiniGame  = new ParkourDuosMiniGame(this);
        this.parkourDuosCommand   = new ParkourDuosCommand(this, parkourDuosMiniGame);
        this.parkourDuosAdminGUI  = new ParkourDuosAdminGUI(this);
        this.parkourDuosMagicStick = new ParkourDuosMagicStick(this);
        miniGameManager.register(parkourDuosMiniGame);
        logLoaded(log, "Parkour Duos");

        // ── Battle Royale ─────────────────────────────────────────────────────
        this.battleRoyaleMiniGame    = new BattleRoyaleMiniGame(this);
        this.battleRoyaleCommand     = new BattleRoyaleCommand(this, battleRoyaleMiniGame);
        this.battleRoyalePlaceholders = new BattleRoyalePlaceholders(this, battleRoyaleMiniGame);
        this.dropListener            = new DropListener(battleRoyaleMiniGame);
        miniGameManager.register(battleRoyaleMiniGame);
        logLoaded(log, "Battle Royale");

        this.pvpFinalMiniGame     = new PvpFinalMiniGame(this);
        this.pvpFinalCommand      = new PvpFinalCommand(this, pvpFinalMiniGame);
        this.pvpFinalPlaceholders = new PvpFinalPlaceholders(this, pvpFinalMiniGame);
        miniGameManager.register(pvpFinalMiniGame);
        logLoaded(log, "PvP Final");

        logDone(log, "Minijuegos", miniGameManager.getIds().size() + " registrados en MiniGameManager");

        this.intervalManager = new IntervalManager(this);

        this.eventManagerGUI = new EventManagerGUI(this);

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

        // /em battleroyale → manejado por EMCommand

        logDone(log, "Comandos");

        // ── Listeners ─────────────────────────────────────────────────────────
        logSection(log, "Listeners");
        Bukkit.getPluginManager().registerEvents(new TeamListener(this),           this);
        Bukkit.getPluginManager().registerEvents(new BingoGUI(this),               this);
        Bukkit.getPluginManager().registerEvents(bingoEditGUI,                     this);
        Bukkit.getPluginManager().registerEvents(bingoWallManager,                 this);
        Bukkit.getPluginManager().registerEvents(boatRacingListener,               this);
        Bukkit.getPluginManager().registerEvents(boatRacingAdminGUI,               this);
        Bukkit.getPluginManager().registerEvents(boatRacingMagicStick,             this);
        Bukkit.getPluginManager().registerEvents(teamSelectionGUI,                 this);
        Bukkit.getPluginManager().registerEvents(teamAdminGUI,                     this);
        Bukkit.getPluginManager().registerEvents(playerSelectorGUI,                this);
        Bukkit.getPluginManager().registerEvents(confirmGUI,                       this);
        Bukkit.getPluginManager().registerEvents(new TeamLobbyListener(this),      this);
        Bukkit.getPluginManager().registerEvents(new CinematicListener(this),      this);
        Bukkit.getPluginManager().registerEvents(cinematicAdminGUI,                this);
        Bukkit.getPluginManager().registerEvents(cinematicMarkerGUI,               this);
        Bukkit.getPluginManager().registerEvents(frozenHeistAdminGUI,              this);
        Bukkit.getPluginManager().registerEvents(frozenHeistMagicStick,            this);
        Bukkit.getPluginManager().registerEvents(eventManagerGUI,                  this);
        Bukkit.getPluginManager().registerEvents(tntRunAdminGUI,                   this);
        Bukkit.getPluginManager().registerEvents(tntRunMagicStick,                 this);
        Bukkit.getPluginManager().registerEvents(bingoAdminGUI,                    this);
        Bukkit.getPluginManager().registerEvents(bingoMagicStick,                  this);
        Bukkit.getPluginManager().registerEvents(parkourDuosAdminGUI,              this);
        Bukkit.getPluginManager().registerEvents(parkourDuosMagicStick,            this);
        Bukkit.getPluginManager().registerEvents(dropListener,                     this);
        Bukkit.getPluginManager().registerEvents(this,                             this);
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
            log.warning("  (PlaceholderAPI) -> No encontrado...");
        }

        cinematicManager.loadAll();
        log.info("  (CinematicManager) -> Cinematicas cargadas post-world-load.");

        Bukkit.getScheduler().runTaskLater(this, () -> teamLobbyManager.refreshAll(), 20L);
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
        player.sendMessage(Component.text("\u2714 Configuración recargada.", NamedTextColor.GREEN));
        getLogger().info("Configuración recargada por " + player.getName());
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
        log.info(GRAY + "  \u2502  " + GRAY + "Plugin para gestión del evento " + CYAN + "\"Ani Event\"" + GRAY + " de animalito  \u2502" + RESET);
        log.info(GRAY + "  \u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2518" + RESET);
        log.info(" ");
    }

    private void logSection(Logger log, String name) { log.info(GRAY + "  (" + CYAN + name + GRAY + ") -> Iniciando..." + RESET); }
    private void logDone(Logger log, String name)    { log.info(GRAY + "  (" + CYAN + name + GRAY + ") -> " + GREEN + "OK" + RESET); }
    private void logDone(Logger log, String name, String extra) { log.info(GRAY + "  (" + CYAN + name + GRAY + ") -> " + GREEN + "OK" + GRAY + "  (" + extra + ")" + RESET); }
    private void logLoaded(Logger log, String name)  { log.info(GRAY + "    \u2022 " + WHITE + name + RESET); }
    private String line() { return GRAY + "  \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" + RESET; }

    public CinematicEffects getCinematicEffects() { return cinematicManager.getEffects(); }

    // ── Getters ───────────────────────────────────────────────────────────────

    public static Anieventmanager    getInstance()             { return instance; }
    public EventManagerGUI           getEventManagerGUI()      { return eventManagerGUI; }
    public TeamManager               getTeamManager()          { return teamManager; }
    public ScoreManager              getScoreManager()         { return scoreManager; }
    public MiniGameManager           getMiniGameManager()      { return miniGameManager; }
    public TeamLobbyManager          getTeamLobbyManager()     { return teamLobbyManager; }
    public TeamSelectionGUI          getTeamSelectionGUI()     { return teamSelectionGUI; }
    public TeamAdminGUI              getTeamAdminGUI()         { return teamAdminGUI; }
    public PlayerSelectorGUI         getPlayerSelectorGUI()    { return playerSelectorGUI; }
    public ConfirmGUI                getConfirmGUI()           { return confirmGUI; }
    public TNTRunMiniGame            getTNTRunMiniGame()       { return tntRunMiniGame; }
    public TNTRunCommand             getTNTRunCommand()        { return tntRunCommand; }
    public TNTRunAdminGUI            getTNTRunAdminGUI()       { return tntRunAdminGUI; }
    public TNTRunMagicStick          getTNTRunMagicStick()     { return tntRunMagicStick; }
    public TNTRunPlaceholders getTNTRunPlaceholders() { return tntRunPlaceholders; }

    public BingoMiniGame             getBingoMiniGame()        { return bingoMiniGame; }
    public BingoCommand              getBingoCommand()         { return bingoCommand; }
    public BingoEditGUI              getBingoEditGUI()         { return bingoEditGUI; }
    public BingoWallManager          getBingoWallManager()     { return bingoWallManager; }
    public BoatRacingMiniGame        getBoatRacingMiniGame()   { return boatRacingMiniGame; }
    public BoatRacingCommand         getBoatRacingCommand()    { return boatRacingCommand; }
    public BoatRacingAdminGUI        getBoatRacingAdminGUI()   { return boatRacingAdminGUI; }
    public BoatRacingMagicStick      getBoatRacingMagicStick() { return boatRacingMagicStick; }
    public FrozenHeistMiniGame       getFrozenHeistMiniGame()  { return frozenHeistMiniGame; }
    public FrozenHeistCommand        getFrozenHeistCommand()   { return frozenHeistCommand; }
    public FrozenHeistAdminGUI       getFrozenHeistAdminGUI()  { return frozenHeistAdminGUI; }
    public FrozenHeistMagicStick     getFrozenHeistMagicStick(){ return frozenHeistMagicStick; }
    public ParkourDuosMiniGame       getParkourDuosMiniGame()  { return parkourDuosMiniGame; }
    public ParkourDuosCommand        getParkourDuosCommand()   { return parkourDuosCommand; }
    public ParkourDuosAdminGUI       getParkourDuosAdminGUI()  { return parkourDuosAdminGUI; }
    public ParkourDuosMagicStick     getParkourDuosMagicStick(){ return parkourDuosMagicStick; }
    public BattleRoyaleMiniGame      getBattleRoyaleMiniGame() { return battleRoyaleMiniGame; }
    public BattleRoyaleCommand        getBattleRoyaleCommand()      { return battleRoyaleCommand; }
    public BattleRoyalePlaceholders  getBattleRoyalePlaceholders() { return battleRoyalePlaceholders; }
    public PvpFinalMiniGame      getPvpFinalMiniGame()      { return pvpFinalMiniGame; }
    public PvpFinalCommand       getPvpFinalCommand()       { return pvpFinalCommand; }
    public PvpFinalPlaceholders  getPvpFinalPlaceholders()  { return pvpFinalPlaceholders; }
    public CinematicManager          getCinematicManager()     { return cinematicManager; }
    public CinematicAdminGUI         getCinematicAdminGUI()    { return cinematicAdminGUI; }
    public CinematicMarkerGUI        getCinematicMarkerGUI()   { return cinematicMarkerGUI; }
    public BingoAdminGUI             getBingoAdminGUI()        { return bingoAdminGUI; }
    public BingoMagicStick           getBingoMagicStick()      { return bingoMagicStick; }
    public IntervalManager getIntervalManager() { return intervalManager; }
}

// ── Getter adicional para BattleRoyaleCommand (agregar a los getters) ─────────
// YA ESTÁ incluido en el archivo generado arriba — solo verificar