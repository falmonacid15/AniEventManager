package org.falmdev.anieventmanager.utils.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Utilidades de GUI compartidas por todos los inventarios del plugin.
 *
 * Convenciones de layout (54 slots = 6 filas):
 *
 *   Fila 0  (slots  0- 8) → título / pestañas
 *   Filas 1-4 (slots 9-44) → contenido
 *   Fila 5  (slots 45-53) → navegación permanente
 *
 * Slots de navegación estándar (fila 5):
 *   48 → ← Volver (flecha)       si hay pantalla anterior
 *   49 → contenido opcional del caller
 *   50 → ⌂ Inicio (brújula)      si hay pantalla raíz
 */
public final class GuiUtil {

    /** Material por default del borde. */
    public static final Material DEFAULT_BORDER_MATERIAL = Material.BLACK_STAINED_GLASS_PANE;

    // ── Slots de navegación ───────────────────────────────────────────────────

    /** Slot del botón "Volver" en la fila de navegación. */
    public static final int NAV_BACK = 48;
    /** Slot del botón "Inicio" en la fila de navegación. */
    public static final int NAV_HOME = 50;

    private GuiUtil() {}

    // ── Border pane ───────────────────────────────────────────────────────────

    public static ItemStack emptyPane(Material material) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack emptyPane() {
        return emptyPane(DEFAULT_BORDER_MATERIAL);
    }

    // ── Fill ──────────────────────────────────────────────────────────────────

    public static void fillBorders(Inventory inv, ItemStack borderItem) {
        if (inv == null || borderItem == null) return;
        int size = inv.getSize();
        if (size <= 9) {
            for (int i = 0; i < size; i++) inv.setItem(i, borderItem);
            return;
        }
        int rows = size / 9;
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, borderItem);
            inv.setItem(size - 9 + i, borderItem);
        }
        for (int row = 1; row < rows - 1; row++) {
            inv.setItem(row * 9,     borderItem);
            inv.setItem(row * 9 + 8, borderItem);
        }
    }

    public static void fillBorders(Inventory inv, Material material) { fillBorders(inv, emptyPane(material)); }
    public static void fillBorders(Inventory inv)                    { fillBorders(inv, emptyPane()); }

    public static void fillAll(Inventory inv, ItemStack borderItem) {
        if (inv == null || borderItem == null) return;
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, borderItem);
    }

    public static void fillAll(Inventory inv, Material material) { fillAll(inv, emptyPane(material)); }
    public static void fillAll(Inventory inv)                    { fillAll(inv, emptyPane()); }

    public static void fillEmpty(Inventory inv, ItemStack borderItem) {
        if (inv == null || borderItem == null) return;
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, borderItem);
        }
    }

    public static void fillEmpty(Inventory inv, Material material) { fillEmpty(inv, emptyPane(material)); }
    public static void fillEmpty(Inventory inv)                    { fillEmpty(inv, emptyPane()); }

    // ── Navegación estándar ───────────────────────────────────────────────────

    /**
     * Coloca los botones de navegación en la fila 5 del inventario (54 slots).
     *
     * Slot 48 → "← Volver"   (ARROW)   solo si {@code hasBack == true}
     * Slot 50 → "⌂ Inicio"   (COMPASS) solo si {@code hasHome == true}
     *
     * Los slots vacíos de la fila 5 se rellenan con paneles negros automáticamente
     * (excepto el slot 49 que queda libre para uso del caller).
     *
     * @param inv     Inventario de 54 slots.
     * @param hasBack true si hay una pantalla anterior a la que volver.
     * @param hasHome true si hay una pantalla raíz (hub principal).
     */
    public static void fillNavigation(Inventory inv, boolean hasBack, boolean hasHome) {
        if (inv == null || inv.getSize() < 54) return;

        // Rellenar fila 5 con paneles (excepto slot 49 — libre para el caller)
        for (int i = 45; i <= 53; i++) {
            if (i != 49) inv.setItem(i, emptyPane());
        }

        if (hasBack) {
            inv.setItem(NAV_BACK, ItemBuilder.of(Material.ARROW)
                    .name("← Volver", NamedTextColor.GRAY)
                    .lore(NamedTextColor.DARK_GRAY, "Regresa a la pantalla anterior.")
                    .build());
        }

        if (hasHome) {
            inv.setItem(NAV_HOME, ItemBuilder.of(Material.COMPASS)
                    .name("⌂ Menú Principal", NamedTextColor.GRAY)
                    .lore(NamedTextColor.DARK_GRAY, "Vuelve al panel del EventManager.")
                    .build());
        }
    }

    /**
     * Sobrecarga conveniente: hasBack y hasHome ambos true.
     * La mayoría de las sub-vistas usan esta.
     */
    public static void fillNavigation(Inventory inv) {
        fillNavigation(inv, true, true);
    }

    /**
     * Solo botón de Inicio (sin Volver) — para pantallas raíz de cada minijuego.
     */
    public static void fillNavigationHomeOnly(Inventory inv) {
        fillNavigation(inv, false, true);
    }

    /**
     * Sin navegación — para el hub principal (no hay adónde "volver" ni "ir al inicio").
     * Solo rellena la fila 5 con paneles.
     */
    public static void fillNavigationNone(Inventory inv) {
        if (inv == null || inv.getSize() < 54) return;
        for (int i = 45; i <= 53; i++) inv.setItem(i, emptyPane());
    }

    /**
     * Comprueba si el slot clickeado es NAV_BACK o NAV_HOME.
     * Útil en los onClick para despachar la navegación en una sola línea.
     *
     * <pre>
     *   if (GuiUtil.handleNavigation(slot, player, plugin, () -> render(player, tab))) return;
     * </pre>
     *
     * @param slot        Slot clickeado.
     * @param player      Jugador que clickeó.
     * @param plugin      Instancia del plugin (para acceder al EventManagerGUI).
     * @param onBack      Runnable a ejecutar si el slot es NAV_BACK (puede ser null).
     * @return true si el slot era un botón de navegación (el caller debe hacer return).
     */
    public static boolean handleNavigation(int slot, org.bukkit.entity.Player player,
                                           org.falmdev.anieventmanager.Anieventmanager plugin,
                                           Runnable onBack) {
        if (slot == NAV_HOME) {
            plugin.getEventManagerGUI().open(player);
            return true;
        }
        if (slot == NAV_BACK && onBack != null) {
            onBack.run();
            return true;
        }
        return false;
    }

    // ── Título de inventario ──────────────────────────────────────────────────

    public static String getTitle(InventoryView view) {
        return PlainTextComponentSerializer.plainText().serialize(view.title());
    }

    // ── Botones rápidos ──────────────────────────────────────────────────────

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

    public static Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    public static Component label(String label, Component value) {
        return Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(value)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static Component label(String label, String value, NamedTextColor valueColor) {
        return label(label, Component.text(value, valueColor));
    }
}