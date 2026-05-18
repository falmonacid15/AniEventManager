package org.falmdev.anieventmanager.utils;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;

public final class TeamColorUtil {

    private TeamColorUtil() {}

    public static DyeColor toDyeColor(NamedTextColor color) {
        if (color == NamedTextColor.RED)          return DyeColor.RED;
        if (color == NamedTextColor.BLUE)         return DyeColor.BLUE;
        if (color == NamedTextColor.GREEN)        return DyeColor.GREEN;
        if (color == NamedTextColor.YELLOW)       return DyeColor.YELLOW;
        if (color == NamedTextColor.LIGHT_PURPLE) return DyeColor.PINK;
        if (color == NamedTextColor.AQUA)         return DyeColor.CYAN;
        if (color == NamedTextColor.GOLD)         return DyeColor.ORANGE;
        if (color == NamedTextColor.WHITE)        return DyeColor.WHITE;
        return DyeColor.WHITE;
    }

    public static Color toArmorColor(NamedTextColor color) {
        if (color == NamedTextColor.RED)          return Color.fromRGB(255, 0,   0);
        if (color == NamedTextColor.BLUE)         return Color.fromRGB(0,   0,   255);
        if (color == NamedTextColor.GREEN)        return Color.fromRGB(0,   128, 0);
        if (color == NamedTextColor.YELLOW)       return Color.fromRGB(255, 255, 0);
        if (color == NamedTextColor.LIGHT_PURPLE) return Color.fromRGB(255, 85,  255);
        if (color == NamedTextColor.AQUA)         return Color.fromRGB(0,   255, 255);
        if (color == NamedTextColor.GOLD)         return Color.fromRGB(255, 170, 0);
        if (color == NamedTextColor.WHITE)        return Color.fromRGB(255, 255, 255);
        return Color.WHITE;
    }
}