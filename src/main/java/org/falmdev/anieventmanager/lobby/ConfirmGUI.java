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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.falmdev.anieventmanager.Anieventmanager;

import java.util.*;

/**
 * GUI reusable de confirmación.
 *
 * Layout (27 slots):
 *   Fila 0: borders rojos
 *   Fila 1: border | CONFIRM(11) | border | INFO(13) | border | CANCEL(15) | border
 *   Fila 2: borders rojos
 *
 * El callback se ejecuta on confirm. On cancel se descarta sin acción.
 */
public class ConfirmGUI implements Listener {

    public static final String TITLE_PREFIX = "Confirmar: ";

    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_INFO    = 13;
    private static final int SLOT_CANCEL  = 15;

    private final Anieventmanager plugin;

    // viewer → callback pendiente
    private final Map<UUID, Pending> pending = new HashMap<>();

    public ConfirmGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    /**
     * Abre el diálogo de confirmación.
     *
     * @param viewer        quien ve
     * @param title         título corto (aparece después de "Confirmar: ")
     * @param description   lista de líneas de lore en el item central
     * @param onConfirm     callback al confirmar
     */
    public void open(Player viewer, String title, List<String> description, Runnable onConfirm) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(TITLE_PREFIX, NamedTextColor.RED)
                        .append(Component.text(title, NamedTextColor.YELLOW)));

        // Borders rojos
        ItemStack border = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta bMeta = border.getItemMeta();
        bMeta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        border.setItemMeta(bMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        // Botón CONFIRMAR (esmeralda verde)
        inv.setItem(SLOT_CONFIRM, buildButton(Material.LIME_CONCRETE,
                "✔ Sí, confirmar", NamedTextColor.GREEN, TextDecoration.BOLD,
                "Click para ejecutar la acción."));

        // Info central (papel)
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta iMeta = info.getItemMeta();
        iMeta.displayName(Component.text(title, NamedTextColor.YELLOW, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        for (String line : description) {
            lore.add(Component.text(line, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Esta acción no se puede deshacer.", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false));
        iMeta.lore(lore);
        info.setItemMeta(iMeta);
        inv.setItem(SLOT_INFO, info);

        // Botón CANCELAR (concreto rojo)
        inv.setItem(SLOT_CANCEL, buildButton(Material.RED_CONCRETE,
                "✘ Cancelar", NamedTextColor.RED, TextDecoration.BOLD,
                "Click para volver sin cambios."));

        pending.put(viewer.getUniqueId(), new Pending(title, onConfirm));
        viewer.openInventory(inv);
    }

    private ItemStack buildButton(Material mat, String text, NamedTextColor color,
                                  TextDecoration deco, String loreText) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(text, color, deco)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.empty(),
                Component.text(loreText, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
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
        // El click en el item del medio o en borders se ignora (ya cancelado)
    }

    private record Pending(String title, Runnable onConfirm) {}
}