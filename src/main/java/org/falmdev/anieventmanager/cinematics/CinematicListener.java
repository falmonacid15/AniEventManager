package org.falmdev.anieventmanager.cinematics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.cinematics.model.CinematicWaypoint;

/**
 * Listener del Magic Stick y eventos de grabación.
 *
 * Click derecho en el aire  → agregar waypoint sin texto
 * Click derecho en bloque   → agregar waypoint + abrir CinematicWaypointGUI
 * Shift + click derecho     → terminar grabación
 *
 * Si el admin se desconecta mientras graba, la grabación se guarda automáticamente.
 */
public class CinematicListener implements Listener {

    private final Anieventmanager plugin;

    public CinematicListener(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Solo mano principal para no duplicar
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        CinematicRecorder recorder = plugin.getCinematicManager().getRecorder();

        if (!recorder.isRecordingAdmin(player)) return;
        if (!CinematicRecorder.isMagicStick(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);

        Action action = event.getAction();

        // Shift + cualquier click → terminar grabación
        if (player.isSneaking() && (action == Action.RIGHT_CLICK_AIR
                || action == Action.RIGHT_CLICK_BLOCK)) {
            recorder.stopRecording();
            return;
        }

        boolean clickedBlock = action == Action.RIGHT_CLICK_BLOCK;
        boolean clickedAir   = action == Action.RIGHT_CLICK_AIR;

        if (!clickedBlock && !clickedAir) return;

        // Agregar waypoint
        CinematicWaypoint wp = recorder.addWaypoint(player, clickedBlock);
        if (wp == null) return;

        if (clickedBlock) {
            // Abrir editor de texto para este waypoint
            // Hacerlo en el tick siguiente para no conflictar con el cancel del event
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getCinematicWaypointGUI().open(player,
                            recorder.getRecordingCinematic(), wp));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CinematicRecorder recorder = plugin.getCinematicManager().getRecorder();
        if (recorder.isRecordingAdmin(event.getPlayer())) {
            recorder.stopRecording();
        }
    }
}