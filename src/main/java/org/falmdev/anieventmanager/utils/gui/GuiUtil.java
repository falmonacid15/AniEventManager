package org.falmdev.anieventmanager.utils.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;


public final class GuiUtil {

    public static final Material DEFAULT_BORDER_MATERIAL = Material.BLACK_STAINED_GLASS_PANE;

    public static final int NAV_BACK = 48;
    public static final int NAV_HOME = 50;

    private GuiUtil() {}

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

    public static void fillSlots(Inventory inv, ItemStack item, int... slots) {
        if (inv == null || item == null) return;
        for (int slot : slots) {
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item);
        }
    }

    public static void fillSlots(Inventory inv, Material material, int... slots) {
        fillSlots(inv, emptyPane(material), slots);
    }

    public static void fillNavigation(Inventory inv, boolean hasBack, boolean hasHome) {
        if (inv == null || inv.getSize() < 54) return;

        for (int i = 45; i <= 53; i++) {
            if (i != 49) inv.setItem(i, emptyPane());
        }

        if (hasBack) {
            inv.setItem(NAV_BACK, ItemBuilder.of(HeadUtil.fromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmIwZjZlOGFmNDZhYzZmYWY4ODkxNDE5MWFiNjZmMjYxZDY3MjZhNzk5OWM2MzdjZjJlNDE1OWZlMWZjNDc3In19fQ=="))
                    .name("Volver", NamedTextColor.GRAY)
                    .lore(NamedTextColor.DARK_GRAY, "Regresa a la pantalla anterior.")
                    .build());
        }

        if (hasHome) {
            inv.setItem(NAV_HOME, ItemBuilder.of(HeadUtil.fromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDA5ODc3OTJjNWFjNDVmMDhlNjkyY2FiZjliOTY2MWYyYzc5ZDBkOGQxNDJmOTk1MWFiMmUwNjQ2YTg1NTgxNiJ9fX0="))
                    .name("Menú Principal", NamedTextColor.GRAY)
                    .lore(NamedTextColor.DARK_GRAY, "Vuelve al panel del EventManager.")
                    .build());
        }
    }

    public static void fillNavigation(Inventory inv) {
        fillNavigation(inv, true, true);
    }

    public static void fillNavigationHomeOnly(Inventory inv) {
        fillNavigation(inv, false, true);
    }

    public static void fillNavigationNone(Inventory inv) {
        if (inv == null || inv.getSize() < 54) return;

        fillSlots(inv, emptyPane(), 45, 46, 52, 53);
    }

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


    public static String getTitle(InventoryView view) {
        return PlainTextComponentSerializer.plainText().serialize(view.title());
    }


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