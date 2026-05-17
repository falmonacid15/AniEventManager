package org.falmdev.anieventmanager;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.falmdev.anieventmanager.commands.EMCommand;
import org.falmdev.anieventmanager.listeners.TeamListener;
import org.falmdev.anieventmanager.managers.ScoreManager;
import org.falmdev.anieventmanager.managers.TeamManager;
import org.falmdev.anieventmanager.minigames.bingo.BingoCommand;
import org.falmdev.anieventmanager.minigames.bingo.BingoEditGUI;
import org.falmdev.anieventmanager.minigames.bingo.BingoGUI;
import org.falmdev.anieventmanager.minigames.bingo.BingoMiniGame;
import org.falmdev.anieventmanager.minigames.frozenheist.FrozenHeistCommand;
import org.falmdev.anieventmanager.minigames.frozenheist.FrozenHeistMiniGame;
import org.falmdev.anieventmanager.minigames.tntrun.TNTRunCommand;
import org.falmdev.anieventmanager.minigames.tntrun.TNTRunMiniGame;
import org.falmdev.anieventmanager.placeholders.AniEventExpansion;

public final class Anieventmanager extends JavaPlugin implements Listener {

    private static Anieventmanager instance;

    private TeamManager    teamManager;
    private ScoreManager   scoreManager;
    private TNTRunMiniGame tntRunMiniGame;
    private TNTRunCommand  tntRunCommand;
    private BingoMiniGame  bingoMiniGame;
    private BingoCommand   bingoCommand;
    private BingoEditGUI   bingoEditGUI;
    private FrozenHeistMiniGame frozenHeistMiniGame;
    private FrozenHeistCommand  frozenHeistCommand;


    @Override
    public void onEnable() {
        instance = this;

        // Managers
        this.teamManager   = new TeamManager(this);
        this.scoreManager  = new ScoreManager(this);

        // Minijuegos
        this.tntRunMiniGame = new TNTRunMiniGame(this);
        this.tntRunCommand  = new TNTRunCommand(this, tntRunMiniGame);
        this.bingoMiniGame  = new BingoMiniGame(this);
        this.bingoCommand   = new BingoCommand(this, bingoMiniGame);
        this.bingoEditGUI   = new BingoEditGUI(this);

        // Comando principal /em
        EMCommand emCommand = new EMCommand(this);
        var emCmd = getCommand("em");
        if (emCmd != null) {
            emCmd.setExecutor(emCommand);
            emCmd.setTabCompleter(emCommand);
        }

        // Comando /bingo para jugadores
        var bingoCmd = getCommand("bingo");
        if (bingoCmd != null) {
            bingoCmd.setExecutor(bingoCommand);
            bingoCmd.setTabCompleter(bingoCommand);
        }

        // Listeners globales
        Bukkit.getPluginManager().registerEvents(new TeamListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BingoGUI(), this);
        Bukkit.getPluginManager().registerEvents(bingoEditGUI, this);
        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("AniEventManager habilitado.");
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AniEventExpansion(this).register();
            getLogger().info("PlaceholderAPI encontrado — placeholders registrados.");
        } else {
            getLogger().warning("PlaceholderAPI no encontrado — los placeholders no estaran disponibles.");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("AniEventManager deshabilitado.");
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public static Anieventmanager getInstance() { return instance; }
    public TeamManager    getTeamManager()      { return teamManager; }
    public ScoreManager   getScoreManager()     { return scoreManager; }
    public TNTRunMiniGame getTNTRunMiniGame()   { return tntRunMiniGame; }
    public TNTRunCommand  getTNTRunCommand()    { return tntRunCommand; }
    public BingoMiniGame  getBingoMiniGame()    { return bingoMiniGame; }
    public BingoCommand   getBingoCommand()     { return bingoCommand; }
    public BingoEditGUI   getBingoEditGUI()     { return bingoEditGUI; }
}