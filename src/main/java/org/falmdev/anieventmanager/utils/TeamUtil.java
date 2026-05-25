package org.falmdev.anieventmanager.utils;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.model.EventTeam;

import java.util.UUID;

/**
 * Utilidades de equipos compartidas entre GUIs y comandos.
 *
 * No duplica {@link TeamColorUtil} — esta clase complementa con utilidades
 * de UI y resolución de jugadores, mientras TeamColorUtil sigue manejando
 * la conversión a DyeColor / armor Color.
 */
public final class TeamUtil {

    private TeamUtil() {}

    /**
     * Convierte un {@link NamedTextColor} de equipo al banner equivalente.
     * Usado en GUIs para mostrar items representativos del color del equipo.
     */
    public static Material colorToBannerMaterial(NamedTextColor color) {
        if (color == NamedTextColor.RED)          return Material.RED_BANNER;
        if (color == NamedTextColor.BLUE)         return Material.BLUE_BANNER;
        if (color == NamedTextColor.GREEN)        return Material.LIME_BANNER;
        if (color == NamedTextColor.YELLOW)       return Material.YELLOW_BANNER;
        if (color == NamedTextColor.LIGHT_PURPLE) return Material.MAGENTA_BANNER;
        if (color == NamedTextColor.AQUA)         return Material.LIGHT_BLUE_BANNER;
        if (color == NamedTextColor.GOLD)         return Material.ORANGE_BANNER;
        return Material.WHITE_BANNER;
    }

    /**
     * Busca un equipo por su displayName exacto.
     * Útil para los listeners de GUIs que recuperan el equipo desde el título.
     */
    public static EventTeam findByDisplayName(Anieventmanager plugin, String displayName) {
        for (EventTeam team : plugin.getTeamManager().getAllTeams()) {
            if (team.getDisplayName().equals(displayName)) return team;
        }
        return null;
    }

    /**
     * Resuelve el nombre de un jugador por UUID, sea online u offline.
     * Si no se puede resolver, devuelve los primeros 6 caracteres del UUID.
     */
    public static String resolveMemberName(UUID uuid) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        if (off.getName() != null) return off.getName();
        return uuid.toString().substring(0, 6);
    }

    /**
     * Convierte el nombre del color a su variante legacy "&X" para PlaceholderAPI.
     */
    public static String namedColorToLegacy(NamedTextColor color) {
        if (color == NamedTextColor.RED)          return "&c";
        if (color == NamedTextColor.BLUE)         return "&9";
        if (color == NamedTextColor.GREEN)        return "&a";
        if (color == NamedTextColor.YELLOW)       return "&e";
        if (color == NamedTextColor.LIGHT_PURPLE) return "&d";
        if (color == NamedTextColor.AQUA)         return "&b";
        if (color == NamedTextColor.GOLD)         return "&6";
        if (color == NamedTextColor.WHITE)        return "&f";
        if (color == NamedTextColor.DARK_RED)     return "&4";
        if (color == NamedTextColor.DARK_BLUE)    return "&1";
        if (color == NamedTextColor.DARK_GREEN)   return "&2";
        if (color == NamedTextColor.DARK_AQUA)    return "&3";
        if (color == NamedTextColor.DARK_PURPLE)  return "&5";
        if (color == NamedTextColor.DARK_GRAY)    return "&8";
        if (color == NamedTextColor.GRAY)         return "&7";
        if (color == NamedTextColor.BLACK)        return "&0";
        return "&f";
    }
}