package org.falmdev.anieventmanager.minigames.pvpfinal;

import org.bukkit.Bukkit;
import org.falmdev.anieventmanager.Anieventmanager;

import org.falmdev.anieventmanager.managers.MiniGame;
import org.falmdev.anieventmanager.minigames.pvpfinal.arena.ArenaManager;
import org.falmdev.anieventmanager.minigames.pvpfinal.combat.CombatListener;
import org.falmdev.anieventmanager.minigames.pvpfinal.combat.CombatManager;
import org.falmdev.anieventmanager.minigames.pvpfinal.kit.KitManager;

public class PvpFinalMiniGame implements MiniGame {

    private final Anieventmanager  plugin;
    private final KitManager       kitManager;
    private final ArenaManager     arenaManager;
    private final CombatManager    combatManager;
    private final CombatListener   combatListener;

    public PvpFinalMiniGame(Anieventmanager plugin) {
        this.plugin         = plugin;
        this.kitManager     = new KitManager(plugin);
        this.arenaManager   = new ArenaManager(plugin);
        this.combatManager  = new CombatManager(plugin, this);
        this.combatListener = new CombatListener(plugin, this);

        Bukkit.getPluginManager().registerEvents(combatListener, plugin);
    }

    public KitManager     getKitManager()     { return kitManager; }
    public ArenaManager   getArenaManager()   { return arenaManager; }
    public CombatManager  getCombatManager()  { return combatManager; }
    public CombatListener getCombatListener() { return combatListener; }

    @Override public String  getId()          { return "pvpfinal"; }
    @Override public String  getDisplayName() { return "PvP Final"; }
    @Override public boolean isRunning()      { return combatManager.isActive(); }
    @Override public boolean isIdle()         { return !combatManager.isActive(); }

    @Override
    public String getStateName() {
        return combatManager.getState().name();
    }

    @Override
    public boolean sendToLobby() {
        return false;
    }

    @Override
    public boolean start() {
        return false;
    }

    @Override
    public void forceStop() {
        combatManager.stopCombat();
    }

    @Override
    public void reloadConfig() {
        kitManager.load();
        arenaManager.load();
    }

    @Override
    public String validateConfig() {
        if (!arenaManager.hasArena()) return "No hay arena creada.";
        if (!arenaManager.getArena().isReady()) return "Arena incompleta (faltan spawns o lobby).";
        if (kitManager.count() == 0) return "No hay kits creados.";
        return null;
    }
}