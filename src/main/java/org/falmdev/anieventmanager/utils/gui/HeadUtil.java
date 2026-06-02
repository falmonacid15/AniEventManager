package org.falmdev.anieventmanager.utils.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Base64;
import java.util.UUID;

/**
 * Utilidad para crear cabezas (PLAYER_HEAD) con texturas custom.
 *
 * Soporta:
 *   - Base64 directo
 *   - URL de textures.minecraft.net
 *   - Cabezas de jugador
 *
 * Compatible con Paper 1.20+
 */
public final class HeadUtil {

    private HeadUtil() {}

    /**
     * Construye una cabeza a partir de una URL de textura.
     * Ejemplo:
     *   http://textures.minecraft.net/texture/{hash}
     */
    public static ItemStack fromTextureUrl(String url) {
        if (url == null || url.isBlank()) return new ItemStack(Material.PLAYER_HEAD);

        String fullUrl = url.startsWith("http")
                ? url
                : "http://textures.minecraft.net/texture/" + url;

        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + fullUrl + "\"}}}";
        String base64 = Base64.getEncoder().encodeToString(json.getBytes());

        return fromBase64(base64);
    }

    /**
     * Construye una cabeza a partir de base64 (value de minecraft-heads).
     */
    public static ItemStack fromBase64(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (base64 == null || base64.isBlank()) return head;

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        // UUID determinista para evitar duplicados
        UUID profileUuid = new UUID(base64.hashCode(), base64.hashCode());

        PlayerProfile profile = Bukkit.createProfile(profileUuid, null);
        profile.setProperty(new ProfileProperty("textures", base64));

        // ✅ Forma correcta en Paper moderno (sin reflection)
        meta.setPlayerProfile(profile);

        head.setItemMeta(meta);
        return head;
    }

    /**
     * Construye la cabeza de un jugador (online u offline).
     */
    public static ItemStack fromPlayer(OfflinePlayer player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }

        return head;
    }

    /**
     * Construye la cabeza de un jugador por nombre.
     */
    public static ItemStack fromPlayerName(String name) {
        return fromPlayer(Bukkit.getOfflinePlayer(name));
    }
}