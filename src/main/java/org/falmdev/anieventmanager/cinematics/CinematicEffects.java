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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.*;

/**
 * Efectos visuales de cinematica via ProtocolLib.
 *
 * Efectos aplicados:
 *  1. Bandas negras (letterbox) — via packet de equipamiento falso con calabaza.
 *  2. Fade in/out — DARKNESS con amplifier alto, igual que el Warden.
 *  3. XP bar oculta
 *  4. Jugadores ocultos
 *  5. Mano oculta
 *
 * Por qué DARKNESS funciona como el Warden:
 *   DARKNESS tiene una animación nativa de fade integrada en el cliente.
 *   Con amplifier 4+ la pantalla llega a negro total.
 *   El cliente hace el fade suave automáticamente al aplicarse y al expirar.
 *
 *   Fade in:  DARKNESS por fadeInTicks → expira → cliente hace fade negro→normal
 *   Fade out: DARKNESS por fadeOutTicks → esperar onset (~22 ticks) → restaurar
 *             jugador mientras está negro → efecto expira → fade nativo negro→normal
 *
 * DARKNESS_ONSET_TICKS: ticks que tarda DARKNESS en llegar a negro total.
 *   El Warden aplica el efecto en pulsos de 13 ticks con amplifier 5.
 *   Con amplifier alto y duración continua el onset es ~22 ticks (1.1s).
 */
public class CinematicEffects {

    // Amplifier que usa el Warden — suficiente para negro total
    private static final int DARKNESS_AMPLIFIER = 4;

    // Ticks que tarda DARKNESS en llegar a negro total al aplicarse
    // El Warden usa pulsos de 13 ticks — con efecto continuo el onset es ~22 ticks
    private static final int DARKNESS_ONSET_TICKS = 22;

    private final Anieventmanager plugin;
    private final ProtocolManager protocolManager;
    private final Set<UUID> activeViewers = new HashSet<>();

    public CinematicEffects(Anieventmanager plugin) {
        this.plugin          = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    // ── Aplicar / restaurar ───────────────────────────────────────────────────

    public void apply(Player player) {
        activeViewers.add(player.getUniqueId());
        hideXPBar(player);
        hideOtherPlayers(player);
        hideHand(player);
    }

    /**
     * Envía el packet de letterbox (calabaza) y aplica fade in si corresponde.
     * Llamar con runTaskLater 2 ticks después de apply().
     *
     * Fade in: DARKNESS por fadeInTicks con amplifier alto.
     * El cliente hace el fade negro→normal de forma nativa al expirar.
     */
    public void applyLetterbox(Player player, int fadeInTicks) {
        if (!activeViewers.contains(player.getUniqueId())) return;
        sendHelmetPacket(player, new ItemStack(Material.CARVED_PUMPKIN));

        if (fadeInTicks > 0) {
            // DARKNESS como el Warden — fade nativo al expirar
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS,
                    fadeInTicks,
                    DARKNESS_AMPLIFIER,
                    false,  // ambient
                    false,  // particles
                    false   // icon
            ));
        }
    }

    /** Versión sin fade — retrocompatible. */
    public void applyLetterbox(Player player) {
        applyLetterbox(player, 0);
    }

    /**
     * Fade OUT: aplica DARKNESS, espera DARKNESS_ONSET_TICKS para que la
     * pantalla esté negra, luego llama onComplete para restaurar al jugador.
     *
     * El jugador se restaura mientras la pantalla está negra.
     * DARKNESS expira después → cliente hace fade negro→normal nativo.
     *
     * fadeOutTicks debe ser > DARKNESS_ONSET_TICKS para que el jugador
     * tenga tiempo de ser restaurado antes de que el efecto expire.
     * Recomendado: mínimo 40 ticks (2s).
     */
    public void applyFadeOut(Player player, int fadeOutTicks, Runnable onComplete) {
        if (fadeOutTicks <= 0) {
            if (onComplete != null) Bukkit.getScheduler().runTask(plugin, onComplete);
            return;
        }

        // DARKNESS con amplifier alto — pantalla va a negro con animación nativa
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS,
                fadeOutTicks,
                DARKNESS_AMPLIFIER,
                false, false, false
        ));

        // Esperar a que la pantalla esté completamente negra antes de restaurar
        // El jugador no verá el salto de posición/gamemode porque está en negro
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (onComplete != null) onComplete.run();
        }, DARKNESS_ONSET_TICKS);
    }

    public void applyAll(List<Player> players) {
        for (Player p : players) apply(p);
    }

    public void restore(Player player) {
        activeViewers.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.BLINDNESS);
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