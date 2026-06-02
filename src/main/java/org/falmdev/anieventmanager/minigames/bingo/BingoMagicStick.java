package org.falmdev.anieventmanager.minigames.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Magic Stick para el Bingo.
 *
 * Modos:
 *   SET_SPAWN   → Setea el spawn del Bingo
 *   WALL_POS1   → Marca la esquina 1 de una pared
 *   WALL_POS2   → Marca la esquina 2 (completa la pared)
 *
 * Flujo de pared:
 *   1. Cambiar a modo WALL_POS1 (pide ID por chat si no está seteado)
 *   2. Click derecho en bloque → guarda pos1, cambia a WALL_POS2
 *   3. Click derecho en bloque → guarda pos2, guarda pared, vuelve a SET_SPAWN
 */
public class BingoMagicStick implements Listener {

    public enum Mode {
        SET_SPAWN ("Setear Spawn",      NamedTextColor.GREEN),
        WALL_POS1 ("Pared → Esquina 1", NamedTextColor.YELLOW),
        WALL_POS2 ("Pared → Esquina 2", NamedTextColor.GOLD);

        private final String displayName;
        private final NamedTextColor color;

        Mode(String displayName, NamedTextColor color) {
            this.displayName = displayName;
            this.color       = color;
        }

        public String getDisplayName()   { return displayName; }
        public NamedTextColor getColor() { return color; }

        public Mode next() {
            // Ciclo simple: SET_SPAWN → WALL_POS1 → SET_SPAWN (WALL_POS2 lo maneja el sistema)
            return this == SET_SPAWN ? WALL_POS1 : SET_SPAWN;
        }
    }

    private static final String STICK_NAME = "✦ Magic Stick [Bingo]";

    private final Anieventmanager plugin;
    private final Map<UUID, Mode>     playerModes    = new HashMap<>();
    private final Map<UUID, String>   pendingWallIds = new HashMap<>();  // uuid → wallId
    private final Map<UUID, Location> pendingPos1    = new HashMap<>();  // uuid → pos1

    public BingoMagicStick(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public void giveMagicStick(Player player) {
        if (!playerModes.containsKey(player.getUniqueId())) {
            playerModes.put(player.getUniqueId(), Mode.SET_SPAWN);
        }
        Mode mode = playerModes.get(player.getUniqueId());
        player.getInventory().setItemInMainHand(buildStick(mode));
        player.sendMessage(Component.text("✦ Magic Stick [Bingo] — Modo: ", NamedTextColor.GOLD)
                .append(Component.text(mode.getDisplayName(), mode.getColor(), TextDecoration.BOLD)));
        player.sendMessage(Component.text(
                "  → Shift+Click para cambiar modo.", NamedTextColor.DARK_GRAY));
    }

    public Mode getMode(Player player) {
        return playerModes.get(player.getUniqueId());
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isMagicStick(item)) return;

        boolean isRightClick = event.getAction() == Action.RIGHT_CLICK_BLOCK
                || event.getAction() == Action.RIGHT_CLICK_AIR;
        boolean isBlock = event.getAction() == Action.RIGHT_CLICK_BLOCK;

        if (!isRightClick) return;
        event.setCancelled(true);

        // Shift+Click → ciclar modo
        if (player.isSneaking()) {
            cycleMode(player);
            return;
        }

        Mode mode = playerModes.getOrDefault(player.getUniqueId(), Mode.SET_SPAWN);

        switch (mode) {
            case SET_SPAWN -> {
                plugin.getBingoMiniGame().getConfig().setSpawn(player.getLocation());
                ok(player, "Spawn del Bingo seteado en " + locStr(player.getLocation()) + ".");
                updateStick(player, mode);
            }
            case WALL_POS1 -> {
                if (!isBlock) { err(player, "Haz click en un bloque para marcar la esquina 1."); return; }
                // Pedir ID si no tiene uno pendiente
                if (!pendingWallIds.containsKey(player.getUniqueId())) {
                    player.sendMessage(Component.text(
                            "✎ Escribe el ID de la pared en el chat (o 'cancelar'):",
                            NamedTextColor.YELLOW));
                    // Guardamos la localización temporalmente
                    assert event.getClickedBlock() != null;
                    pendingPos1.put(player.getUniqueId(), event.getClickedBlock().getLocation());
                    // Ponemos un estado especial esperando el ID
                    pendingWallIds.put(player.getUniqueId(), "__awaiting__");
                    return;
                }
                if (pendingWallIds.get(player.getUniqueId()).equals("__awaiting__")) {
                    err(player, "Primero escribe el ID de la pared en el chat.");
                    return;
                }
                // Ya tiene ID → guardar pos1
                assert event.getClickedBlock() != null;
                pendingPos1.put(player.getUniqueId(), event.getClickedBlock().getLocation());
                // Cambiar a modo pos2
                playerModes.put(player.getUniqueId(), Mode.WALL_POS2);
                player.getInventory().setItemInMainHand(buildStick(Mode.WALL_POS2));
                ok(player, "Esquina 1 marcada en " + locStr(event.getClickedBlock().getLocation()) + ".");
                player.sendMessage(Component.text(
                        "  → Ahora haz click derecho en la esquina 2.", NamedTextColor.GRAY));
            }
            case WALL_POS2 -> {
                if (!isBlock) { err(player, "Haz click en un bloque para marcar la esquina 2."); return; }
                Location pos1 = pendingPos1.remove(player.getUniqueId());
                String wallId = pendingWallIds.remove(player.getUniqueId());
                if (pos1 == null || wallId == null) {
                    err(player, "Error: falta la esquina 1. Reinicia el proceso.");
                    playerModes.put(player.getUniqueId(), Mode.SET_SPAWN);
                    return;
                }
                assert event.getClickedBlock() != null;
                Location pos2 = event.getClickedBlock().getLocation();
                // Guardar pared
                BingoWall wall = new BingoWall(wallId,
                        pos1.getWorld().getName(),
                        pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
                        pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ());
                plugin.getBingoMiniGame().getConfig().saveWall(wall);
                ok(player, "Pared '" + wallId + "' guardada.");
                player.sendMessage(Component.text(
                        "  Esquina 1: " + locStr(pos1) + "  →  Esquina 2: " + locStr(pos2),
                        NamedTextColor.GRAY));
                // Volver a SET_SPAWN
                playerModes.put(player.getUniqueId(), Mode.SET_SPAWN);
                player.getInventory().setItemInMainHand(buildStick(Mode.SET_SPAWN));
            }
        }
    }

    // ── Chat listener para capturar el ID de la pared ─────────────────────────

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!pendingWallIds.containsKey(player.getUniqueId())) return;
        if (!pendingWallIds.get(player.getUniqueId()).equals("__awaiting__")) return;

        event.setCancelled(true);
        String msg = event.getMessage().trim();

        if (msg.equalsIgnoreCase("cancelar")) {
            pendingWallIds.remove(player.getUniqueId());
            pendingPos1.remove(player.getUniqueId());
            playerModes.put(player.getUniqueId(), Mode.SET_SPAWN);
            player.sendMessage(Component.text("Cancelado.", NamedTextColor.GRAY));
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                    player.getInventory().setItemInMainHand(buildStick(Mode.SET_SPAWN)));
            return;
        }

        String wallId = msg.toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (wallId.isEmpty()) {
            player.sendMessage(Component.text("ID inválido. Solo letras, números y _.", NamedTextColor.RED));
            return;
        }

        // Registrar el ID y cambiar a pos1 activo
        pendingWallIds.put(player.getUniqueId(), wallId);

        // La pos1 ya la tenemos (fue guardada en el click anterior)
        Location savedPos1 = pendingPos1.get(player.getUniqueId());
        if (savedPos1 != null) {
            // Ya teníamos la pos1 esperando — cambiar a modo WALL_POS2
            playerModes.put(player.getUniqueId(), Mode.WALL_POS2);
            player.sendMessage(Component.text("✔ ID: ", NamedTextColor.GREEN)
                    .append(Component.text(wallId, NamedTextColor.WHITE))
                    .append(Component.text(" — Esquina 1 ya registrada.", NamedTextColor.GREEN)));
            player.sendMessage(Component.text(
                    "  → Haz click derecho en la esquina 2.", NamedTextColor.GRAY));
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                    player.getInventory().setItemInMainHand(buildStick(Mode.WALL_POS2)));
        } else {
            // No hay pos1 aún — activar modo WALL_POS1 normal
            playerModes.put(player.getUniqueId(), Mode.WALL_POS1);
            player.sendMessage(Component.text("✔ ID: ", NamedTextColor.GREEN)
                    .append(Component.text(wallId, NamedTextColor.WHITE))
                    .append(Component.text(" — Ahora haz click en la esquina 1.", NamedTextColor.GREEN)));
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                    player.getInventory().setItemInMainHand(buildStick(Mode.WALL_POS1)));
        }
    }

    // ── Ciclar modo ───────────────────────────────────────────────────────────

    private void cycleMode(Player player) {
        Mode current = playerModes.getOrDefault(player.getUniqueId(), Mode.SET_SPAWN);
        // Si estamos en medio de una pared, no ciclar
        if (current == Mode.WALL_POS2) {
            err(player, "Completa la pared actual antes de cambiar de modo.");
            return;
        }
        Mode next = current.next();
        playerModes.put(player.getUniqueId(), next);

        if (next == Mode.WALL_POS1) {
            // Pedir ID antes de continuar
            pendingWallIds.remove(player.getUniqueId());
            pendingPos1.remove(player.getUniqueId());
            player.sendMessage(Component.text("✎ Escribe el ID de la nueva pared (o 'cancelar'):",
                    NamedTextColor.YELLOW));
            pendingWallIds.put(player.getUniqueId(), "__awaiting__");
        }

        updateStick(player, next);
        player.sendMessage(Component.text("✦ Modo: ", NamedTextColor.GOLD)
                .append(Component.text(next.getDisplayName(), next.getColor(), TextDecoration.BOLD)));
    }

    private void updateStick(Player player, Mode mode) {
        ItemStack current = player.getInventory().getItemInMainHand();
        if (isMagicStick(current)) {
            player.getInventory().setItemInMainHand(buildStick(mode));
        }
    }

    // ── Construcción del item ─────────────────────────────────────────────────

    private ItemStack buildStick(Mode mode) {
        ItemBuilder b = ItemBuilder.of(Material.BLAZE_ROD)
                .name(STICK_NAME, NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine()
                .lore(Component.text("Modo activo:", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("  ▶ " + mode.getDisplayName(), mode.getColor(), TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false))
                .emptyLine();

        for (Mode m : Mode.values()) {
            boolean active = m == mode;
            b.lore(Component.text(
                    (active ? "  ▶ " : "  ○ ") + m.getDisplayName(),
                    active ? m.getColor() : NamedTextColor.DARK_GRAY
            ).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, active));
        }

        b.emptyLine()
                .lore(Component.text("Click derecho", NamedTextColor.YELLOW)
                        .append(Component.text(" — Ejecutar modo", NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Shift + Click", NamedTextColor.YELLOW)
                        .append(Component.text(" — Cambiar modo", NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false));

        return b.build();
    }

    public static boolean isMagicStick(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        if (!item.hasItemMeta() || item.getItemMeta().displayName() == null) return false;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(item.getItemMeta().displayName());
        return name.equals(STICK_NAME);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String locStr(Location l) {
        return String.format("%.0f, %.0f, %.0f", l.getX(), l.getY(), l.getZ());
    }
    private void ok(Player p, String msg)  { p.sendMessage(Component.text("✔ " + msg, NamedTextColor.GREEN)); }
    private void err(Player p, String msg) { p.sendMessage(Component.text("✘ " + msg, NamedTextColor.RED)); }
}