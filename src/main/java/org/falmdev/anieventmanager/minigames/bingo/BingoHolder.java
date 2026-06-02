package org.falmdev.anieventmanager.minigames.bingo;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class BingoHolder implements InventoryHolder {
    private final BingoConfig config;

    public BingoHolder(BingoConfig config) {
        this.config = config;
    }

    public BingoConfig getConfig() { return config; }
    @Override
    public Inventory getInventory() { return null; }
}