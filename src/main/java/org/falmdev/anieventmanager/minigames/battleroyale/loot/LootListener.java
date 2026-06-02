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

/**
 * LootListener — detecta apertura de cofres registrados.
 *
 * Cuando un jugador abre un cofre del Battle Royale por primera vez:
 *   1. Ese cofre queda marcado como "ya abierto en esta partida" (sin re-reward)
 *   2. Si el tier tiene coins-chance y se cumple → da monedas al jugador
 *
 * El loot del cofre lo pone el LootManager al hacer refill, esto NO toca items.
 * Solo gestiona las monedas como bonus al primer abrir.
 *
 * Al hacer refill manual o automático, el set de "ya abiertos" se resetea
 * (el LootManager llama a clearOpenedChests()).
 */
public class LootListener implements Listener {

    private final Anieventmanager      plugin;
    private final BattleRoyaleMiniGame game;

    // Set de bloques ya abiertos (key = "world:x:y:z")
    // Se limpia con cada refill
    private final Set<String> openedChests = ConcurrentHashMap.newKeySet();

    public LootListener(Anieventmanager plugin, BattleRoyaleMiniGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    /**
     * Limpiar set de cofres abiertos. Llamado desde LootManager.refill().
     */
    public void clearOpenedChests() {
        openedChests.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        // Solo cuando hay partida activa
        if (!game.isRunning()) return;

        // Solo para jugadores del BR
        Player player = event.getPlayer();
        var brp = game.getBRPlayer(player);
        if (brp == null) return;

        // Verificar que el bloque es un cofre registrado
        LootManager loot = game.getLootManager();
        if (loot == null) return;

        String key = blockKey(block);
        if (!isRegisteredChest(loot, block)) return;

        // Si ya fue abierto en esta partida, no dar monedas otra vez
        if (openedChests.contains(key)) return;
        openedChests.add(key);

        // Buscar el tier del cofre
        String tierId = findTier(loot, block);
        if (tierId == null) return;

        LootConfig.TierCoinReward reward = loot.getLootConfig().getCoinReward(tierId);
        if (reward == null) return;

        // Aplicar chance
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll >= reward.chance()) return; // no toca monedas

        // Calcular cantidad
        int amount;
        if (reward.max() <= reward.min()) amount = reward.min();
        else amount = ThreadLocalRandom.current().nextInt(reward.min(), reward.max() + 1);

        if (amount <= 0) return;

        // Sumar monedas al jugador
        game.getCoinManager().add(player, amount);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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