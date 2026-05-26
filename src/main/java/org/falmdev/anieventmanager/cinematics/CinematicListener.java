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
import org.falmdev.anieventmanager.cinematics.model.Cinematic;

/**
 * Listener para grabación y modo debug.
 *
 * Grabación:
 *   Tijera izquierda → togglePause()
 *   Tijera derecha   → stopRecording() + guardar mundo
 *
 * Modo debug:
 *   Click en slot 0 (pausa/play) → toggleDebugPause()
 *   Click en slot 1 (marker)     → addMarkerAtCurrentTick()
 *   Click en slot 2 (detener)    → stop()
 */
public class CinematicListener implements Listener {

    private final Anieventmanager plugin;

    public CinematicListener(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Action action = event.getAction();

        // ── Modo debug: detectar clicks en los botones del hotbar ─────────────
        CinematicPlayer cinematicPlayer = plugin.getCinematicManager().getPlayer();
        if (cinematicPlayer.isDebugAdmin(player)) {
            int slot = player.getInventory().getHeldItemSlot();
            boolean handled = cinematicPlayer.handleDebugHotbarClick(player, slot);
            if (handled) { event.setCancelled(true); return; }
        }

        // ── Grabación: detectar tijeras ───────────────────────────────────────
        CinematicRecorder recorder = plugin.getCinematicManager().getRecorder();
        if (!recorder.isRecordingAdmin(player)) return;
        if (!CinematicRecorder.isRecordingShears(
                player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            recorder.togglePause(player);

        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            Cinematic cinematic = recorder.getRecordingCinematic();
            if (cinematic != null) {
                plugin.getCinematicManager().setCinematicWorld(
                        cinematic.getId(), player.getWorld());
            }
            recorder.stopRecording();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Si estaba en modo debug, detener
        CinematicPlayer cinematicPlayer = plugin.getCinematicManager().getPlayer();
        if (cinematicPlayer.isDebugAdmin(player)) {
            cinematicPlayer.stop();
        }

        // Si estaba grabando, guardar y detener
        CinematicRecorder recorder = plugin.getCinematicManager().getRecorder();
        if (recorder.isRecordingAdmin(player)) {
            Cinematic cinematic = recorder.getRecordingCinematic();
            if (cinematic != null) {
                plugin.getCinematicManager().setCinematicWorld(
                        cinematic.getId(), player.getWorld());
            }
            recorder.stopRecording();
        }
    }
}