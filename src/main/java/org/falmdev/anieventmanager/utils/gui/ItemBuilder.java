package org.falmdev.anieventmanager.utils.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builder fluido para construir ItemStacks de forma concisa.
 *
 * Ejemplos:
 *
 *   ItemStack item = ItemBuilder.of(Material.EMERALD)
 *       .name("Confirmar", NamedTextColor.GREEN, TextDecoration.BOLD)
 *       .lore("Click para ejecutar la acción.")
 *       .build();
 *
 *   ItemStack head = ItemBuilder.fromString("head-http://textures...")
 *       .name(team.getDisplayName(), team.getColor(), TextDecoration.BOLD)
 *       .build();
 *
 * Los métodos de nombre y lore aplican {@code ITALIC=false} automáticamente
 * para evitar el itálico que Minecraft pone por default en displayNames.
 */
public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta  meta;
    private final List<Component> loreLines = new ArrayList<>();

    // ── Constructores ─────────────────────────────────────────────────────────

    private ItemBuilder(ItemStack item) {
        this.item = item;
        this.meta = item.getItemMeta();
    }

    /** Builder desde un Material vanilla. */
    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    /** Builder desde un ItemStack existente (lo modifica). */
    public static ItemBuilder of(ItemStack stack) {
        return new ItemBuilder(stack.clone());
    }

    /**
     * Builder desde un string que puede ser material vanilla o referencia a head
     * (ver {@link MaterialUtil#parse(String)}).
     */
    public static ItemBuilder fromString(String raw) {
        return new ItemBuilder(MaterialUtil.parse(raw));
    }

    /** Builder desde la cabeza de un jugador. */
    public static ItemBuilder playerHead(OfflinePlayer player) {
        return new ItemBuilder(HeadUtil.fromPlayer(player));
    }

    // ── Nombre ────────────────────────────────────────────────────────────────

    public ItemBuilder name(Component component) {
        meta.displayName(component.decoration(TextDecoration.ITALIC, false));
        return this;
    }

    public ItemBuilder name(String text, NamedTextColor color, TextDecoration... decorations) {
        Component c = Component.text(text)
                .color(color)
                .decoration(TextDecoration.ITALIC, false);
        for (TextDecoration d : decorations) c = c.decoration(d, true);
        meta.displayName(c);
        return this;
    }

    public ItemBuilder name(String text, NamedTextColor color) {
        return name(text, color, new TextDecoration[0]);
    }

    // ── Lore ──────────────────────────────────────────────────────────────────

    /** Agrega líneas de lore como texto plano (gris, sin itálico). */
    public ItemBuilder lore(String... lines) {
        for (String l : lines) {
            loreLines.add(Component.text(l, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return this;
    }

    /** Agrega líneas de lore con un color específico. */
    public ItemBuilder lore(NamedTextColor color, String... lines) {
        for (String l : lines) {
            loreLines.add(Component.text(l, color)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return this;
    }

    /** Agrega líneas de lore ya construidas como Components. */
    public ItemBuilder lore(Component... lines) {
        for (Component c : lines) {
            loreLines.add(c.decoration(TextDecoration.ITALIC, false));
        }
        return this;
    }

    /** Agrega una lista de Components como lore. */
    public ItemBuilder lore(List<Component> lines) {
        for (Component c : lines) {
            loreLines.add(c.decoration(TextDecoration.ITALIC, false));
        }
        return this;
    }

    /** Agrega una línea en blanco al lore. */
    public ItemBuilder emptyLine() {
        loreLines.add(Component.empty());
        return this;
    }

    // ── Otras propiedades ─────────────────────────────────────────────────────

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder flag(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    /** Oculta atributos, enchants, durabilidad y demás detalles. */
    public ItemBuilder hideDetails() {
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_DESTROYS,
                ItemFlag.HIDE_PLACED_ON
        );
        return this;
    }

    public ItemBuilder unbreakable(boolean value) {
        meta.setUnbreakable(value);
        return this;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    public ItemStack build() {
        if (!loreLines.isEmpty()) meta.lore(new ArrayList<>(loreLines));
        item.setItemMeta(meta);
        return item;
    }
}