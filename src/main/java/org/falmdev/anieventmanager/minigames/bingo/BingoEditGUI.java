package org.falmdev.anieventmanager.minigames.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI de edición de una tarea del bingo.
 *
 * Layout (36 slots = 4 filas x 9 columnas):
 *   Fila 0: borders
 *   Fila 1: border | ICON(10) | border | TITLE(12) | border | DESC(14) | border | RESET(16) | border
 *   Fila 2: borders
 *   Fila 3: borders con INFO(28) en el centro
 *
 * Descripción: soporta saltos de línea usando \n en el chat.
 *   Ejemplo: "Primera línea\nSegunda línea\n&aTercera en verde"
 */
public class BingoEditGUI implements Listener {

    private static final int SLOT_ICON  = 10;
    private static final int SLOT_TITLE = 12;
    private static final int SLOT_DESC  = 14;
    private static final int SLOT_RESET = 16;
    private static final int SLOT_INFO  = 28;

    private final Anieventmanager plugin;
    private final BingoConfig config;

    private final Map<UUID, String> awaitingTitle = new HashMap<>();
    private final Map<UUID, String> awaitingDesc  = new HashMap<>();

    public BingoEditGUI(Anieventmanager plugin) {
        this.plugin = plugin;
        this.config = plugin.getBingoMiniGame().getConfig();
    }

    // ── Abrir el editor ───────────────────────────────────────────────────────

    public void open(Player player, BingoTask task) {
        Inventory inv = Bukkit.createInventory(null, 36,
                Component.text("Editar tarea: ", NamedTextColor.GOLD)
                        .append(Component.text(task.getId(), NamedTextColor.YELLOW)));

        fillBorders(inv);
        inv.setItem(SLOT_ICON,  buildIconSlot(task));
        inv.setItem(SLOT_TITLE, buildTitleSlot(task));
        inv.setItem(SLOT_DESC,  buildDescSlot(task));
        inv.setItem(SLOT_RESET, buildResetSlot(task));
        inv.setItem(SLOT_INFO,  buildInfoSlot(task));

        player.openInventory(inv);
    }

    // ── Clicks ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String titlePlain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());

        if (!titlePlain.startsWith("Editar tarea: ")) return;

        event.setCancelled(true);

        String taskId = titlePlain.replace("Editar tarea: ", "").trim();
        int slot = event.getRawSlot();

        BingoTask task = config.loadTasks().stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst().orElse(null);

        if (task == null) {
            player.sendMessage(Component.text("Tarea no encontrada.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        switch (slot) {

            case SLOT_ICON -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    player.sendMessage(Component.text(
                            "Debes tener un ítem en la mano para usarlo como icono.",
                            NamedTextColor.RED));
                    return;
                }
                task.setIcon(hand.getType());
                config.saveTask(task);
                player.sendMessage(Component.text("✔ Icono cambiado a ", NamedTextColor.GREEN)
                        .append(Component.text(hand.getType().name().toLowerCase()
                                .replace('_', ' '), NamedTextColor.WHITE))
                        .append(Component.text(".", NamedTextColor.GREEN)));
                plugin.getServer().getScheduler().runTask(plugin, () -> open(player, task));
            }

            case SLOT_TITLE -> {
                player.closeInventory();
                awaitingTitle.put(player.getUniqueId(), taskId);
                player.sendMessage(Component.text(
                                "Escribe el nuevo título para '", NamedTextColor.YELLOW)
                        .append(Component.text(taskId, NamedTextColor.WHITE))
                        .append(Component.text("' en el chat:", NamedTextColor.YELLOW)));
                player.sendMessage(Component.text(
                        "Usa &a, &c, &l, etc. para colores. 'cancelar' para cancelar.",
                        NamedTextColor.GRAY));
            }

            case SLOT_DESC -> {
                player.closeInventory();
                awaitingDesc.put(player.getUniqueId(), taskId);
                player.sendMessage(Component.text(
                                "Escribe la descripción para '", NamedTextColor.YELLOW)
                        .append(Component.text(taskId, NamedTextColor.WHITE))
                        .append(Component.text("' en el chat:", NamedTextColor.YELLOW)));
                player.sendMessage(Component.text(
                        "Usa \\n para saltos de línea. Ej: &7Primera línea\\n&aSeg. línea",
                        NamedTextColor.GRAY));
                player.sendMessage(Component.text(
                        "'cancelar' para cancelar. 'borrar' para quitar la descripción.",
                        NamedTextColor.GRAY));
            }

            case SLOT_RESET -> {
                if (!task.hasCustomIcon()) {
                    player.sendMessage(Component.text(
                            "Esta tarea no tiene icono personalizado.", NamedTextColor.GRAY));
                    return;
                }
                task.setIcon(null);
                config.saveTask(task);
                player.sendMessage(Component.text("✔ Icono reseteado al default.", NamedTextColor.GREEN));
                plugin.getServer().getScheduler().runTask(plugin, () -> open(player, task));
            }
        }
    }

    // ── Chat prompts ──────────────────────────────────────────────────────────

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        // Título
        if (awaitingTitle.containsKey(uid)) {
            event.setCancelled(true);
            String taskId = awaitingTitle.remove(uid);
            String input  = event.getMessage().trim();

            if (input.equalsIgnoreCase("cancelar")) {
                player.sendMessage(Component.text("Edición cancelada.", NamedTextColor.GRAY));
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                BingoTask task = config.loadTasks().stream()
                        .filter(t -> t.getId().equals(taskId)).findFirst().orElse(null);
                if (task == null) { player.sendMessage(Component.text("Tarea no encontrada.", NamedTextColor.RED)); return; }
                task.setDisplayName(input);
                config.saveTask(task);
                player.sendMessage(Component.text("✔ Título cambiado a: ", NamedTextColor.GREEN)
                        .append(LegacyComponentSerializer.legacyAmpersand().deserialize(input)));
                open(player, task);
            });
            return;
        }

        // Descripción
        if (awaitingDesc.containsKey(uid)) {
            event.setCancelled(true);
            String taskId = awaitingDesc.remove(uid);
            String input  = event.getMessage().trim();

            if (input.equalsIgnoreCase("cancelar")) {
                player.sendMessage(Component.text("Edición cancelada.", NamedTextColor.GRAY));
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                BingoTask task = config.loadTasks().stream()
                        .filter(t -> t.getId().equals(taskId)).findFirst().orElse(null);
                if (task == null) { player.sendMessage(Component.text("Tarea no encontrada.", NamedTextColor.RED)); return; }

                if (input.equalsIgnoreCase("borrar")) {
                    task.setDescription("");
                    config.saveTask(task);
                    player.sendMessage(Component.text("✔ Descripción eliminada.", NamedTextColor.GREEN));
                } else {
                    // Reemplazar \n literal por el separador interno
                    // El jugador escribe "línea1\nlínea2", se guarda con \n real
                    String processed = input.replace("\\n", "\n");
                    task.setDescription(processed);
                    config.saveTask(task);
                    player.sendMessage(Component.text("✔ Descripción guardada.", NamedTextColor.GREEN));
                }
                open(player, task);
            });
        }
    }

    // ── Construcción de slots ─────────────────────────────────────────────────

    private ItemStack buildIconSlot(BingoTask task) {
        Material mat  = task.hasCustomIcon() ? task.getIcon() : Material.ITEM_FRAME;
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("Cambiar icono", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Icono actual: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(
                        task.hasCustomIcon()
                                ? task.getIcon().name().toLowerCase().replace('_', ' ')
                                : "default (cristal)",
                        NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());
        lore.add(Component.text("Click con un ítem en mano", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("para usarlo como icono.", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildTitleSlot(BingoTask task) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("Cambiar título", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Título actual: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(task.getDisplayName())
                        .decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());
        lore.add(Component.text("Click para escribir en el chat.", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Soporta &a, &c, &l, etc.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildDescSlot(BingoTask task) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("Cambiar descripción", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (task.hasDescription()) {
            // Mostrar cada línea separada
            for (String line : task.getDescription().split("\n")) {
                lore.add(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(line)
                        .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore.add(Component.text("Sin descripción.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click para escribir en el chat.", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Usa \\n para saltos de línea.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Usa &a, &c, etc. para colores.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("'borrar' para eliminarla.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildResetSlot(BingoTask task) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("Quitar icono personalizado", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (task.hasCustomIcon()) {
            lore.add(Component.text("Click para volver al cristal", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("de color por defecto.", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("No hay icono personalizado.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildInfoSlot(BingoTask task) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("Información", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("ID: " + task.getId(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Tipo: " + task.getType().name(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Descripción: " + task.getShortDescription(),
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorders(Inventory inv) {
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta   = border.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        border.setItemMeta(meta);
        for (int i = 0; i < 36; i++) inv.setItem(i, border);
        inv.setItem(SLOT_ICON,  null);
        inv.setItem(SLOT_TITLE, null);
        inv.setItem(SLOT_DESC,  null);
        inv.setItem(SLOT_RESET, null);
        inv.setItem(SLOT_INFO,  null);
    }
}