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
 * Layout (27 slots = 3 filas x 9 columnas):
 *
 *   [border] [border] [border] [border] [border] [border] [border] [border] [border]
 *   [border] [ICON]   [border] [TITLE]  [border] [RESET]  [border] [INFO]   [border]
 *   [border] [border] [border] [border] [border] [border] [border] [border] [border]
 *
 *   Slot 10 — ICON:  click para establecer el item en mano como icono
 *   Slot 13 — TITLE: click para cambiar el título (prompt en chat)
 *   Slot 15 — RESET: click para quitar el icono personalizado
 *   Slot 16 — INFO:  información de la tarea (no clickeable)
 */
public class BingoEditGUI implements Listener {

    private static final int SLOT_ICON   = 10;
    private static final int SLOT_TITLE  = 12;
    private static final int SLOT_DESC   = 14;
    private static final int SLOT_RESET  = 16;
    private static final int SLOT_INFO   = 28;

    private final Anieventmanager plugin;
    private final BingoConfig config;

    // Jugadores esperando input de chat para cambiar título
    // UUID -> taskId
    private final Map<UUID, String> awaitingTitle = new HashMap<>();
    private final Map<UUID, String> awaitingDesc = new HashMap<>();

    public BingoEditGUI(Anieventmanager plugin) {
        this.plugin = plugin;
        this.config  = plugin.getBingoMiniGame().getConfig();
    }

    // ── Abrir el editor ───────────────────────────────────────────────────────

    public void open(Player player, BingoTask task) {
        // El título usa el prefijo plain "Editar tarea: " para poder extraer el id fácilmente
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

        // Extraer el título como plain text para evitar problemas con códigos de color
        String titlePlain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());

        if (!titlePlain.startsWith("Editar tarea: ")) return;

        event.setCancelled(true);

        String taskId = titlePlain.replace("Editar tarea: ", "").trim();
        int slot = event.getRawSlot();

        // Recargar la tarea desde config para tener el estado más reciente
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
                // Tomar el item que el jugador tiene en mano
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    player.sendMessage(Component.text(
                            "Debes tener un ítem en la mano para usarlo como icono.",
                            NamedTextColor.RED));
                    return;
                }
                task.setIcon(hand.getType());
                config.saveTask(task);
                player.sendMessage(Component.text("✔ Icono de '", NamedTextColor.GREEN)
                        .append(Component.text(taskId, NamedTextColor.YELLOW))
                        .append(Component.text("' cambiado a ", NamedTextColor.GREEN))
                        .append(Component.text(hand.getType().name().toLowerCase()
                                .replace('_', ' '), NamedTextColor.WHITE))
                        .append(Component.text(".", NamedTextColor.GREEN)));
                // Reabrir con los datos actualizados
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> open(player, task));
            }

            case SLOT_TITLE -> {
                // Iniciar prompt de chat
                player.closeInventory();
                awaitingTitle.put(player.getUniqueId(), taskId);
                player.sendMessage(Component.text(
                                "Escribe el nuevo título para la tarea '", NamedTextColor.YELLOW)
                        .append(Component.text(taskId, NamedTextColor.WHITE))
                        .append(Component.text("' en el chat:", NamedTextColor.YELLOW)));
                player.sendMessage(Component.text(
                        "Escribe 'cancelar' para cancelar.", NamedTextColor.GRAY));
            }

            case SLOT_DESC -> {
                player.closeInventory();
                awaitingDesc.put(player.getUniqueId(), taskId);
                player.sendMessage(Component.text(
                                "Escribe la nueva descripción para la tarea '", NamedTextColor.YELLOW)
                        .append(Component.text(taskId, NamedTextColor.WHITE))
                        .append(Component.text("' en el chat:", NamedTextColor.YELLOW)));
                player.sendMessage(Component.text(
                        "Escribe 'cancelar' para cancelar.", NamedTextColor.GRAY));
                player.sendMessage(Component.text(
                        "Escribe 'borrar' para quitar la descripción.", NamedTextColor.GRAY));
            }

            case SLOT_RESET -> {
                if (!task.hasCustomIcon()) {
                    player.sendMessage(Component.text(
                            "Esta tarea no tiene icono personalizado.", NamedTextColor.GRAY));
                    return;
                }
                task.setIcon(null);
                config.saveTask(task);
                player.sendMessage(Component.text("✔ Icono de '", NamedTextColor.GREEN)
                        .append(Component.text(taskId, NamedTextColor.YELLOW))
                        .append(Component.text("' reseteado al default.", NamedTextColor.GREEN)));
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> open(player, task));
            }
        }
    }

    // ── Chat prompt para el título ─────────────────────────────────────────────


    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        // Manejo de título (existente)
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
                        .append(Component.text(input, NamedTextColor.WHITE)));
                open(player, task);
            });
            return;
        }

        // Manejo de descripción (NUEVO)
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
                    task.setDescription(input);
                    config.saveTask(task);
                    player.sendMessage(Component.text("✔ Descripción cambiada a: ", NamedTextColor.GREEN)
                            .append(Component.text(input, NamedTextColor.WHITE)));
                }
                open(player, task);
            });
        }
    }

    // ── Construcción de slots ─────────────────────────────────────────────────

    private ItemStack buildIconSlot(BingoTask task) {
        Material mat = task.hasCustomIcon() ? task.getIcon() : Material.ITEM_FRAME;
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
                .append(Component.text(task.getDisplayName(), NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());
        lore.add(Component.text("Click para escribir un", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("nuevo título en el chat.", NamedTextColor.YELLOW)
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

    private ItemStack buildDescSlot(BingoTask task) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("Cambiar descripción", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (task.hasDescription()) {
            String desc = task.getDescription();
            int chunkSize = 40;
            for (int i = 0; i < desc.length(); i += chunkSize) {
                String chunk = desc.substring(i, Math.min(i + chunkSize, desc.length()));
                lore.add(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(chunk)
                        .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore.add(Component.text("Sin descripción.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click para escribir en el chat.", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Escribe 'borrar' para quitarla.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
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