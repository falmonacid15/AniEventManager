package org.falmdev.anieventmanager.minigames.battleroyale.loot;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.minigames.battleroyale.BattleRoyaleMiniGame;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;


public class LootListener implements Listener {

    private final Anieventmanager      plugin;
    private final BattleRoyaleMiniGame game;

    private final Set<String> openedChests = ConcurrentHashMap.newKeySet();

    public LootListener(Anieventmanager plugin, BattleRoyaleMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    public void clearOpenedChests() {
        openedChests.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        if (!game.isRunning()) return;

        Player player = event.getPlayer();
        var brp = game.getBRPlayer(player);
        if (brp == null) return;

        LootManager loot = game.getLootManager();
        if (loot == null) return;

        String key = blockKey(block);
        if (!isRegisteredChest(loot, block)) return;

        if (openedChests.contains(key)) return;
        openedChests.add(key);

        String tierId = findTier(loot, block);
        if (tierId == null) return;

        LootConfig.TierCoinReward reward = loot.getLootConfig().getCoinReward(tierId);
        if (reward == null) return;

        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll >= reward.chance()) return;

        int amount;
        if (reward.max() <= reward.min()) amount = reward.min();
        else amount = ThreadLocalRandom.current().nextInt(reward.min(), reward.max() + 1);

        if (amount <= 0) return;

        game.getCoinManager().add(player, amount);
    }

    public boolean isOpened(LootChest chest) {
        return openedChests.contains(
                chest.getWorldName() + ":" + chest.getX() + ":" + chest.getY() + ":" + chest.getZ());
    }

    private boolean isRegisteredChest(LootManager loot, Block block) {
        for (LootChest c : loot.getChests()) {
            if (c.getX() == block.getX() && c.getY() == block.getY()
                    && c.getZ() == block.getZ()
                    && c.getWorldName().equals(block.getWorld().getName())) {
                return true;
            }
        }
        return false;
    }

    private String findTier(LootManager loot, Block block) {
        for (LootChest c : loot.getChests()) {
            if (c.getX() == block.getX() && c.getY() == block.getY()
                    && c.getZ() == block.getZ()
                    && c.getWorldName().equals(block.getWorld().getName())) {
                return c.getTierId();
            }
        }
        return null;
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ":"
                + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}