package org.falmdev.anieventmanager.cinematics;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.*;

/**
 * Efectos visuales de cinematica via ProtocolLib.
 *
 * Efectos aplicados:
 *  1. Bandas negras (letterbox) — via packet de equipamiento falso con calabaza.
 *     El RP reemplaza pumpkinblur.png con las bandas. Completamente client-side.
 *  2. XP bar oculta
 *  3. Jugadores ocultos
 *  4. Mano oculta
 */
public class CinematicEffects {

    private final Anieventmanager plugin;
    private final ProtocolManager protocolManager;
    private final Set<UUID> activeViewers = new HashSet<>();

    public CinematicEffects(Anieventmanager plugin) {
        this.plugin          = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void apply(Player player) {
        activeViewers.add(player.getUniqueId());
        // Nota: showLetterbox() NO se llama aquí.
        // Se llama desde CinematicPlayer con un delay de 2 ticks via applyLetterbox(),
        // para que el packet de calabaza llegue DESPUÉS del packet de SPECTATOR
        // que limpia el equipamiento del jugador.
        hideXPBar(player);
        hideOtherPlayers(player);
        hideHand(player);
    }

    /**
     * Envía solo el packet de letterbox (calabaza).
     * Llamar con runTaskLater 2 ticks después de apply() para que no
     * sea sobreescrito por el packet de equipamiento de SPECTATOR.
     */
    public void applyLetterbox(Player player) {
        if (!activeViewers.contains(player.getUniqueId())) return;
        showLetterbox(player);
    }

    public void applyAll(List<Player> players) {
        for (Player p : players) apply(p);
    }

    public void restore(Player player) {
        activeViewers.remove(player.getUniqueId());
        hideLetterbox(player);
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

    // ── 1. Letterbox via pumpkin packet ───────────────────────────────────────

    /**
     * Envía un packet de equipamiento falso que pone una calabaza tallada
     * en el slot de cabeza del jugador — solo en el cliente, el servidor
     * no sabe nada de esto.
     *
     * El resourcepack (updated_Cinematic_Bars.zip) reemplaza pumpkinblur.png
     * con las bandas negras cinematográficas, así que al "ponerse" la calabaza
     * el jugador ve las bandas en lugar del overlay original.
     */
    private void showLetterbox(Player player) {
        sendHelmetPacket(player, new ItemStack(Material.CARVED_PUMPKIN));
    }

    /**
     * Restaura el helmet real del jugador enviando otro packet de equipamiento.
     */
    private void hideLetterbox(Player player) {
        ItemStack realHelmet = player.getInventory().getHelmet();
        sendHelmetPacket(player, realHelmet != null ? realHelmet : new ItemStack(Material.AIR));
    }

    private void sendHelmetPacket(Player player, ItemStack helmet) {
        try {
            PacketContainer packet = protocolManager.createPacket(
                    PacketType.Play.Server.ENTITY_EQUIPMENT);
            packet.getIntegers().write(0, player.getEntityId());
            packet.getSlotStackPairLists().write(0,
                    Collections.singletonList(
                            new Pair<>(EnumWrappers.ItemSlot.HEAD, helmet)));
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicEffects] letterbox: " + e.getMessage());
        }
    }

    // ── 2. XP Bar ─────────────────────────────────────────────────────────────

    private void hideXPBar(Player player) {
        try {
            PacketContainer p = protocolManager.createPacket(
                    PacketType.Play.Server.EXPERIENCE);
            p.getFloat().write(0, 0f);
            p.getIntegers().write(0, 0);
            p.getIntegers().write(1, 0);
            protocolManager.sendServerPacket(player, p);
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicEffects] hideXPBar: " + e.getMessage());
        }
    }

    private void restoreXPBar(Player player) {
        try {
            PacketContainer p = protocolManager.createPacket(
                    PacketType.Play.Server.EXPERIENCE);
            p.getFloat().write(0, player.getExp());
            p.getIntegers().write(0, player.getLevel());
            p.getIntegers().write(1, player.getTotalExperience());
            protocolManager.sendServerPacket(player, p);
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicEffects] restoreXPBar: " + e.getMessage());
        }
    }

    // ── 3. Jugadores ──────────────────────────────────────────────────────────

    private void hideOtherPlayers(Player viewer) {
        try {
            List<Integer> ids = new ArrayList<>();
            for (Player o : Bukkit.getOnlinePlayers())
                if (!o.getUniqueId().equals(viewer.getUniqueId()))
                    ids.add(o.getEntityId());
            if (ids.isEmpty()) return;
            PacketContainer p = protocolManager.createPacket(
                    PacketType.Play.Server.ENTITY_DESTROY);
            p.getIntLists().write(0, ids);
            protocolManager.sendServerPacket(viewer, p);
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicEffects] hideOtherPlayers: " + e.getMessage());
        }
    }

    private void restoreOtherPlayers(Player viewer) {
        try {
            for (Player o : Bukkit.getOnlinePlayers())
                if (!o.getUniqueId().equals(viewer.getUniqueId()))
                    protocolManager.updateEntity(o, Collections.singletonList(viewer));
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicEffects] restoreOtherPlayers: " + e.getMessage());
        }
    }

    // ── 4. Mano ───────────────────────────────────────────────────────────────

    private void hideHand(Player player) {
        try {
            PacketContainer p = protocolManager.createPacket(
                    PacketType.Play.Server.ENTITY_EQUIPMENT);
            p.getIntegers().write(0, player.getEntityId());
            p.getSlotStackPairLists().write(0, Arrays.asList(
                    new Pair<>(EnumWrappers.ItemSlot.MAINHAND,
                            new ItemStack(Material.AIR)),
                    new Pair<>(EnumWrappers.ItemSlot.OFFHAND,
                            new ItemStack(Material.AIR))));
            protocolManager.sendServerPacket(player, p);
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicEffects] hideHand: " + e.getMessage());
        }
    }

    private void restoreHand(Player player) {
        try {
            PacketContainer p = protocolManager.createPacket(
                    PacketType.Play.Server.ENTITY_EQUIPMENT);
            p.getIntegers().write(0, player.getEntityId());
            p.getSlotStackPairLists().write(0, Arrays.asList(
                    new Pair<>(EnumWrappers.ItemSlot.MAINHAND,
                            player.getInventory().getItemInMainHand()),
                    new Pair<>(EnumWrappers.ItemSlot.OFFHAND,
                            player.getInventory().getItemInOffHand())));
            protocolManager.sendServerPacket(player, p);
        } catch (Exception e) {
            plugin.getLogger().warning("[CinematicEffects] restoreHand: " + e.getMessage());
        }
    }
}