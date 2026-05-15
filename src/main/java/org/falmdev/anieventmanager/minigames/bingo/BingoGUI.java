package org.falmdev.anieventmanager.minigames.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GUI de inventario que muestra la tarjeta de bingo del equipo.
 *
 * Combina:
 *  - Progreso en memoria (BingoCard) — completado/pendiente/contador
 *  - Metadatos del YAML (BingoConfig) — displayName e icono actualizados
 *
 * Layout del inventario (54 slots = 6 filas x 9 columnas):
 *
 *   Fila 0: [borde] x9
 *   Fila 1: [borde] [T01] [T02] [T03] [T04] [T05] [borde] [info] [borde]
 *   Fila 2: [borde] [T06] [T07] [T08] [T09] [T10] [borde] [    ] [borde]
 *   Fila 3: [borde] [T11] [T12] [T13] [T14] [T15] [borde] [    ] [borde]
 *   Fila 4: [borde] [T16] [T17] [T18] [T19] [T20] [borde] [    ] [borde]
 *   Fila 5: [borde] [T21] [T22] [T23] [T24] [T25] [borde] [borde][borde]
 */
public class BingoGUI implements Listener {

    private static final int[] GRID_SLOTS = {
            10, 11, 12, 13, 14,
            19, 20, 21, 22, 23,
            28, 29, 30, 31, 32,
            37, 38, 39, 40, 41,
            46, 47, 48, 49, 50
    };

    private static final int INFO_SLOT = 16;

    /**
     * Abre el GUI combinando progreso en memoria con metadatos del YAML.
     *
     * @param player  jugador al que se muestra
     * @param card    tarjeta en memoria (tiene el estado de progreso)
     * @param config  config del bingo (tiene displayName e icono actualizados)
     */
    public static void open(Player player, BingoCard card, BingoConfig config) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Bingo — ", NamedTextColor.GOLD)
                        .append(Component.text(card.getTeam().getDisplayName(),
                                card.getTeam().getColor())));

        fillBorders(inv);

        // Cargar metadatos actualizados del YAML y cruzarlos con el progreso en memoria
        Map<String, BingoTask> configTasks = config.loadTasks().stream()
                .collect(Collectors.toMap(BingoTask::getId, t -> t));

        List<BingoTask> cardTasks = card.getTasks();
        for (int i = 0; i < GRID_SLOTS.length && i < cardTasks.size(); i++) {
            BingoTask inMemory = cardTasks.get(i);

            // Tomar displayName e icono del YAML (pueden haber sido editados)
            BingoTask fromConfig = configTasks.get(inMemory.getId());
            String displayName = fromConfig != null
                    ? fromConfig.getDisplayName()
                    : inMemory.getDisplayName();
            Material icon = fromConfig != null && fromConfig.hasCustomIcon()
                    ? fromConfig.getIcon()
                    : null;

            inv.setItem(GRID_SLOTS[i], buildTaskItem(inMemory, displayName, icon));
        }

        inv.setItem(INFO_SLOT, buildInfoItem(card));
        player.openInventory(inv);
    }

    /**
     * Sobrecarga sin config — usa los datos en memoria tal cual.
     * Se usa cuando no hay partida activa o en contextos sin acceso al config.
     */
    public static void open(Player player, BingoCard card) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Bingo — ", NamedTextColor.GOLD)
                        .append(Component.text(card.getTeam().getDisplayName(),
                                card.getTeam().getColor())));

        fillBorders(inv);

        List<BingoTask> tasks = card.getTasks();
        for (int i = 0; i < GRID_SLOTS.length && i < tasks.size(); i++) {
            BingoTask t = tasks.get(i);
            inv.setItem(GRID_SLOTS[i],
                    buildTaskItem(t, t.getDisplayName(), t.hasCustomIcon() ? t.getIcon() : null));
        }

        inv.setItem(INFO_SLOT, buildInfoItem(card));
        player.openInventory(inv);
    }

    // ── Construcción de ítems ─────────────────────────────────────────────────

    private static ItemStack buildTaskItem(BingoTask task, String displayName, Material icon) {
        // Material: icono personalizado si existe, si no cristal según estado
        Material mat = icon != null
                ? icon
                : (task.isCompleted()
                   ? Material.LIME_STAINED_GLASS_PANE
                   : Material.RED_STAINED_GLASS_PANE);

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();

        // Nombre — displayName actualizado del YAML
        NamedTextColor nameColor = task.isCompleted() ? NamedTextColor.GREEN : NamedTextColor.WHITE;
        meta.displayName(Component.text(displayName, nameColor)
                .decoration(TextDecoration.ITALIC, false));

        // Lore — progreso en memoria
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (task.isCompleted()) {
            lore.add(Component.text("✔ Completada", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            if (task.getType() == BingoTask.Type.OBTAIN_ITEM
                    || task.getType() == BingoTask.Type.CRAFT_ITEM
                    || task.getType() == BingoTask.Type.KILL_MOB) {
                lore.add(Component.text("Progreso: " + task.getProgress()
                                + "/" + task.getRequired(), NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("✘ Pendiente", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        lore.add(Component.empty());
        lore.add(Component.text("Tipo: " + prettyType(task.getType()), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildInfoItem(BingoCard card) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta  meta = item.getItemMeta();

        meta.displayName(Component.text("Progreso del equipo", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Equipo: " + card.getTeam().getDisplayName(),
                card.getTeam().getColor()).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Completadas: " + card.getCompletedCount()
                        + "/" + card.getTotalTasks(), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Porcentaje: " + card.getCompletionPercent()
                        + "%", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());


        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static void fillBorders(Inventory inv) {
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta   = border.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        border.setItemMeta(meta);

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
            inv.setItem(45 + i, border);
        }
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9,     border);
            inv.setItem(row * 9 + 6, border);
        }
        for (int row = 0; row < 6; row++) {
            inv.setItem(row * 9 + 8, border);
            inv.setItem(row * 9 + 7, border);
        }
    }

    // ── Listener — cancelar clicks ────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String titlePlain = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (titlePlain.startsWith("Bingo — ")) {
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
}