package org.falmdev.anieventmanager.lobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.*;

/**
 * GUI reusable de confirmación.
 *
 * Layout (27 slots):
 *   Fila 0: borders rojos
 *   Fila 1: border | CONFIRM(11) | border | INFO(13) | border | CANCEL(15) | border
 *   Fila 2: borders rojos
 */
public class ConfirmGUI implements Listener {

    public static final String TITLE_PREFIX = "Confirmar: ";

    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_INFO    = 13;
    private static final int SLOT_CANCEL  = 15;

    private final Anieventmanager plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public ConfirmGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, String title, List<String> description, Runnable onConfirm) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(TITLE_PREFIX, NamedTextColor.RED)
                        .append(Component.text(title, NamedTextColor.YELLOW)));

        GuiUtil.fillAll(inv, Material.RED_STAINED_GLASS_PANE);

        inv.setItem(SLOT_CONFIRM, ItemBuilder.of(Material.LIME_CONCRETE)
                .name("✔ Sí, confirmar", NamedTextColor.GREEN, TextDecoration.BOLD)
                .emptyLine()
                .lore("Click para ejecutar la acción.")
                .build());

        // Info central con título grande + descripción
        ItemBuilder info = ItemBuilder.of(Material.PAPER)
                .name(title, NamedTextColor.YELLOW, TextDecoration.BOLD)
                .emptyLine()
                .lore(description.toArray(new String[0]))
                .emptyLine()
                .lore(NamedTextColor.DARK_RED, "Esta acción no se puede deshacer.");
        inv.setItem(SLOT_INFO, info.build());

        inv.setItem(SLOT_CANCEL, ItemBuilder.of(Material.RED_CONCRETE)
                .name("✘ Cancelar", NamedTextColor.RED, TextDecoration.BOLD)
                .emptyLine()
                .lore("Click para volver sin cambios.")
                .build());

        pending.put(viewer.getUniqueId(), new Pending(title, onConfirm));
        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = GuiUtil.getTitle(event.getView());
        if (!title.startsWith(TITLE_PREFIX)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player viewer)) return;

        Pending p = pending.get(viewer.getUniqueId());
        if (p == null) { viewer.closeInventory(); return; }

        int slot = event.getRawSlot();

        if (slot == SLOT_CONFIRM) {
            pending.remove(viewer.getUniqueId());
            viewer.closeInventory();
            Bukkit.getScheduler().runTask(plugin, p.onConfirm);
        } else if (slot == SLOT_CANCEL) {
            pending.remove(viewer.getUniqueId());
            viewer.closeInventory();
            viewer.sendMessage(Component.text("Acción cancelada.", NamedTextColor.GRAY));
        }
    }

    private record Pending(String title, Runnable onConfirm) {}
}