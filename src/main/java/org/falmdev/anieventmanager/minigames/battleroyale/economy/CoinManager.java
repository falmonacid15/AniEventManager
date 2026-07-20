package org.falmdev.anieventmanager.minigames.battleroyale.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleMiniGame;
import org.falmdev.anieventmanager.minigames.battleroyale.model.BRPlayer;

import java.util.*;
import java.util.stream.Collectors;


public class CoinManager {

    private final Anieventmanager      plugin;
    private final BattleRoyaleMiniGame game;

    public CoinManager(Anieventmanager plugin, BattleRoyaleMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    public int get(Player player) {
        BRPlayer brp = game.getBRPlayer(player);
        return brp != null ? brp.getCoins() : 0;
    }

    public int get(UUID uuid) {
        BRPlayer brp = game.getAllPlayers().get(uuid);
        return brp != null ? brp.getCoins() : 0;
    }

    public boolean add(Player player, int amount) {
        BRPlayer brp = game.getBRPlayer(player);
        if (brp == null) return false;
        brp.addCoins(amount);
        notifyChange(player, amount, brp.getCoins());
        return true;
    }

    public boolean remove(Player player, int amount) {
        BRPlayer brp = game.getBRPlayer(player);
        if (brp == null) return false;
        if (brp.getCoins() < amount) return false;
        brp.removeCoins(amount);
        notifyChange(player, -amount, brp.getCoins());
        return true;
    }

    public boolean set(Player player, int amount) {
        BRPlayer brp = game.getBRPlayer(player);
        if (brp == null) return false;
        int delta = amount - brp.getCoins();
        brp.setCoins(amount);
        notifyChange(player, delta, brp.getCoins());
        return true;
    }

    public int giveAll(int amount) {
        int affected = 0;
        for (BRPlayer brp : game.getAllPlayers().values()) {
            brp.addCoins(amount);
            Player p = Bukkit.getPlayer(brp.getUuid());
            if (p != null && p.isOnline()) notifyChange(p, amount, brp.getCoins());
            affected++;
        }
        return affected;
    }

    public void resetAll() {
        int starting = game.getConfig().getStartingCoins();
        for (BRPlayer brp : game.getAllPlayers().values()) {
            brp.setCoins(starting);
        }
        plugin.getLogger().info("[BR-Coins] Reset de balances. Inicial: " + starting);
    }

    public List<BRPlayer> getRanking() {
        return game.getAllPlayers().values().stream()
                .sorted(Comparator.comparingInt(BRPlayer::getCoins).reversed())
                .collect(Collectors.toList());
    }

    public BRPlayer getRankingAt(int position) {
        List<BRPlayer> ranking = getRanking();
        if (position < 1 || position > ranking.size()) return null;
        return ranking.get(position - 1);
    }

    private void notifyChange(Player player, int delta, int newTotal) {
        if (delta == 0) return;
        Component msg = Component.text(
                        (delta > 0 ? "+" : "") + delta + " monedas ",
                        delta > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)
                .append(Component.text("(total: " + newTotal + ")", NamedTextColor.GRAY));
        player.sendActionBar(msg);
    }
}