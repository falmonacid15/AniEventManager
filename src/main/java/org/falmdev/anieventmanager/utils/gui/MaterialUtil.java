package org.falmdev.anieventmanager.utils.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Parsea strings de material que pueden referirse a:
 *
 *   "DIAMOND_SWORD"                              → vanilla material
 *   "head-PlayerName"                            → cabeza de jugador
 *   "head-base64-<base64 value>"                 → cabeza con texture base64
 *   "head-<url o hash de minecraft-heads.com>"   → cabeza con texture URL
 *
 * Estilo zMenu. Útil para configurar items custom desde YAML.
 */
public final class MaterialUtil {

    private MaterialUtil() {}

    /**
     * Parsea un string a ItemStack. Si no se reconoce el formato,
     * devuelve {@link Material#STONE} como fallback (nunca null).
     */
    public static ItemStack parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ItemStack(Material.STONE);
        }

        String s = raw.trim();

        // ── Cabezas custom ────────────────────────────────────────────────────
        if (s.toLowerCase().startsWith("head-")) {
            String rest = s.substring(5);

            // head-base64-<value>
            if (rest.toLowerCase().startsWith("base64-")) {
                return HeadUtil.fromBase64(rest.substring(7));
            }

            // head-http://... o head-<hash>
            if (rest.startsWith("http") || looksLikeHash(rest)) {
                return HeadUtil.fromTextureUrl(rest);
            }

            // head-PlayerName (fallback)
            return HeadUtil.fromPlayerName(rest);
        }

        // ── Material vanilla ──────────────────────────────────────────────────
        try {
            Material mat = Material.valueOf(s.toUpperCase());
            return new ItemStack(mat);
        } catch (IllegalArgumentException e) {
            return new ItemStack(Material.STONE);
        }
    }

    /** Heurística: los hashes de minecraft-heads.com son hex de 60+ caracteres. */
    private static boolean looksLikeHash(String s) {
        if (s.length() < 32) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }
}