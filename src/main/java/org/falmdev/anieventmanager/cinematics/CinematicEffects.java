package org.falmdev.anieventmanager.cinematics;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.*;

/**
 * Efectos visuales de cinematica via ProtocolLib.
 *
 * FIX Bug 2: eliminado el envío de UPDATE_HEALTH con health=0.
 * En Paper 1.21 ese packet efectivamente mata al jugador aunque esté
 * en SPECTATOR, porque el servidor procesa el daño antes de verificar
 * el gamemode. Solo ocultamos la XP bar, que no tiene side effects.
 *
 * Lo que hace ahora:
 *  - Oculta XP bar (EXPERIENCE packet con valores 0)
 *  - Oculta jugadores (ENTITY_DESTROY con sus IDs)
 *  - Oculta mano (ENTITY_EQUIPMENT con AIR)
 */
public class CinematicEffects {

    private final Anieventmanager plugin;
    private final ProtocolManager protocolManager;

    private final Set<UUID> activeViewers = new HashSet<>();

    public CinematicEffects(Anieventmanager plugin) {
        this.plugin          = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public void apply(Player player) {
        activeViewers.add(player.getUniqueId());
        hideXPBar(player);
        hideOtherPlayers(player);
        hideHand(player);
    }

    public void applyAll(List<Player> players) {
        for (Player p : players) apply(p);
    }

    public void restore(Player player) {
        activeViewers.remove(player.getUniqueId());
        restoreXPBar(player);
        restoreOtherPlayers(player);
        restoreHand(player);
    }

    public void restoreAll(List<Player> players) {
        for (Player p : players) restore(p);
    }

    public boolean isActive(Player player) {
        return activeViewers.contains(player.getUniqueId());
    }

    // ── XP Bar ───────────────────────────────────────────────────────────────
    // NOTA: NO ocultamos la barra de vida (UPDATE_HEALTH) porque en Paper 1.21
    // enviar health=0 mata al jugador independientemente del gamemode.
    // La barra de vida ya no es visible en SPECTATOR de todas formas.

    private void hideXPBar(Player player) {
        try {
            PacketContainer xpPacket = protocolManager.createPacket(
                    PacketType.Play.Server.EXPERIENCE);
            xpPacket.getFloat().write(0, 0f);   // experienceBar
            xpPacket.getIntegers().write(0, 0); // level
            xpPacket.getIntegers().write(1, 0); // totalExperience
            protocolManager.sendServerPacket(player, xpPacket);
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicEffects] hideXPBar: " + e.getMessage());
        }
    }

    private void restoreXPBar(Player player) {
        try {
            PacketContainer xpPacket = protocolManager.createPacket(
                    PacketType.Play.Server.EXPERIENCE);
            xpPacket.getFloat().write(0, player.getExp());
            xpPacket.getIntegers().write(0, player.getLevel());
            xpPacket.getIntegers().write(1, player.getTotalExperience());
            protocolManager.sendServerPacket(player, xpPacket);
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicEffects] restoreXPBar: " + e.getMessage());
        }
    }

    // ── Ocultar jugadores ─────────────────────────────────────────────────────

    private void hideOtherPlayers(Player viewer) {
        try {
            List<Integer> entityIds = new ArrayList<>();
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(viewer.getUniqueId())) {
                    entityIds.add(other.getEntityId());
                }
            }
            if (entityIds.isEmpty()) return;

            PacketContainer removePacket = protocolManager.createPacket(
                    PacketType.Play.Server.ENTITY_DESTROY);
            removePacket.getIntLists().write(0, entityIds);
            protocolManager.sendServerPacket(viewer, removePacket);
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicEffects] hideOtherPlayers: " + e.getMessage());
        }
    }

    private void restoreOtherPlayers(Player viewer) {
        try {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(viewer.getUniqueId())) {
                    protocolManager.updateEntity(other,
                            Collections.singletonList(viewer));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicEffects] restoreOtherPlayers: " + e.getMessage());
        }
    }

    // ── Ocultar mano ─────────────────────────────────────────────────────────

    private void hideHand(Player player) {
        try {
            PacketContainer equipPacket = protocolManager.createPacket(
                    PacketType.Play.Server.ENTITY_EQUIPMENT);
            equipPacket.getIntegers().write(0, player.getEntityId());

            List<com.comphenix.protocol.wrappers.Pair<
                    com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot,
                    org.bukkit.inventory.ItemStack>> slots = Arrays.asList(
                    new com.comphenix.protocol.wrappers.Pair<>(
                            com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot.MAINHAND,
                            new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR)),
                    new com.comphenix.protocol.wrappers.Pair<>(
                            com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot.OFFHAND,
                            new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR))
            );
            equipPacket.getSlotStackPairLists().write(0, slots);
            protocolManager.sendServerPacket(player, equipPacket);
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicEffects] hideHand: " + e.getMessage());
        }
    }

    private void restoreHand(Player player) {
        try {
            PacketContainer equipPacket = protocolManager.createPacket(
                    PacketType.Play.Server.ENTITY_EQUIPMENT);
            equipPacket.getIntegers().write(0, player.getEntityId());

            List<com.comphenix.protocol.wrappers.Pair<
                    com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot,
                    org.bukkit.inventory.ItemStack>> slots = Arrays.asList(
                    new com.comphenix.protocol.wrappers.Pair<>(
                            com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot.MAINHAND,
                            player.getInventory().getItemInMainHand()),
                    new com.comphenix.protocol.wrappers.Pair<>(
                            com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot.OFFHAND,
                            player.getInventory().getItemInOffHand())
            );
            equipPacket.getSlotStackPairLists().write(0, slots);
            protocolManager.sendServerPacket(player, equipPacket);
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicEffects] restoreHand: " + e.getMessage());
        }
    }
}