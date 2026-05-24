package org.falmdev.anieventmanager.lobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

/**
 * Listener del lobby de selección de equipos.
 *
 * Funciones:
 *  - Click derecho/izquierdo en cartel registrado → abre el TeamSelectionGUI
 *  - Bloquea la edición de carteles registrados (cuando se rompen — solo OP)
 *  - Bloquea SignChangeEvent en carteles registrados para que el plugin
 *    mantenga el control del texto
 *  - Al join del servidor, refresca las lámparas y carteles del jugador
 */
public class TeamLobbyListener implements Listener {

    private final Anieventmanager plugin;

    public TeamLobbyListener(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── Click en cartel ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Solo nos importa si el bloque es un cartel y está registrado
        if (!plugin.getTeamLobbyManager().getConfig().isSignRegistered(block)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        String teamId = plugin.getTeamLobbyManager().getConfig().getSignTeam(block);
        if (teamId == null) return;

        var teamOpt = plugin.getTeamManager().getTeam(teamId);
        if (teamOpt.isEmpty()) {
            player.sendMessage(Component.text(
                    "✘ Este cartel apunta a un equipo eliminado: " + teamId,
                    NamedTextColor.RED));
            return;
        }

        EventTeam team = teamOpt.get();
        plugin.getTeamSelectionGUI().open(player, team);
    }

    // ── Proteger carteles y lámparas registrados ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean isSign = plugin.getTeamLobbyManager().getConfig().isSignRegistered(block);
        boolean isLamp = plugin.getTeamLobbyManager().getConfig().isLampRegistered(block);
        if (!isSign && !isLamp) return;

        Player player = event.getPlayer();
        if (!player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(Component.text(
                    "✘ Este bloque está registrado en el lobby de equipos.",
                    NamedTextColor.RED));
            return;
        }

        // Si es OP, permitir romper pero limpiar el registro
        if (isSign) plugin.getTeamLobbyManager().getConfig().unregisterSign(block);
        if (isLamp) plugin.getTeamLobbyManager().getConfig().unregisterLamp(block);
        player.sendMessage(Component.text(
                "ℹ Bloque registrado eliminado y desregistrado del lobby.",
                NamedTextColor.GRAY));
    }

    /**
     * Si alguien intenta editar un cartel registrado, ignoramos su texto
     * y forzamos un refresh para mantener el formato del plugin.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        if (!plugin.getTeamLobbyManager().getConfig().isSignRegistered(block)) return;

        // Programar un refresh para el tick siguiente, después que el cliente
        // termine de procesar el SignChangeEvent
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getTeamLobbyManager().refreshSign(block), 1L);
    }

    // ── Refresh al join ───────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Refresh general — los chunks de los carteles pueden estar descargados
        // hasta que el jugador se acerca, así que refrescamos a los pocos ticks
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getTeamLobbyManager().refreshAll(), 40L);
    }
}