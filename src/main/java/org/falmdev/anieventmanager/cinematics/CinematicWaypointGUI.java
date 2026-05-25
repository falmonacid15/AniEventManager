package org.falmdev.anieventmanager.cinematics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.falmdev.anieventmanager.Anieventmanager;
import org.falmdev.anieventmanager.cinematics.model.Cinematic;
import org.falmdev.anieventmanager.cinematics.model.CinematicWaypoint;
import org.falmdev.anieventmanager.utils.gui.GuiUtil;
import org.falmdev.anieventmanager.utils.gui.ItemBuilder;

import java.util.*;

/**
 * GUI de edición de texto para un waypoint individual.
 *
 * Layout (27 slots):
 *   Fila 0: borders
 *   Fila 1: INFO(10) | TITLE(11) | SUBTITLE(12) | ACTIONBAR(13) | TIMING(14) | CLEAR(15) | TICK(16)
 *   Fila 2: borders | BACK(22)
 *
 * Todos los textos soportan formato "&" de Minecraft.
 */
public class CinematicWaypointGUI implements Listener {

    public static final String TITLE_PREFIX = "Waypoint #";

    private static final int SLOT_INFO      = 10;
    private static final int SLOT_TITLE     = 11;
    private static final int SLOT_SUBTITLE  = 12;
    private static final int SLOT_ACTIONBAR = 13;
    private static final int SLOT_TIMING    = 14;
    private static final int SLOT_CLEAR     = 15;
    private static final int SLOT_TICK      = 16;
    private static final int SLOT_BACK      = 22;

    private final Anieventmanager plugin;

    // viewer → estado de edición actual
    private final Map<UUID, EditSession> sessions = new HashMap<>();

    private enum EditField { TITLE, SUBTITLE, ACTIONBAR, TICK }

    private record EditSession(Cinematic cinematic, CinematicWaypoint waypoint,
                               EditField field, Runnable onReturn) {}

    // Para prompts de chat: solo necesitamos el campo pendiente
    private final Map<UUID, EditSession> awaitingChat = new HashMap<>();

    public CinematicWaypointGUI(Anieventmanager plugin) {
        this.plugin = plugin;
    }

    // ── Abrir ─────────────────────────────────────────────────────────────────

    /**
     * @param onReturn callback para "← Volver" (puede ser null; si es null vuelve a CinematicAdminGUI)
     */
    public void open(Player player, Cinematic cinematic, CinematicWaypoint waypoint) {
        open(player, cinematic, waypoint, null);
    }

    public void open(Player player, Cinematic cinematic, CinematicWaypoint waypoint, Runnable onReturn) {
        int idx = waypoint.getIndex() + 1; // display 1-based
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(TITLE_PREFIX + idx + " — ", NamedTextColor.GOLD)
                        .append(Component.text(cinematic.getDisplayName(), NamedTextColor.YELLOW)));

        GuiUtil.fillAll(inv);

        inv.setItem(SLOT_INFO,      buildInfoItem(waypoint));
        inv.setItem(SLOT_TITLE,     buildFieldButton(Material.NAME_TAG,
                "Título principal", NamedTextColor.GOLD,
                waypoint.getTitleMain(), "Soporta &a, &c, &l, etc."));
        inv.setItem(SLOT_SUBTITLE,  buildFieldButton(Material.PAPER,
                "Subtítulo", NamedTextColor.YELLOW,
                waypoint.getTitleSub(), "Soporta &a, &c, &l, etc."));
        inv.setItem(SLOT_ACTIONBAR, buildFieldButton(Material.WRITABLE_BOOK,
                "Actionbar", NamedTextColor.AQUA,
                waypoint.getActionbar(), "Texto en la barra de acción."));
        inv.setItem(SLOT_TIMING,    buildTimingItem(waypoint));
        inv.setItem(SLOT_CLEAR,     ItemBuilder.of(Material.BARRIER)
                .name("Limpiar textos", NamedTextColor.RED, TextDecoration.BOLD)
                .emptyLine()
                .lore("Click para borrar título,", "subtítulo y actionbar.")
                .build());
        inv.setItem(SLOT_TICK,      buildTickItem(waypoint));
        inv.setItem(SLOT_BACK,      GuiUtil.simpleButton(Material.ARROW,
                "← Volver", NamedTextColor.GRAY, "Click para regresar."));

        sessions.put(player.getUniqueId(),
                new EditSession(cinematic, waypoint, null, onReturn));
        player.openInventory(inv);
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    private org.bukkit.inventory.ItemStack buildInfoItem(CinematicWaypoint wp) {
        ItemBuilder b = ItemBuilder.of(Material.COMPASS)
                .name("Waypoint #" + (wp.getIndex() + 1), NamedTextColor.WHITE, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Tick",
                        Component.text(wp.getTickOffset(), NamedTextColor.YELLOW)))
                .lore(GuiUtil.label("Posición", Component.text(
                        String.format("%.1f, %.1f, %.1f",
                                wp.getLocation().getX(), wp.getLocation().getY(),
                                wp.getLocation().getZ()), NamedTextColor.WHITE)));

        if (wp.hasText()) {
            b.emptyLine();
            b.lore(NamedTextColor.GREEN, "✔ Tiene texto configurado.");
        } else {
            b.emptyLine();
            b.lore(NamedTextColor.DARK_GRAY, "Sin texto en este punto.");
        }
        return b.build();
    }

    private org.bukkit.inventory.ItemStack buildFieldButton(Material mat, String label,
                                                            NamedTextColor color, String currentValue, String hint) {
        ItemBuilder b = ItemBuilder.of(mat)
                .name(label, color, TextDecoration.BOLD)
                .emptyLine();

        if (currentValue != null && !currentValue.isBlank()) {
            b.lore(GuiUtil.noItalic(Component.text("Actual: ", NamedTextColor.GRAY)
                    .append(LegacyComponentSerializer.legacyAmpersand().deserialize(currentValue))));
        } else {
            b.lore(NamedTextColor.DARK_GRAY, "Sin texto.");
        }

        b.emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para escribir en el chat.")
                .lore(hint);

        return b.build();
    }

    private org.bukkit.inventory.ItemStack buildTimingItem(CinematicWaypoint wp) {
        return ItemBuilder.of(Material.CLOCK)
                .name("Tiempos del título", NamedTextColor.GOLD, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Fade in",  Component.text(wp.getTitleFadeIn()  + " ticks", NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Stay",     Component.text(wp.getTitleStay()    + " ticks", NamedTextColor.WHITE)))
                .lore(GuiUtil.label("Fade out", Component.text(wp.getTitleFadeOut() + " ticks", NamedTextColor.WHITE)))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para editar (prompt en chat).")
                .lore("Formato: fadeIn stay fadeOut (ej: 10 60 10)")
                .build();
    }

    private org.bukkit.inventory.ItemStack buildTickItem(CinematicWaypoint wp) {
        return ItemBuilder.of(Material.COMPARATOR)
                .name("Tick offset", NamedTextColor.AQUA, TextDecoration.BOLD)
                .emptyLine()
                .lore(GuiUtil.label("Tick actual",
                        Component.text(wp.getTickOffset(), NamedTextColor.YELLOW)))
                .lore(GuiUtil.label("Segundos aprox.",
                        Component.text(String.format("%.1fs", wp.getTickOffset() / 20.0),
                                NamedTextColor.GRAY)))
                .emptyLine()
                .lore(NamedTextColor.YELLOW, "Click para cambiar el tick offset.")
                .lore("Afecta cuándo aparece este punto en la ruta.")
                .build();
    }

    // ── Click listener ────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = GuiUtil.getTitle(event.getView());
        if (!title.startsWith(TITLE_PREFIX)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        EditSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        int slot = event.getRawSlot();
        CinematicWaypoint wp = session.waypoint();
        Cinematic cinematic  = session.cinematic();

        switch (slot) {
            case SLOT_TITLE -> promptChat(player, session, EditField.TITLE,
                    "Escribe el título principal (soporta &-codes):");
            case SLOT_SUBTITLE -> promptChat(player, session, EditField.SUBTITLE,
                    "Escribe el subtítulo (soporta &-codes):");
            case SLOT_ACTIONBAR -> promptChat(player, session, EditField.ACTIONBAR,
                    "Escribe el texto del actionbar (soporta &-codes):");
            case SLOT_TICK -> promptChat(player, session, EditField.TICK,
                    "Escribe el tick offset (número entero, ej: 80):");
            case SLOT_TIMING -> {
                player.closeInventory();
                awaitingChat.put(player.getUniqueId(),
                        new EditSession(cinematic, wp, EditField.TITLE, session.onReturn()));
                player.sendMessage(Component.text(
                        "✎ Escribe los tiempos del título como: fadeIn stay fadeOut",
                        NamedTextColor.YELLOW));
                player.sendMessage(Component.text(
                        "  Ejemplo: 10 60 10  (cada valor en ticks)", NamedTextColor.GRAY));
                player.sendMessage(Component.text(
                        "  'cancelar' para cancelar.", NamedTextColor.GRAY));
                // Reutilizamos TITLE como señal, pero en onChat distinguimos por formato
                awaitingChat.put(player.getUniqueId(),
                        new EditSession(cinematic, wp, null /* timing especial */, session.onReturn()));
            }
            case SLOT_CLEAR -> {
                wp.setTitleMain(null);
                wp.setTitleSub(null);
                wp.setActionbar(null);
                cinematic.save();
                player.sendMessage(Component.text("✔ Textos del waypoint limpiados.", NamedTextColor.GREEN));
                open(player, cinematic, wp, session.onReturn());
            }
            case SLOT_BACK -> {
                sessions.remove(player.getUniqueId());
                player.closeInventory();
                if (session.onReturn() != null) {
                    Bukkit.getScheduler().runTask(plugin, session.onReturn());
                } else {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getCinematicAdminGUI().openDetail(player, cinematic));
                }
            }
        }
    }

    private void promptChat(Player player, EditSession session, EditField field, String prompt) {
        player.closeInventory();
        awaitingChat.put(player.getUniqueId(),
                new EditSession(session.cinematic(), session.waypoint(), field, session.onReturn()));
        player.sendMessage(Component.text("✎ " + prompt, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("  'cancelar' para cancelar. 'borrar' para vaciar el campo.",
                NamedTextColor.GRAY));
    }

    // ── Chat prompt ───────────────────────────────────────────────────────────

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        EditSession session = awaitingChat.remove(uid);
        if (session == null) return;

        event.setCancelled(true);
        String msg = event.getMessage().trim();

        if (msg.equalsIgnoreCase("cancelar")) {
            player.sendMessage(Component.text("Edición cancelada.", NamedTextColor.GRAY));
            Bukkit.getScheduler().runTask(plugin, () ->
                    open(player, session.cinematic(), session.waypoint(), session.onReturn()));
            return;
        }

        CinematicWaypoint wp = session.waypoint();
        EditField field = session.field();

        Bukkit.getScheduler().runTask(plugin, () -> {
            // Timing especial (field == null significa que era el TIMING prompt)
            if (field == null) {
                processTiming(player, session, msg);
                return;
            }

            boolean borrar = msg.equalsIgnoreCase("borrar");

            switch (field) {
                case TITLE -> {
                    wp.setTitleMain(borrar ? null : msg);
                    player.sendMessage(Component.text(
                            borrar ? "✔ Título borrado." : "✔ Título guardado.", NamedTextColor.GREEN));
                }
                case SUBTITLE -> {
                    wp.setTitleSub(borrar ? null : msg);
                    player.sendMessage(Component.text(
                            borrar ? "✔ Subtítulo borrado." : "✔ Subtítulo guardado.", NamedTextColor.GREEN));
                }
                case ACTIONBAR -> {
                    wp.setActionbar(borrar ? null : msg);
                    player.sendMessage(Component.text(
                            borrar ? "✔ Actionbar borrado." : "✔ Actionbar guardado.", NamedTextColor.GREEN));
                }
                case TICK -> {
                    try {
                        int tick = Integer.parseInt(msg);
                        if (tick < 0) {
                            player.sendMessage(Component.text("✘ El tick no puede ser negativo.", NamedTextColor.RED));
                        } else {
                            wp.setTickOffset(tick);
                            // Reordenar waypoints en la cinematica
                            session.cinematic().addWaypoint(wp); // re-inserta reordenando
                            player.sendMessage(Component.text(
                                    "✔ Tick offset seteado a " + tick + ".", NamedTextColor.GREEN));
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("✘ Valor inválido.", NamedTextColor.RED));
                    }
                }
            }

            session.cinematic().save();
            open(player, session.cinematic(), wp, session.onReturn());
        });
    }

    private void processTiming(Player player, EditSession session, String msg) {
        String[] parts = msg.trim().split("\\s+");
        if (parts.length != 3) {
            player.sendMessage(Component.text(
                    "✘ Formato incorrecto. Usa: fadeIn stay fadeOut (ej: 10 60 10)", NamedTextColor.RED));
            open(player, session.cinematic(), session.waypoint(), session.onReturn());
            return;
        }
        try {
            int fadeIn  = Integer.parseInt(parts[0]);
            int stay    = Integer.parseInt(parts[1]);
            int fadeOut = Integer.parseInt(parts[2]);
            session.waypoint().setTitleFadeIn(Math.max(0, fadeIn));
            session.waypoint().setTitleStay(Math.max(0, stay));
            session.waypoint().setTitleFadeOut(Math.max(0, fadeOut));
            session.cinematic().save();
            player.sendMessage(Component.text(
                    "✔ Tiempos guardados: " + fadeIn + " / " + stay + " / " + fadeOut, NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("✘ Valor inválido.", NamedTextColor.RED));
        }
        open(player, session.cinematic(), session.waypoint(), session.onReturn());
    }
}