package org.falmdev.anieventmanager.minigames.parkourduos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

public enum HotbarAbility {

    RETURN_CHECKPOINT(
            3,
            Material.COMPASS,
            "Volver al Checkpoint",
            NamedTextColor.AQUA,
            "Regresa a tu equipo al último checkpoint completado.",
            "Requiere confirmación del compañero.",
            30
    ),

    JUMP_BOOST(
            4,
            Material.FEATHER,
            "Super Salto",
            NamedTextColor.YELLOW,
            "Aplica super salto a ambos jugadores.",
            "Requiere confirmación del compañero.",
            45
    ),

    TELEPORT_TO_TEAMMATE(
            5,
            Material.ENDER_PEARL,
            "Ir al Compañero",
            NamedTextColor.LIGHT_PURPLE,
            "Te teletransporta a la posición de tu compañero.",
            "No requiere confirmación.",
            20
    );

    private final int slot;
    private final Material material;
    private final String displayName;
    private final NamedTextColor color;
    private final String descLine1;
    private final String descLine2;
    private final int cooldownSeconds;

    HotbarAbility(int slot, Material material, String displayName, NamedTextColor color,
                  String descLine1, String descLine2, int cooldownSeconds) {
        this.slot            = slot;
        this.material        = material;
        this.displayName     = displayName;
        this.color           = color;
        this.descLine1       = descLine1;
        this.descLine2       = descLine2;
        this.cooldownSeconds = cooldownSeconds;
    }

    public int getSlot()             { return slot; }
    public Material getMaterial()    { return material; }
    public String getDisplayName()   { return displayName; }
    public NamedTextColor getColor() { return color; }
    public int getCooldownSeconds()  { return cooldownSeconds; }

    public ItemStack buildItem() {
        return ItemBuilder.of(material)
                .name(displayName, color, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.GRAY, descLine1)
                .lore(NamedTextColor.DARK_GRAY, descLine2)
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click derecho para usar.")
                .lore(NamedTextColor.GRAY, "Cooldown: " + cooldownSeconds + "s")
                .build();
    }

    public ItemStack buildCooldownItem(int secondsLeft) {
        return ItemBuilder.of(material)
                .name("⏱ " + displayName + " (" + secondsLeft + "s)", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                .emptyLine()
                .lore(NamedTextColor.RED, "En cooldown — " + secondsLeft + "s restantes.")
                .build();
    }

    public static HotbarAbility fromItem(ItemStack item) {
        if (item == null) return null;
        for (HotbarAbility ability : values()) {
            if (item.getType() == ability.material && item.hasItemMeta()) {
                String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(item.getItemMeta().displayName());
                if (name.contains(ability.displayName)) return ability;
            }
        }
        return null;
    }
}