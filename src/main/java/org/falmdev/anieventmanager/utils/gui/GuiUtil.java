package org.falmdev.anieventmanager.utils.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Utilidades de GUI compartidas por todos los inventarios del plugin.
 *
 * Centralizada para:
 *  - Construir el típico "border pane" (item glass con displayName vacío)
 *  - Rellenar bordes del inventario (todos los slots del perímetro)
 *  - Rellenar slots vacíos sin tocar los custom
 *  - Construir botones simples reutilizables
 *  - Extraer el plain text del título de un inventario
 */
public final class GuiUtil {

    /** Material por default usado cuando se llama fillBorders sin argumentos. */
    public static final Material DEFAULT_BORDER_MATERIAL = Material.BLACK_STAINED_GLASS_PANE;

    private GuiUtil() {}

    // ── Border pane ───────────────────────────────────────────────────────────

    /**
     * Crea un item de borde estándar — material configurable, sin displayName visible.
     */
    public static ItemStack emptyPane(Material material) {
        ItemStack item = new ItemStack(material);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack emptyPane() {
        return emptyPane(DEFAULT_BORDER_MATERIAL);
    }

    // ── Fill borders ──────────────────────────────────────────────────────────

    /**
     * Rellena solo el perímetro del inventario (primera/última fila + columnas 0 y 8).
     * Inventarios pequeños (≤ 9 slots) se llenan completos.
     */
    public static void fillBorders(Inventory inv, ItemStack borderItem) {
        if (inv == null || borderItem == null) return;
        int size = inv.getSize();
        if (size <= 9) {
            for (int i = 0; i < size; i++) inv.setItem(i, borderItem);
            return;
        }

        int rows = size / 9;
        // Fila superior y inferior
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, borderItem);
            inv.setItem(size - 9 + i, borderItem);
        }
        // Columnas laterales (excluyendo esquinas ya seteadas)
        for (int row = 1; row < rows - 1; row++) {
            inv.setItem(row * 9,     borderItem);
            inv.setItem(row * 9 + 8, borderItem);
        }
    }

    public static void fillBorders(Inventory inv, Material material) {
        fillBorders(inv, emptyPane(material));
    }

    public static void fillBorders(Inventory inv) {
        fillBorders(inv, emptyPane());
    }

    // ── Fill all (perímetro + filas internas) ────────────────────────────────

    /**
     * Rellena TODOS los slots del inventario con el item indicado.
     * Útil cuando después se sobrescriben slots específicos con items custom.
     */
    public static void fillAll(Inventory inv, ItemStack borderItem) {
        if (inv == null || borderItem == null) return;
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, borderItem);
    }

    public static void fillAll(Inventory inv, Material material) {
        fillAll(inv, emptyPane(material));
    }

    public static void fillAll(Inventory inv) {
        fillAll(inv, emptyPane());
    }

    // ── Fill empty (sin tocar los custom) ────────────────────────────────────

    /**
     * Rellena solo los slots {@code null} del inventario, dejando intactos
     * los items ya seteados. Útil al final del rendering para "limpiar" huecos.
     */
    public static void fillEmpty(Inventory inv, ItemStack borderItem) {
        if (inv == null || borderItem == null) return;
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, borderItem);
        }
    }

    public static void fillEmpty(Inventory inv, Material material) {
        fillEmpty(inv, emptyPane(material));
    }

    public static void fillEmpty(Inventory inv) {
        fillEmpty(inv, emptyPane());
    }

    // ── Título de inventario ──────────────────────────────────────────────────

    /** Extrae el texto plano del título de un InventoryView. */
    public static String getTitle(InventoryView view) {
        return PlainTextComponentSerializer.plainText().serialize(view.title());
    }

    // ── Botones rápidos ──────────────────────────────────────────────────────

    /**
     * Construye un botón simple con título en negrita y varias líneas de lore en gris.
     * Equivalente a {@code ItemBuilder.of(mat).name(text, color, BOLD).emptyLine().lore(lore).build()}.
     */
    public static ItemStack simpleButton(Material material, String text, NamedTextColor color,
                                         String... loreLines) {
        ItemBuilder b = ItemBuilder.of(material)
                .name(text, color, TextDecoration.BOLD);
        if (loreLines.length > 0) {
            b.emptyLine();
            b.lore(loreLines);
        }
        return b.build();
    }

    // ── Conveniencias de texto ───────────────────────────────────────────────

    /**
     * Componente con itálico desactivado — el patrón que casi todos los items
     * usan en displayName y lore.
     */
    public static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Línea típica "  Label: value" usada en lores de info.
     */
    public static Component label(String label, Component value) {
        return Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(value)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static Component label(String label, String value, NamedTextColor valueColor) {
        return label(label, Component.text(value, valueColor));
    }
}