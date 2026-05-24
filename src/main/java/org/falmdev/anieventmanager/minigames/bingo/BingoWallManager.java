package org.falmdev.anieventmanager.minigames.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.falmdev.anieventmanager.Anieventmanager;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.*;

public class BingoWallManager implements Listener {

    private final Anieventmanager plugin;
    private final BingoConfig config;

    // UUID -> [pos1, pos2] (null si aún no marcada)
    private final Map<UUID, Location[]> selections = new HashMap<>();
    // UUID -> wallId que está configurando
    private final Map<UUID, String> awaitingWall = new HashMap<>();

    public BingoWallManager(Anieventmanager plugin) {
        this.plugin = plugin;
        this.config = plugin.getBingoMiniGame().getConfig();
    }

    // ── Selección de esquinas ─────────────────────────────────────────────────

    /**
     * Inicia el proceso de selección de esquinas para una pared.
     */
    public void startSelection(Player player, String wallId) {
        awaitingWall.put(player.getUniqueId(), wallId);
        selections.put(player.getUniqueId(), new Location[2]);
        player.sendMessage(Component.text("Haz click derecho en los dos bloques que forman las esquinas de la pared '",
                        NamedTextColor.YELLOW)
                .append(Component.text(wallId, NamedTextColor.WHITE))
                .append(Component.text("'.", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Primero la esquina 1, luego la esquina 2.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Escribe 'cancelar' en el chat para cancelar.", NamedTextColor.GRAY));
    }

    public boolean isSelecting(Player player) {
        return awaitingWall.containsKey(player.getUniqueId());
    }

    public void cancelSelection(Player player) {
        awaitingWall.remove(player.getUniqueId());
        selections.remove(player.getUniqueId());
        player.sendMessage(Component.text("Selección cancelada.", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Solo click derecho en bloque, solo mano principal
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        if (!awaitingWall.containsKey(uid)) return;

        event.setCancelled(true);

        Location[] sel = selections.get(uid);
        Location clicked = event.getClickedBlock().getLocation();

        if (sel[0] == null) {
            sel[0] = clicked;
            player.sendMessage(Component.text("✔ Esquina 1 marcada: ", NamedTextColor.GREEN)
                    .append(locComponent(clicked)));
            player.sendMessage(Component.text("Ahora haz click derecho en la esquina 2.", NamedTextColor.YELLOW));
        } else if (sel[1] == null) {
            sel[1] = clicked;
            player.sendMessage(Component.text("✔ Esquina 2 marcada: ", NamedTextColor.GREEN)
                    .append(locComponent(clicked)));

            // Guardar la pared
            String wallId = awaitingWall.remove(uid);
            selections.remove(uid);

            BingoWall wall = new BingoWall(wallId,
                    clicked.getWorld().getName(),
                    sel[0].getBlockX(), sel[0].getBlockY(), sel[0].getBlockZ(),
                    sel[1].getBlockX(), sel[1].getBlockY(), sel[1].getBlockZ());

            config.saveWall(wall);
            player.sendMessage(Component.text("✔ Pared '", NamedTextColor.GREEN)
                    .append(Component.text(wallId, NamedTextColor.YELLOW))
                    .append(Component.text("' guardada. (" +
                            Math.abs(sel[1].getBlockX() - sel[0].getBlockX() + 1) + "×" +
                            Math.abs(sel[1].getBlockY() - sel[0].getBlockY() + 1) + "×" +
                            Math.abs(sel[1].getBlockZ() - sel[0].getBlockZ() + 1) +
                            " bloques)", NamedTextColor.GREEN)));
            player.sendMessage(Component.text("Usa /em bingo wall place " + wallId +
                    " para colocarla.", NamedTextColor.GRAY));
        }
    }

    // ── Colocar / quitar barriers ─────────────────────────────────────────────

    public void placeWall(BingoWall wall, Material mat) {
        World world = Bukkit.getWorld(wall.getWorld());
        if (world == null) return;

        int minX = Math.min(wall.getX1(), wall.getX2());
        int maxX = Math.max(wall.getX1(), wall.getX2());
        int minY = Math.min(wall.getY1(), wall.getY2());
        int maxY = Math.max(wall.getY1(), wall.getY2());
        int minZ = Math.min(wall.getZ1(), wall.getZ2());
        int maxZ = Math.max(wall.getZ1(), wall.getZ2());

        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    world.getBlockAt(x, y, z).setType(mat, false);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingWall.containsKey(player.getUniqueId())) return;

        if (event.getMessage().trim().equalsIgnoreCase("cancelar")) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> cancelSelection(player));
        }
    }

    /**
     * Quita todas las paredes configuradas (pone AIR).
     * Se llama al iniciar la partida.
     */
    public void clearAllWalls() {
        for (BingoWall wall : config.loadWalls()) {
            placeWall(wall, Material.AIR);
        }
    }

    /**
     * Coloca todas las paredes configuradas (pone BARRIER).
     */
    public void placeAllWalls() {
        for (BingoWall wall : config.loadWalls()) {
            placeWall(wall, Material.BARRIER);
        }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private Component locComponent(Location l) {
        return Component.text(
                String.format("%d, %d, %d", l.getBlockX(), l.getBlockY(), l.getBlockZ()),
                NamedTextColor.WHITE);
    }
}