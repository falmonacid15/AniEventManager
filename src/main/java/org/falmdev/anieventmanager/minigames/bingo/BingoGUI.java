package org.falmdev.anieventmanager.minigames.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GUI de inventario que muestra la tarjeta de bingo del equipo.
 *
 * Layout (54 slots = 6 filas x 9 columnas):
 * Nota: la info del equipo va en la fila del fondo centrada.
 */
public class BingoGUI implements Listener {

    private static final int[] GRID_SLOTS = {
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42,
            47, 48, 49, 50, 51
    };

    // Info del equipo centrada en la fila del fondo
    private static final int INFO_SLOT = 49;

    // ── Abrir GUI ─────────────────────────────────────────────────────────────

    public static void open(Player player, BingoCard card, BingoConfig config) {
        Inventory inv = Bukkit.createInventory(new BingoHolder(), 54,
                Component.text("✦ Tarjeta de Bingo", NamedTextColor.GOLD));

        fillBorders(inv);

        // Cruzar progreso en memoria con metadatos del YAML
        Map<String, BingoTask> configTasks = config.loadTasks().stream()
                .collect(Collectors.toMap(BingoTask::getId, t -> t));

        List<BingoTask> cardTasks = card.getTasks();
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (i < cardTasks.size()) {
                BingoTask inMemory = cardTasks.get(i);
                BingoTask fromConfig = configTasks.get(inMemory.getId());

                String displayName = fromConfig != null
                        ? fromConfig.getDisplayName()
                        : inMemory.getDisplayName();
                Material icon = fromConfig != null && fromConfig.hasCustomIcon()
                        ? fromConfig.getIcon()
                        : null;

                inv.setItem(GRID_SLOTS[i], buildTaskItem(inMemory, displayName, icon));
            } else {
                // Slot sin tarea — rellenar con cristal gris
                inv.setItem(GRID_SLOTS[i], buildEmptySlot());
            }
        }

        inv.setItem(INFO_SLOT, buildInfoItem(card));
        player.openInventory(inv);
    }

    // ── Construcción de ítems ─────────────────────────────────────────────────

    private static ItemStack buildTaskItem(BingoTask task, String displayName, Material icon) {
        Material mat = icon != null
                ? (task.isCompleted() ? Material.LIME_STAINED_GLASS_PANE : icon)
                : (task.isCompleted()
                   ? Material.LIME_STAINED_GLASS_PANE
                   : Material.RED_STAINED_GLASS_PANE);

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();

        // Parsear color codes (&c, &a, etc.) en el display name
        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(displayName)
                .decoration(TextDecoration.ITALIC, false);

        // Si la tarea está completada y no tiene color propio, forzar verde
        if (task.isCompleted() && !displayName.contains("&")) {
            nameComponent = Component.text(displayName, NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false);
        }

        meta.displayName(nameComponent);

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        // Estado
        if (task.isCompleted()) {
            lore.add(Component.text("✔ Completada", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("✘ Pendiente", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }

        // Progreso para tareas con cantidad
        if (task.getType() == BingoTask.Type.OBTAIN_ITEM
                || task.getType() == BingoTask.Type.CRAFT_ITEM
                || task.getType() == BingoTask.Type.KILL_MOB) {
            lore.add(Component.empty());
            lore.add(Component.text("Progreso: ", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(task.getProgress() + "/" + task.getRequired(),
                                    task.isCompleted() ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)));

            // Barra de progreso visual
            lore.add(buildProgressBar(task.getProgress(), task.getRequired()));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Tipo: " + prettyType(task.getType()), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        hideItemDetails(meta);
        item.setItemMeta(meta);
        return item;
    }



    /**
     * Genera una barra de progreso visual con caracteres Unicode.
     * Ejemplo: ██████░░░░ 6/10
     */
    private static Component buildProgressBar(int current, int required) {
        int bars    = 10;
        int filled  = required > 0 ? Math.min(bars, (current * bars) / required) : bars;
        int empty   = bars - filled;

        Component bar = Component.text("  ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
        for (int i = 0; i < filled; i++)
            bar = bar.append(Component.text("█", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        for (int i = 0; i < empty; i++)
            bar = bar.append(Component.text("░", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));

        return bar;
    }

    private static ItemStack buildInfoItem(BingoCard card) {
        // Usar NETHER_STAR para destacar el item de info
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta  meta = item.getItemMeta();

        meta.displayName(
                Component.text("✦ ", NamedTextColor.GOLD)
                        .append(Component.text(card.getTeam().getDisplayName(), card.getTeam().getColor()))
                        .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        // Barra de progreso del equipo
        int done    = card.getCompletedCount();
        int total   = card.getTotalTasks();
        int percent = card.getCompletionPercent();

        lore.add(Component.text("Completadas: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(done + "/" + total, NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)));

        lore.add(Component.text("Porcentaje: ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(percent + "%",
                                percent == 100 ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false)));

        lore.add(Component.empty());
        lore.add(buildProgressBar(done, total));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildEmptySlot() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static void fillBorders(Inventory inv) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        border.setItemMeta(meta);

        // Fila 0 completa
        for (int i = 0; i < 9; i++) inv.setItem(i, border);

        // Bordes laterales
        for (int row = 1; row <= 5; row++) {
            inv.setItem(row * 9,     border); // col 0
            inv.setItem(row * 9 + 1, border); // ⭐ NUEVO → col 1 (la que te falta)

            inv.setItem(row * 9 + 6, border); // col 6
            inv.setItem(row * 9 + 7, border); // col 7
            inv.setItem(row * 9 + 8, border); // col 8
        }

        // Fila del fondo
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
    }

    // ── Listener — cancelar clicks ─────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof BingoHolder) {
            event.setCancelled(true);
        }
    }



    // ── Utilidades ────────────────────────────────────────────────────────────

    private static String prettyType(BingoTask.Type type) {
        return switch (type) {
            case OBTAIN_ITEM    -> "Obtener ítem";
            case CRAFT_ITEM     -> "Craftear ítem";
            case KILL_MOB       -> "Matar mob";
            case REACH_LOCATION -> "Llegar a lugar";
            case EQUIP_ITEM     -> "Equipar ítem";
            case FISH_ITEM      -> "Pescar ítem";
        };
    }

    private static void hideItemDetails(ItemMeta meta) {
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_DESTROYS,
                ItemFlag.HIDE_PLACED_ON
        );
    }
}