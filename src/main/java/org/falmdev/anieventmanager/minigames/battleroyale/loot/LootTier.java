package org.falmdev.anieventmanager.minigames.battleroyale.loot;

import org.bukkit.Color;

import java.util.List;

/**
 * Tier de loot — define color, particulas, items posibles y cantidades.
 */
public record LootTier(
        String       id,           // "COMMON", "RARE", "LEGENDARY"
        int          weight,       // probabilidad relativa de asignación a un cofre
        Color        particleColor,
        int          minItems,
        int          maxItems,
        List<LootEntry> items      // pool de items posibles con sus pesos
) {

    /**
     * Item posible dentro de un tier.
     */
    public record LootEntry(
            org.bukkit.Material material,
            int weight,
            int amount   // cantidad por stack al spawnear
    ) {}
}